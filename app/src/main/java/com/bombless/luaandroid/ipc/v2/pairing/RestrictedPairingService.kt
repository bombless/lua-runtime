package com.bombless.luaandroid.ipc.v2.pairing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.bombless.luaandroid.ipc.v2.security.KNOWN_SCOPES

/** Exposes pairing submission/status only; no Runtime metadata, Session, or business API. */
class RestrictedPairingService : Service() {
    override fun onCreate() {
        super.onCreate()
        PairingRuntimeState.initialize(this)
    }

    private val binder = object : IRestrictedPairingService.Stub() {
        override fun submitPairingRequest(request: PairingRequest): PairingRequestResult {
            val uid = Binder.getCallingUid()
            val identity = try { PairingRuntimeState.identityVerifier.verify(uid) }
            catch (_: Exception) { throw SecurityException("Caller identity could not be verified") }
            try { PairingRuntimeState.ensureStoreHealthy() }
            catch (_: Exception) { throw SecurityException("Pairing is unavailable because trust state cannot be read") }
            if (!PairingRuntimeState.allowRequest(uid)) throw SecurityException("Pairing request rate limit exceeded")
            if (request.schemaVersion != 1 || request.requestedScopes.isEmpty() ||
                request.requestedScopes.size > KNOWN_SCOPES.size ||
                request.requestedScopes.any { it !in KNOWN_SCOPES } ||
                request.requestedScopes.distinct().size != request.requestedScopes.size) {
                throw IllegalArgumentException("Invalid pairing request")
            }
            val challenge = try {
                PairingRuntimeState.challengeManager.create(identity, request.requestedScopes.toSet())
            } catch (_: Exception) { throw SecurityException("Pairing request cannot be accepted") }
            PairingRuntimeState.remember(challenge.challengeId, identity, challenge.expiresAtElapsedRealtime)
            if (!showConsent(challenge.challengeId, identity.packageName)) {
                PairingRuntimeState.deny(challenge.challengeId)
                throw SecurityException("Runtime consent UI is unavailable; no access was granted")
            }
            return PairingRequestResult().apply {
                requestId = challenge.challengeId
                status = "PENDING"
                expiresAtElapsedRealtime = challenge.expiresAtElapsedRealtime
            }
        }

        override fun getPairingRequestStatus(requestId: String): PairingRequestResult {
            if (requestId.isBlank() || requestId.length > 128) throw IllegalArgumentException("Invalid request ID")
            val identity = try { PairingRuntimeState.identityVerifier.verify(Binder.getCallingUid()) }
            catch (_: Exception) { throw SecurityException("Caller identity could not be verified") }
            val state = PairingRuntimeState.status(requestId, identity)
                ?: throw SecurityException("Pairing request is unavailable")
            return PairingRequestResult().apply {
                this.requestId = requestId
                status = state.status
                expiresAtElapsedRealtime = state.expiresAt
            }
        }

        override fun cancelPairingRequest(requestId: String): Boolean {
            if (requestId.isBlank() || requestId.length > 128) throw IllegalArgumentException("Invalid request ID")
            val identity = try { PairingRuntimeState.identityVerifier.verify(Binder.getCallingUid()) }
            catch (_: Exception) { throw SecurityException("Caller identity could not be verified") }
            return PairingRuntimeState.cancel(requestId, identity)
        }
    }

    private fun showConsent(requestId: String, packageName: String): Boolean {
        // A successful startActivity() return does not prove that Android displayed the Activity:
        // background-activity-launch policy can silently leave it behind the current foreground app.
        // Always use a user-tapped notification as the consent entry point; the PendingIntent launch
        // is an explicit user gesture and makes the approval surface discoverable.
        val channelId = "pairing-consent"
        return try {
            val manager = getSystemService(NotificationManager::class.java)
            // Android 13+ silently suppresses notifications without POST_NOTIFICATIONS; do not
            // leave a request pending when there is no user-visible consent route.
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) return false
            if (!manager.areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(NotificationChannel(
                    channelId, "App pairing requests", NotificationManager.IMPORTANCE_HIGH,
                ))
            }
            val notificationIntent = Intent(this, PairingConsentActivity::class.java)
                .putExtra(PairingConsentActivity.EXTRA_REQUEST_ID, requestId)
                // PendingIntent identity ignores extras; a unique data URI prevents hash-code
                // collisions from redirecting one request's notification to another request.
                .setData(android.net.Uri.parse("runtime-pairing://consent/$requestId"))
            val pending = PendingIntent.getActivity(this, requestId.hashCode(), notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId)
                else @Suppress("DEPRECATION") Notification.Builder(this)
            builder.setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("App pairing request")
                .setContentText("$packageName requests Runtime access")
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setAutoCancel(true)
                .setContentIntent(pending)
            manager.notify(requestId.hashCode(), builder.build())
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}