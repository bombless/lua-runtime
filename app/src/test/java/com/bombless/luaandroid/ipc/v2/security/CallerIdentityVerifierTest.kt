package com.bombless.luaandroid.ipc.v2.security

import org.junit.Assert.*
import org.junit.Test

class CallerIdentityVerifierTest {
    private class FakeSource(
        var packages: List<String> = listOf("example.client"),
        var packageUid: Int = 12001,
        var signers: List<String> = listOf("b".repeat(64), "a".repeat(64)),
    ) : PackageIdentitySource {
        override fun packagesForUid(uid: Int) = packages
        override fun uidForPackage(packageName: String) = packageUid
        override fun currentSignerSha256(packageName: String) = signers
    }

    @Test fun verifiesOwnershipAndCanonicalizesCurrentSignerSet() {
        val identity = CallerIdentityVerifier(FakeSource()).verify(12001)
        assertEquals("example.client", identity.packageName)
        assertEquals(listOf("a".repeat(64), "b".repeat(64)), identity.signerSha256)
        assertEquals(12001, identity.uid)
    }

    @Test fun rejectsSharedOrAmbiguousUidEvenWhenOnePackageLooksTrusted() {
        val source = FakeSource(packages = listOf("example.client", "other.shared"))
        assertThrows(CallerIdentityException::class.java) { CallerIdentityVerifier(source).verify(12001) }
    }

    @Test fun rejectsPackageUidMismatchAndMissingOrMalformedSigner() {
        assertThrows(CallerIdentityException::class.java) {
            CallerIdentityVerifier(FakeSource(packageUid = 12002)).verify(12001)
        }
        assertThrows(CallerIdentityException::class.java) {
            CallerIdentityVerifier(FakeSource(signers = emptyList())).verify(12001)
        }
        assertThrows(CallerIdentityException::class.java) {
            CallerIdentityVerifier(FakeSource(signers = listOf("not-a-digest"))).verify(12001)
        }
    }

    @Test fun changedCurrentSignerDoesNotInheritOldIdentity() {
        val before = CallerIdentityVerifier(FakeSource(signers = listOf("a".repeat(64)))).verify(12001)
        val after = CallerIdentityVerifier(FakeSource(signers = listOf("c".repeat(64)))).verify(12001)
        assertNotEquals(before, after)
    }
}