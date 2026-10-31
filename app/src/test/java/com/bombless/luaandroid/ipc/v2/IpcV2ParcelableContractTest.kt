package com.bombless.luaandroid.ipc.v2

import android.os.Parcel
import android.os.Parcelable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IpcV2ParcelableContractTest {
    private fun <T : Parcelable> roundTrip(value: T, creator: Parcelable.Creator<T>): T {
        val parcel = Parcel.obtain()
        try {
            value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return creator.createFromParcel(parcel)
        } finally { parcel.recycle() }
    }

    @Test fun protocolInfoRoundTripsAllFieldsAndNestedCapabilities() {
        val source = ProtocolInfo().apply {
            runtimeId="runtime"; runtimeVersion="1.2"; serviceVersion="2.0.0"; buildId="42"
            engineVersion="3.0.1"; maxMetadataBytes=65536
            supportedCapabilities.add(CapabilityInfo("runtime.info.v1", 1, "{}"))
        }
        val copy = roundTrip(source, ProtocolInfo.CREATOR)
        assertEquals(source.namespace, copy.namespace)
        assertEquals(source.protocolMajor, copy.protocolMajor); assertEquals(source.protocolMinor, copy.protocolMinor)
        assertEquals(source.runtimeId, copy.runtimeId); assertEquals(source.runtimeVersion, copy.runtimeVersion)
        assertEquals(source.serviceVersion, copy.serviceVersion); assertEquals(source.buildId, copy.buildId)
        assertEquals(source.engineId, copy.engineId); assertEquals(source.engineVersion, copy.engineVersion)
        assertEquals(source.luaLanguageVersion, copy.luaLanguageVersion); assertEquals(source.luaAbi, copy.luaAbi)
        assertEquals(source.maxMetadataBytes, copy.maxMetadataBytes); assertEquals(source.maxTransferBytes, copy.maxTransferBytes)
        assertEquals(source.maxInputCount, copy.maxInputCount); assertEquals(source.maxConcurrency, copy.maxConcurrency)
        assertEquals(source.maxQueueDepth, copy.maxQueueDepth); assertEquals(source.maxExecutionMillis, copy.maxExecutionMillis)
        assertEquals(source.maxOutputBytes, copy.maxOutputBytes); assertEquals(source.maxEventBatchSize, copy.maxEventBatchSize)
        assertEquals(1, copy.supportedCapabilities.size)
        assertEquals("runtime.info.v1", copy.supportedCapabilities.single().id)
    }

    @Test fun capabilityInfoRoundTrips() {
        val source = CapabilityInfo("jobs.query.v1", 3, "{\"pageSize\":50}")
        val copy = roundTrip(source, CapabilityInfo.CREATOR)
        assertEquals(source.id, copy.id); assertEquals(source.schemaVersion, copy.schemaVersion)
        assertEquals(source.limitsJson, copy.limitsJson)
    }

    @Test fun clientHelloRoundTripsVersionRangeCapabilitiesAndNonce() {
        val source = ClientHello().apply {
            clientVersion="companion"; buildId="debug"; minProtocolMajor=2; maxProtocolMajor=2
            minProtocolMinor=0; maxProtocolMinor=2
            requestedCapabilities=arrayOf("runtime.info.v1", "jobs.query.v1"); nonce="0123456789abcdef"
        }
        val copy = roundTrip(source, ClientHello.CREATOR)
        assertEquals(source.clientVersion, copy.clientVersion); assertEquals(source.buildId, copy.buildId)
        assertEquals(source.minProtocolMajor, copy.minProtocolMajor); assertEquals(source.maxProtocolMajor, copy.maxProtocolMajor)
        assertEquals(source.minProtocolMinor, copy.minProtocolMinor); assertEquals(source.maxProtocolMinor, copy.maxProtocolMinor)
        assertEquals(source.requestedCapabilities.toList(), copy.requestedCapabilities.toList())
        assertEquals(source.nonce, copy.nonce)
    }

    @Test fun sessionInfoRoundTrips() {
        val source = SessionInfo().apply {
            sessionId="session-id"; expiresAtMillis=123456789L
            grantedScopes=arrayOf("runtime.read", "jobs.read")
        }
        val copy = roundTrip(source, SessionInfo.CREATOR)
        assertEquals(source.sessionId, copy.sessionId); assertEquals(source.expiresAtMillis, copy.expiresAtMillis)
        assertEquals(source.grantedScopes.toList(), copy.grantedScopes.toList())
    }

    @Test fun structuredErrorRoundTrips() {
        val source = ErrorInfo().apply {
            domain="protocol"; code="INVALID_MESSAGE"; safeMessage="Invalid request"; retryable=true
            fieldPath="requestedCapabilities[0]"; detailsJson="{\"limit\":64}"
            retryAfterMillis=250L; correlationId="correlation-1"
        }
        val copy = roundTrip(source, ErrorInfo.CREATOR)
        assertEquals(source.domain, copy.domain); assertEquals(source.code, copy.code)
        assertEquals(source.safeMessage, copy.safeMessage); assertTrue(copy.retryable)
        assertEquals(source.fieldPath, copy.fieldPath); assertEquals(source.detailsJson, copy.detailsJson)
        assertEquals(source.retryAfterMillis, copy.retryAfterMillis); assertEquals(source.correlationId, copy.correlationId)
        assertFalse(roundTrip(ErrorInfo(), ErrorInfo.CREATOR).retryable)
    }
}