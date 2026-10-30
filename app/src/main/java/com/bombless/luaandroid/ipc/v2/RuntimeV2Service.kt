package com.bombless.luaandroid.ipc.v2

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Process
import java.util.UUID

/** Frozen v2 bootstrap. Capabilities remain empty until their implementations ship. */
class RuntimeV2Service : Service() {
    private val runtimeId by lazy {
        val prefs = getSharedPreferences("ipc-v2", MODE_PRIVATE)
        prefs.getString("runtime_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("runtime_id", it).apply()
        }
    }

    private val binder = object : IProtocolBootstrap.Stub() {
        override fun getProtocolInfo() = ProtocolInfo().apply {
            val appInfo = packageManager.getPackageInfo(packageName, 0)
            runtimeId = this@RuntimeV2Service.runtimeId
            runtimeVersion = appInfo.versionName ?: "unknown"
            serviceVersion = appInfo.versionName ?: "unknown"
            @Suppress("DEPRECATION")
            run { buildId = appInfo.versionCode.toString() }
            engineVersion = "3.0.1"
            // LuaJ is a JVM implementation; it does not expose a LuaJIT/native C ABI.
            luaAbi = ""
        }

        override fun openSession(hello: ClientHello): ISession {
            validateHello(hello)
            if (hello.minProtocolMajor > 2 || hello.maxProtocolMajor < 2 ||
                hello.minProtocolMinor > 0 || hello.maxProtocolMinor < 0) {
                throw ProtocolException(
                    "protocol",
                    ProtocolException.PROTOCOL_MAJOR_MISMATCH,
                    "No mutually supported protocol v2 version"
                )
            }

            val callerUid = Binder.getCallingUid()
            val callerPackages = trustedPackagesForUid(callerUid)
            if (callerPackages.isEmpty()) {
                throw ProtocolException(
                    "auth",
                    ProtocolException.CALLER_IDENTITY_INVALID,
                    "Binder caller identity could not be verified"
                )
            }

            val sessionId = UUID.randomUUID().toString()
            val sessionExpiresAtMillis = System.currentTimeMillis() + 30 * 60 * 1000L
            return object : ISession.Stub() {
                private fun verifyCaller() {
                    if (System.currentTimeMillis() >= sessionExpiresAtMillis) {
                        throw SecurityException("Session expired")
                    }
                    if (Binder.getCallingUid() != callerUid) {
                        throw SecurityException("Session caller UID mismatch")
                    }
                    if (trustedPackagesForUid(callerUid).none { it in callerPackages }) {
                        throw SecurityException("Session caller package or signing identity is no longer trusted")
                    }
                }

                override fun getSessionInfo() = SessionInfo().apply {
                    verifyCaller()
                    this.sessionId = sessionId
                    expiresAtMillis = sessionExpiresAtMillis
                    grantedScopes = emptyArray()
                }

                override fun getCapabilities(): MutableList<CapabilityInfo> {
                    verifyCaller()
                    // Deliberately empty: no v2 job, transfer, or package capability is implemented yet.
                    return mutableListOf()
                }
            }
        }
    }

    /** Resolve package ownership from Binder's UID and compare signing identity with this app. */
    private fun trustedPackagesForUid(uid: Int): Set<String> {
        val packages = packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
        if (packages.isEmpty()) return emptySet()
        if (uid == Process.myUid()) return packages

        val ownPackage = applicationInfo.packageName
        return packages.filterTo(mutableSetOf()) { packageName ->
            packageManager.checkSignatures(packageName, ownPackage) == PackageManager.SIGNATURE_MATCH
        }
    }

    private fun validateHello(hello: ClientHello?) {
        if (hello == null) {
            throw ProtocolException("protocol", ProtocolException.INVALID_MESSAGE, "ClientHello is required")
        }
        if (hello.clientVersion.length > 128 || hello.buildId.length > 128 || hello.nonce.length !in 16..128) {
            throw ProtocolException("protocol", ProtocolException.INVALID_MESSAGE, "ClientHello contains an invalid field length")
        }
        if (hello.minProtocolMajor < 1 || hello.maxProtocolMajor < hello.minProtocolMajor ||
            hello.minProtocolMinor < 0 || hello.maxProtocolMinor < hello.minProtocolMinor) {
            throw ProtocolException("protocol", ProtocolException.INVALID_MESSAGE, "Client protocol range is invalid")
        }
        if (hello.requestedCapabilities.size > 64 ||
            hello.requestedCapabilities.any { it.isBlank() || it.length > 128 }) {
            throw ProtocolException("protocol", ProtocolException.INVALID_MESSAGE, "Requested capability list exceeds protocol limits")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
