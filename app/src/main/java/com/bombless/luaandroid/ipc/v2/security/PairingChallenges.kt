package com.bombless.luaandroid.ipc.v2.security

import android.os.SystemClock
import java.security.SecureRandom
import java.util.Base64

data class PairingChallenge(val schemaVersion: Int = CURRENT_SCHEMA_VERSION, val challengeId: String, val expiresAtElapsedRealtime: Long) {
    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

data class PendingPairingChallenge(
    val schemaVersion: Int = PairingChallenge.CURRENT_SCHEMA_VERSION,
    val identity: VerifiedCallerIdentity,
    val requestedScopes: Set<String>,
    val expiresAtElapsedRealtime: Long,
)

data class ApprovedPairingRequest(val identity: VerifiedCallerIdentity, val grantedScopes: Set<String>)
fun interface MonotonicClock { fun nowMillis(): Long }

object AndroidMonotonicClock : MonotonicClock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

/** In-memory one-time 256-bit challenges. Process death invalidates pending consent requests. */
class PairingChallengeManager(
    private val random: SecureRandom = SecureRandom(),
    private val clock: MonotonicClock,
    private val ttlMillis: Long = 2 * 60 * 1000L,
    private val maxPending: Int = 32,
) {
    private val pending = LinkedHashMap<String, PendingPairingChallenge>()

    @Synchronized fun create(identity: VerifiedCallerIdentity, requestedScopes: Set<String>): PairingChallenge {
        require(ttlMillis in 1..10 * 60 * 1000L && maxPending in 1..256)
        require(requestedScopes.isNotEmpty() && requestedScopes.size <= KNOWN_SCOPES.size && requestedScopes.all { it in KNOWN_SCOPES })
        pruneExpired(); check(pending.size < maxPending) { "Too many pending pairing requests" }
        var token: String
        do {
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        } while (token in pending)
        val expires = Math.addExact(clock.nowMillis(), ttlMillis)
        pending[token] = PendingPairingChallenge(identity = identity, requestedScopes = requestedScopes.toSortedSet(), expiresAtElapsedRealtime = expires)
        return PairingChallenge(challengeId = token, expiresAtElapsedRealtime = expires)
    }

    @Synchronized fun pendingForConsent(challengeId: String): PendingPairingChallenge? {
        val item = pending[challengeId] ?: return null
        if (clock.nowMillis() >= item.expiresAtElapsedRealtime) { pending.remove(challengeId); return null }
        return item
    }

    /** Runtime UI resolves server-held state; there is deliberately no client-provided approval flag. */
    @Synchronized fun approve(challengeId: String, selectedScopes: Set<String>): ApprovedPairingRequest? {
        val item = pending.remove(challengeId) ?: return null
        if (clock.nowMillis() >= item.expiresAtElapsedRealtime) return null
        if (selectedScopes.isEmpty() || selectedScopes.any { it !in item.requestedScopes }) return null
        return ApprovedPairingRequest(item.identity, selectedScopes.toSortedSet())
    }

    @Synchronized fun deny(challengeId: String): Boolean = pending.remove(challengeId) != null
    @Synchronized fun pendingCount(): Int { pruneExpired(); return pending.size }

    private fun pruneExpired() {
        val now = clock.nowMillis(); val iterator = pending.entries.iterator()
        while (iterator.hasNext()) if (now >= iterator.next().value.expiresAtElapsedRealtime) iterator.remove()
    }
}