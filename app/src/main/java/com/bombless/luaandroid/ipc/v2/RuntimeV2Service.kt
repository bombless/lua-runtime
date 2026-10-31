package com.bombless.luaandroid.ipc.v2

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.bombless.luaandroid.ipc.v2.security.AndroidPackageIdentitySource
import com.bombless.luaandroid.ipc.v2.security.CallerIdentityException
import com.bombless.luaandroid.ipc.v2.security.CallerIdentityVerifier
import com.bombless.luaandroid.ipc.v2.security.VerifiedCallerIdentity
import java.util.UUID

/** Frozen v2 bootstrap. Capabilities remain empty until their implementations ship. */
class RuntimeV2Service : Service() {
    private val packageIdentitySource by lazy { AndroidPackageIdentitySource(this) }
    private val callerIdentityVerifier by lazy { CallerIdentityVerifier(packageIdentitySource) }
    private val runtimeSignerSha256 by lazy { packageIdentitySource.currentSignerSha256(packageName).sorted() }

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
            val callerIdentity = verifyTrustedCaller(callerUid)

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
                    if (verifyTrustedCaller(Binder.getCallingUid()) != callerIdentity) {
                        throw SecurityException("Session caller identity is no longer trusted")
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

    /** The legacy signature-protected path still requires one unambiguous package and exact signer set. */
    private fun verifyTrustedCaller(uid: Int): VerifiedCallerIdentity {
        return try {
            callerIdentityVerifier.verify(uid).also { identity ->
                if (identity.signerSha256 != runtimeSignerSha256) {
                    throw CallerIdentityException("Caller signer does not match Runtime signer")
                }
            }
        } catch (_: Exception) {
            throw ProtocolException(
                "auth",
                ProtocolException.CALLER_IDENTITY_INVALID,
                "Binder caller identity could not be verified"
            )
        }
    }

    private fun utf8Length(value: String): Int = value.toByteArray(Charsets.UTF_8).size

    private fun validateHello(hello: ClientHello?) {
        if (hello == null) {
            throw ProtocolException("protocol", ProtocolException.INVALID_MESSAGE, "ClientHello is required")
        }
        if (utf8Length(hello.clientVersion) > 128 || utf8Length(hello.buildId) > 128 || utf8Length(hello.nonce) !in 16..128) {
            throw ProtocolException("protocol", ProtocolException.INVALID_MESSAGE, "ClientHello contains an invalid field length")
        }
        if (hello.minProtocolMajor < 1 || hello.maxProtocolMajor < hello.minProtocolMajor ||
            hello.minProtocolMinor < 0 || hello.maxProtocolMinor < hello.minProtocolMinor) {
            throw ProtocolException("protocol", ProtocolException.INVALID_MESSAGE, "Client protocol range is invalid")
        }
        if (hello.requestedCapabilities.size > 64 ||
            hello.requestedCapabilities.any { it.isBlank() || utf8Length(it) > 128 }) {
            throw ProtocolException("protocol", ProtocolException.INVALID_MESSAGE, "Requested capability list exceeds protocol limits")
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i("RuntimeV2Service", "onBind action=${intent?.action}")
        return binder
    }
}
