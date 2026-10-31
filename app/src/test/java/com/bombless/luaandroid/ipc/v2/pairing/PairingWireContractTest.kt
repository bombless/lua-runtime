package com.bombless.luaandroid.ipc.v2.pairing

import android.os.Parcel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PairingWireContractTest {
    @Test fun requestParcelRoundTripPreservesSeparateSchemaAndExactScopes() {
        val original = PairingRequest().apply {
            schemaVersion = 1
            requestedScopes = arrayOf("runtime.read", "jobs.submit", "artifacts.read")
        }
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = PairingRequest.CREATOR.createFromParcel(parcel)
            assertEquals(1, restored.schemaVersion)
            assertArrayEquals(original.requestedScopes, restored.requestedScopes)
        } finally { parcel.recycle() }
    }

    @Test fun resultParcelRoundTripContainsOnlyRequestState() {
        val original = PairingRequestResult().apply {
            schemaVersion = 1
            requestId = "opaque-request-handle"
            status = "PENDING"
            expiresAtElapsedRealtime = 123456L
        }
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = PairingRequestResult.CREATOR.createFromParcel(parcel)
            assertEquals(original.schemaVersion, restored.schemaVersion)
            assertEquals(original.requestId, restored.requestId)
            assertEquals(original.status, restored.status)
            assertEquals(original.expiresAtElapsedRealtime, restored.expiresAtElapsedRealtime)
        } finally { parcel.recycle() }
    }
}