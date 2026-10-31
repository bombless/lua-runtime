package com.bombless.luaandroid.ipc.v2.pairing

import android.content.Context
import com.bombless.luaandroid.ipc.v2.security.AndroidMonotonicClock
import com.bombless.luaandroid.ipc.v2.security.AndroidPackageIdentitySource
import com.bombless.luaandroid.ipc.v2.security.AtomicPairingStore
import com.bombless.luaandroid.ipc.v2.security.CallerIdentityVerifier
import com.bombless.luaandroid.ipc.v2.security.PairingChallengeManager
import com.bombless.luaandroid.ipc.v2.security.PairingRecord
import com.bombless.luaandroid.ipc.v2.security.VerifiedCallerIdentity
import java.io.File

/** Process-local pending consent state; process death invalidates every pending challenge. */
object PairingRuntimeState {
    data class RequestState(val identity: VerifiedCallerIdentity, val expiresAt: Long, var status: String)
    lateinit var identityVerifier: CallerIdentityVerifier
        private set
    lateinit var challengeManager: PairingChallengeManager
        private set
    lateinit var store: AtomicPairingStore
        private set
    private val requests = LinkedHashMap<String, RequestState>()
    private val recentByUid = LinkedHashMap<Int, MutableList<Long>>()

    /** Installs isolated dependencies for local Binder-boundary tests; never called by production code. */
    @Synchronized internal fun configureForTests(
        verifier: CallerIdentityVerifier,
        challenges: PairingChallengeManager,
        pairingStore: AtomicPairingStore,
    ) {
        identityVerifier = verifier
        challengeManager = challenges
        store = pairingStore
        requests.clear()
        recentByUid.clear()
    }

    @Synchronized fun initialize(context: Context) {
        if (::identityVerifier.isInitialized) return
        identityVerifier = CallerIdentityVerifier(AndroidPackageIdentitySource(context.applicationContext))
        challengeManager = PairingChallengeManager(clock = AndroidMonotonicClock)
        store = AtomicPairingStore(File(context.filesDir, "pairings-v1.bin"))
        // Validate persisted state at startup. Corruption blocks pairing rather than resetting trust.
        store.load()
    }

    @Synchronized fun ensureStoreHealthy() { store.load() }

    @Synchronized fun allowRequest(uid: Int, now: Long = AndroidMonotonicClock.nowMillis()): Boolean {
        val times = recentByUid.getOrPut(uid) { mutableListOf() }
        times.removeAll { now - it >= 10 * 60 * 1000L }
        if (times.size >= 5) return false
        times += now
        if (recentByUid.size > 512) {
            val oldest = recentByUid.keys.firstOrNull { it != uid }
            if (oldest != null) recentByUid.remove(oldest)
        }
        return true
    }

    @Synchronized fun remember(id: String, identity: VerifiedCallerIdentity, expiry: Long) {
        while (requests.size >= 256) {
            val stale = requests.entries.firstOrNull { it.value.status != "PENDING" } ?: break
            requests.remove(stale.key)
        }
        if (requests.size >= 256) throw IllegalStateException("Pairing request state is full")
        requests[id] = RequestState(identity, expiry, "PENDING")
    }

    @Synchronized fun status(id: String, identity: VerifiedCallerIdentity): RequestState? {
        val state = requests[id] ?: return null
        if (state.identity != identity) return null
        if (state.status == "PENDING" && AndroidMonotonicClock.nowMillis() >= state.expiresAt) {
            challengeManager.deny(id)
            state.status = "EXPIRED"
        }
        return state.copy()
    }

    @Synchronized fun approve(id: String, selectedScopes: Set<String>): Boolean {
        val state = requests[id] ?: return false
        if (state.status != "PENDING") return false
        val approved = challengeManager.approve(id, selectedScopes) ?: run {
            state.status = "EXPIRED"
            return false
        }
        // Re-resolve identity at the consent boundary; package/signer changes fail closed.
        val current = try { identityVerifier.verify(approved.identity.uid) } catch (_: Exception) { null }
        if (current != approved.identity || current.packageName != state.identity.packageName) {
            state.status = "IDENTITY_CHANGED"
            return false
        }
        return try {
            val existing = store.load()
            val nextVersion = (existing.filter { it.packageName == current.packageName }
                .maxOfOrNull { it.pairingVersion } ?: 0L) + 1L
            val records = existing.filterNot {
                it.packageName == current.packageName && it.signerSha256 == current.signerSha256
            }
            store.replace(records + PairingRecord(
                packageName = current.packageName,
                signerSha256 = current.signerSha256,
                grantedScopes = approved.grantedScopes,
                pairingVersion = nextVersion,
                createdAtEpochMillis = System.currentTimeMillis(),
            ))
            state.status = "APPROVED"
            true
        } catch (_: Exception) {
            state.status = "PERSISTENCE_FAILED"
            false
        }
    }

    @Synchronized fun cancel(id: String, identity: VerifiedCallerIdentity): Boolean {
        val state = requests[id] ?: return false
        if (state.identity != identity || state.status != "PENDING") return false
        if (AndroidMonotonicClock.nowMillis() >= state.expiresAt) {
            challengeManager.deny(id)
            state.status = "EXPIRED"
            return false
        }
        challengeManager.deny(id)
        state.status = "CANCELLED"
        return true
    }

    @Synchronized fun deny(id: String): Boolean {
        val state = requests[id] ?: return false
        if (state.status != "PENDING") return false
        challengeManager.deny(id)
        state.status = "DENIED"
        return true
    }
}