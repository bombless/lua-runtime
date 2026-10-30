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
            runtimeId = this@RuntimeV2Service.runtimeId
            runtimeVersion = "1.0"
            buildId = "1"
            engineVersion = "LuaJ"
        }
        override fun openSession(hello: ClientHello): ISession {
            if (hello.minProtocolMajor > 2 || hello.maxProtocolMajor < 2 || hello.minProtocolMinor > 0)
                throw ProtocolException("protocol", ProtocolException.PROTOCOL_MAJOR_MISMATCH, "No mutually supported protocol v2 version")
            val callerUid = Binder.getCallingUid()
            if (callerUid != Process.myUid() && packageManager.checkSignatures(callerUid, Process.myUid()) != PackageManager.SIGNATURE_MATCH)
                throw ProtocolException("auth", ProtocolException.PAIRING_REQUIRED, "This build requires a same-signature client; user-mediated pairing is not implemented yet")
            val sessionId = UUID.randomUUID().toString()
            val sessionExpiresAtMillis = System.currentTimeMillis() + 30 * 60 * 1000L
            return object : ISession.Stub() {
                private fun verifyCaller() {
                    if (System.currentTimeMillis() >= sessionExpiresAtMillis) throw SecurityException("Session expired")
                    if (Binder.getCallingUid() != callerUid) throw SecurityException("Session caller UID mismatch")
                    if (callerUid != Process.myUid() && packageManager.checkSignatures(callerUid, Process.myUid()) != PackageManager.SIGNATURE_MATCH)
                        throw SecurityException("Session signing identity is no longer trusted")
                }
                override fun getSessionInfo() = SessionInfo().apply {
                    verifyCaller(); this.sessionId = sessionId
                    expiresAtMillis = sessionExpiresAtMillis
                    grantedScopes = emptyArray()
                }
                override fun getCapabilities(): MutableList<CapabilityInfo> {
                    verifyCaller()
                    return mutableListOf()
                }
            }
        }
    }
    override fun onBind(intent: Intent?): IBinder = binder
}