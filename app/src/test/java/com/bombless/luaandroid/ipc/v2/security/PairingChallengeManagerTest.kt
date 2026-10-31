package com.bombless.luaandroid.ipc.v2.security

import org.junit.Assert.*
import org.junit.Test

class PairingChallengeManagerTest {
    private var now = 10_000L
    private val clock = MonotonicClock { now }
    private val identity = VerifiedCallerIdentity(12001, "example.client", listOf("a".repeat(64)))

    @Test fun challengeBindsIdentityAndExactScopesAndCanBeApprovedOnlyOnce() {
        val manager = PairingChallengeManager(clock = clock)
        val requested = setOf("runtime.read", "jobs.read")
        val challenge = manager.create(identity, requested)
        assertEquals(43, challenge.challengeId.length) // 32 random bytes, URL-safe base64 without padding
        val pending = manager.pendingForConsent(challenge.challengeId)!!
        assertEquals(identity, pending.identity)
        assertEquals(requested, pending.requestedScopes)
        val approved = manager.approve(challenge.challengeId, setOf("runtime.read"))!!
        assertEquals(identity, approved.identity)
        assertEquals(setOf("runtime.read"), approved.grantedScopes)
        assertNull(manager.pendingForConsent(challenge.challengeId))
        assertNull(manager.approve(challenge.challengeId, requested))
    }

    @Test fun expiryDenialReplayAndScopeEscalationFailClosed() {
        val manager = PairingChallengeManager(clock = clock, ttlMillis = 100)
        val expired = manager.create(identity, setOf("runtime.read"))
        now += 100
        assertNull(manager.approve(expired.challengeId, setOf("runtime.read")))

        val denied = manager.create(identity, setOf("runtime.read"))
        assertTrue(manager.deny(denied.challengeId))
        assertFalse(manager.deny(denied.challengeId))

        val escalated = manager.create(identity, setOf("runtime.read"))
        assertNull(manager.approve(escalated.challengeId, setOf("runtime.read", "jobs.submit")))
        assertNull(manager.approve(escalated.challengeId, setOf("runtime.read")))
    }

    @Test fun pendingRequestsAreBoundedAndUnknownScopesAreRejected() {
        val manager = PairingChallengeManager(clock = clock, maxPending = 1)
        manager.create(identity, setOf("runtime.read"))
        assertThrows(IllegalStateException::class.java) { manager.create(identity, setOf("jobs.read")) }
        assertThrows(IllegalArgumentException::class.java) { manager.create(identity, setOf("unknown.scope")) }
    }
}