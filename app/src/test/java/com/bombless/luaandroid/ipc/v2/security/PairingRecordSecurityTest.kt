package com.bombless.luaandroid.ipc.v2.security

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PairingRecordSecurityTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun record() = PairingRecord(
        packageName = "example.client",
        signerSha256 = listOf("b".repeat(64), "a".repeat(64)),
        grantedScopes = setOf("jobs.read", "runtime.read"),
        pairingVersion = 3,
        createdAtEpochMillis = 123456L,
    )

    @Test fun codecRoundTripsAndCanonicalizesIdentityAndScopes() {
        val decoded = PairingRecordCodec.decode(PairingRecordCodec.encode(listOf(record()))).single()
        assertEquals("example.client", decoded.packageName)
        assertEquals(listOf("a".repeat(64), "b".repeat(64)), decoded.signerSha256)
        assertEquals(setOf("jobs.read", "runtime.read"), decoded.grantedScopes)
        assertEquals(3L, decoded.pairingVersion)
    }

    @Test fun unknownFileAndRecordVersionsFailClosed() {
        val encoded = PairingRecordCodec.encode(listOf(record()))
        val unknownFileVersion = encoded.clone().also { java.nio.ByteBuffer.wrap(it).putInt(4, 99) }
        assertThrows(PairingStoreException::class.java) { PairingRecordCodec.decode(unknownFileVersion) }
        val unknownRecordVersion = encoded.clone().also { java.nio.ByteBuffer.wrap(it).putInt(12, 99) }
        assertThrows(PairingStoreException::class.java) { PairingRecordCodec.decode(unknownRecordVersion) }
    }

    @Test fun truncatedTrailingAndInvalidScopeDataAreRejected() {
        val encoded = PairingRecordCodec.encode(listOf(record()))
        assertThrows(PairingStoreException::class.java) { PairingRecordCodec.decode(encoded.copyOf(encoded.size - 2)) }
        assertThrows(PairingStoreException::class.java) { PairingRecordCodec.decode(encoded + byteArrayOf(1)) }
        val invalidUtf8 = encoded.clone().also { it[20] = 0xff.toByte() }
        assertThrows(PairingStoreException::class.java) { PairingRecordCodec.decode(invalidUtf8) }
        assertThrows(IllegalArgumentException::class.java) {
            PairingRecordCodec.encode(listOf(record().copy(grantedScopes = setOf("*"))))
        }
    }

    @Test fun atomicStorePersistsAndCorruptStateNeverBecomesEmptyAuthorization() {
        val file = java.io.File(temporaryFolder.root, "pairings.bin")
        val store = AtomicPairingStore(file)
        assertTrue(store.load().isEmpty())
        store.replace(listOf(record()))
        assertEquals(listOf(record().copy(signerSha256 = listOf("a".repeat(64), "b".repeat(64)))), store.load())
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(PairingStoreException::class.java) { store.load() }
    }
}