package com.bombless.luaandroid.ipc.v2.pairing

import android.content.Intent
import android.os.Binder
import android.os.Process
import com.bombless.luaandroid.ipc.v2.security.AtomicPairingStore
import com.bombless.luaandroid.ipc.v2.security.CallerIdentityVerifier
import com.bombless.luaandroid.ipc.v2.security.MonotonicClock
import com.bombless.luaandroid.ipc.v2.security.PackageIdentitySource
import com.bombless.luaandroid.ipc.v2.security.PairingChallengeManager
import com.bombless.luaandroid.ipc.v2.security.VerifiedCallerIdentity
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Exercises the exported AIDL Stub boundary with Binder's process UID, not just helper methods. */
@RunWith(RobolectricTestRunner::class)
class RestrictedPairingServiceBinderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private class FakeIdentitySource(
        var packages: List<String> = listOf("verified.client"),
        var packageUid: Int = Process.myUid(),
        var signers: List<String> = listOf("a".repeat(64)),
    ) : PackageIdentitySource {
        override fun packagesForUid(uid: Int): List<String> = packages
        override fun uidForPackage(packageName: String): Int = packageUid
        override fun currentSignerSha256(packageName: String): List<String> = signers
    }

    private data class Harness(
        val api: IRestrictedPairingService,
        val source: FakeIdentitySource,
        val store: AtomicPairingStore,
        val challenges: PairingChallengeManager,
        val identity: VerifiedCallerIdentity,
    )

    private fun harness(
        source: FakeIdentitySource = FakeIdentitySource(),
        storeFile: File = File(temporaryFolder.root, "pairings.bin"),
    ): Harness {
        val challenges = PairingChallengeManager(clock = MonotonicClock { android.os.SystemClock.elapsedRealtime() })
        val store = AtomicPairingStore(storeFile)
        val identity = CallerIdentityVerifier(source).verify(Binder.getCallingUid())
        PairingRuntimeState.configureForTests(CallerIdentityVerifier(source), challenges, store)
        val service = Robolectric.buildService(RestrictedPairingService::class.java).create().get()
        val api = IRestrictedPairingService.Stub.asInterface(service.onBind(Intent("test")))
        return Harness(api, source, store, challenges, identity)
    }

    private fun request(vararg scopes: String) = PairingRequest().apply {
        schemaVersion = 1
        requestedScopes = arrayOf(*scopes)
    }

    @Test fun binderRejectsUidWithNoVerifiedPackage() {
        val source = FakeIdentitySource(packages = emptyList())
        PairingRuntimeState.configureForTests(
            CallerIdentityVerifier(source), PairingChallengeManager(clock = MonotonicClock { 10_000L }),
            AtomicPairingStore(File(temporaryFolder.root, "denied.bin")),
        )
        val service = Robolectric.buildService(RestrictedPairingService::class.java).create().get()
        val api = IRestrictedPairingService.Stub.asInterface(service.onBind(Intent("test")))
        assertThrows(SecurityException::class.java) { api.submitPairingRequest(request("runtime.read")) }
    }

    @Test fun binderRejectsUidPackageMismatchAndSharedUid() {
        val mismatched = FakeIdentitySource(packageUid = Binder.getCallingUid() + 1)
        PairingRuntimeState.configureForTests(
            CallerIdentityVerifier(mismatched), PairingChallengeManager(clock = MonotonicClock { 10_000L }),
            AtomicPairingStore(File(temporaryFolder.root, "mismatch.bin")),
        )
        var service = Robolectric.buildService(RestrictedPairingService::class.java).create().get()
        var api = IRestrictedPairingService.Stub.asInterface(service.onBind(Intent("test")))
        assertThrows(SecurityException::class.java) { api.submitPairingRequest(request("runtime.read")) }

        val shared = FakeIdentitySource(packages = listOf("verified.client", "attacker.shared"))
        PairingRuntimeState.configureForTests(
            CallerIdentityVerifier(shared), PairingChallengeManager(clock = MonotonicClock { 10_000L }),
            AtomicPairingStore(File(temporaryFolder.root, "shared.bin")),
        )
        service = Robolectric.buildService(RestrictedPairingService::class.java).create().get()
        api = IRestrictedPairingService.Stub.asInterface(service.onBind(Intent("test")))
        assertThrows(SecurityException::class.java) { api.submitPairingRequest(request("runtime.read")) }
    }

    @Test fun binderRejectsMalformedAndUnknownScopeRequests() {
        val h = harness()
        assertThrows(IllegalArgumentException::class.java) { h.api.submitPairingRequest(request()) }
        assertThrows(IllegalArgumentException::class.java) { h.api.submitPairingRequest(request("unknown.scope")) }
        assertThrows(IllegalArgumentException::class.java) { h.api.submitPairingRequest(request("runtime.read", "runtime.read")) }
        assertThrows(IllegalArgumentException::class.java) {
            h.api.submitPairingRequest(PairingRequest().apply { schemaVersion = 99; requestedScopes = arrayOf("runtime.read") })
        }
    }

    @Test fun clientCannotForgePackageNameAndApprovalUsesBinderVerifiedIdentity() {
        val h = harness()
        assertFalse(PairingRequest::class.java.declaredFields.any { it.name.equals("packageName", ignoreCase = true) })
        val challenge = h.challenges.create(h.identity, setOf("runtime.read"))
        PairingRuntimeState.remember(challenge.challengeId, h.identity, challenge.expiresAtElapsedRealtime)
        assertTrue(PairingRuntimeState.approve(challenge.challengeId, setOf("runtime.read")))
        assertEquals("verified.client", h.store.load().single().packageName)
    }

    @Test fun changedSignerAtConsentBoundaryFailsClosedAndChallengeCannotReplay() {
        val h = harness()
        val challenge = h.challenges.create(h.identity, setOf("runtime.read"))
        PairingRuntimeState.remember(challenge.challengeId, h.identity, challenge.expiresAtElapsedRealtime)
        h.source.signers = listOf("b".repeat(64))
        assertFalse(PairingRuntimeState.approve(challenge.challengeId, setOf("runtime.read")))
        assertFalse(PairingRuntimeState.approve(challenge.challengeId, setOf("runtime.read")))
        assertTrue(h.store.load().isEmpty())
    }

    @Test fun expiredAndDeniedChallengesCannotBeApprovedOrReplayed() {
        var now = 1_000L
        val manager = PairingChallengeManager(clock = MonotonicClock { now }, ttlMillis = 100L)
        val identity = VerifiedCallerIdentity(Binder.getCallingUid(), "verified.client", listOf("a".repeat(64)))
        val expired = manager.create(identity, setOf("runtime.read"))
        now += 100L
        assertNull(manager.approve(expired.challengeId, setOf("runtime.read")))
        val denied = manager.create(identity, setOf("runtime.read"))
        assertTrue(manager.deny(denied.challengeId))
        assertFalse(manager.deny(denied.challengeId))
        assertNull(manager.approve(denied.challengeId, setOf("runtime.read")))
    }

    @Test fun cancelIsCallerBoundAndDenialPersistsNoPairing() {
        val h = harness()
        val challenge = h.challenges.create(h.identity, setOf("runtime.read"))
        PairingRuntimeState.remember(challenge.challengeId, h.identity, challenge.expiresAtElapsedRealtime)
        assertEquals("PENDING", h.api.getPairingRequestStatus(challenge.challengeId).status)
        assertTrue(h.api.cancelPairingRequest(challenge.challengeId))
        assertEquals("CANCELLED", h.api.getPairingRequestStatus(challenge.challengeId).status)
        assertFalse(h.api.cancelPairingRequest(challenge.challengeId))

        val deniedChallenge = h.challenges.create(h.identity, setOf("runtime.read"))
        PairingRuntimeState.remember(deniedChallenge.challengeId, h.identity, deniedChallenge.expiresAtElapsedRealtime)
        assertTrue(PairingRuntimeState.deny(deniedChallenge.challengeId))
        assertFalse(PairingRuntimeState.deny(deniedChallenge.challengeId))
        assertTrue(h.store.load().isEmpty())
        assertFalse(PairingRuntimeState.cancel(challenge.challengeId,
            h.identity.copy(packageName = "attacker.package")))
    }

    @Test fun persistenceFailureNeverReportsApprovalOrWritesTrust() {
        val parentIsAFile = File(temporaryFolder.root, "not-a-directory").apply { writeText("block") }
        val h = harness(storeFile = File(parentIsAFile, "pairings.bin"))
        val challenge = h.challenges.create(h.identity, setOf("runtime.read"))
        PairingRuntimeState.remember(challenge.challengeId, h.identity, challenge.expiresAtElapsedRealtime)
        assertFalse(PairingRuntimeState.approve(challenge.challengeId, setOf("runtime.read")))
        assertEquals("PERSISTENCE_FAILED", PairingRuntimeState.status(challenge.challengeId, h.identity)?.status)
    }
}