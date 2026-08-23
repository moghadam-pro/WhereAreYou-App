package com.whereareyou.core.protocol

import com.whereareyou.core.model.InternetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StatusProtocolTest {

    private fun samplePayload(
        sessionId: String = "3174",
        sequence: Int = 1,
        location: EncodedLocation? = EncodedLocation(36.29714, 59.12345, 18),
        battery: Int = 9,
        charging: Boolean = false,
        internet: InternetState = InternetState.VALIDATED,
        timestamp: Long = 29784562L,
    ) = StatusPayload(
        sessionId = sessionId,
        sequence = sequence,
        location = location,
        batteryPercent = battery,
        charging = charging,
        internetState = internet,
        timestampEpochMinute = timestamp,
    )

    @Test
    fun `documented example encodes to the documented wire string`() {
        val encoded = StatusProtocol.encode(samplePayload())
        assertEquals("و1|3174|1|3629714|5912345|18|9|0|1|29784562", encoded)
    }

    @Test
    fun `documented wire string decodes to the documented fields`() {
        val result = StatusProtocol.decode("و1|3174|1|3629714|5912345|18|9|0|1|29784562")
        val success = assertIs<StatusDecodeResult.Success>(result)
        assertEquals("3174", success.payload.sessionId)
        assertEquals(1, success.payload.sequence)
        assertEquals(36.29714, success.payload.location?.latitude)
        assertEquals(59.12345, success.payload.location?.longitude)
        assertEquals(18, success.payload.location?.accuracyMeters)
        assertEquals(9, success.payload.batteryPercent)
        assertEquals(false, success.payload.charging)
        assertEquals(InternetState.VALIDATED, success.payload.internetState)
        assertEquals(29784562L, success.payload.timestampEpochMinute)
    }

    @Test
    fun `encode then decode round trips for positive coordinates`() {
        val payload = samplePayload(location = EncodedLocation(36.29714, 59.12345, 18))
        val decoded = StatusProtocol.decode(StatusProtocol.encode(payload))
        assertEquals(StatusDecodeResult.Success(payload), decoded)
    }

    @Test
    fun `encode then decode round trips for negative coordinates`() {
        val payload = samplePayload(location = EncodedLocation(-6.26031, -35.12345, 42))
        val decoded = StatusProtocol.decode(StatusProtocol.encode(payload))
        assertEquals(StatusDecodeResult.Success(payload), decoded)
    }

    @Test
    fun `battery boundary 0 round trips`() {
        val payload = samplePayload(battery = 0)
        assertEquals(StatusDecodeResult.Success(payload), StatusProtocol.decode(StatusProtocol.encode(payload)))
    }

    @Test
    fun `battery boundary 100 round trips`() {
        val payload = samplePayload(battery = 100)
        assertEquals(StatusDecodeResult.Success(payload), StatusProtocol.decode(StatusProtocol.encode(payload)))
    }

    @Test
    fun `accuracy at the representable cap round trips`() {
        val payload = samplePayload(location = EncodedLocation(1.0, 1.0, 9999))
        assertEquals(StatusDecodeResult.Success(payload), StatusProtocol.decode(StatusProtocol.encode(payload)))
    }

    @Test
    fun `accuracy above the representable cap is clamped on encode`() {
        val payload = samplePayload(location = EncodedLocation(1.0, 1.0, 50_000))
        val encoded = StatusProtocol.encode(payload)
        val decoded = assertIs<StatusDecodeResult.Success>(StatusProtocol.decode(encoded))
        assertEquals(9999, decoded.payload.location?.accuracyMeters)
    }

    @Test
    fun `no-location response omits coordinates and accuracy`() {
        val payload = samplePayload(location = null)
        val encoded = StatusProtocol.encode(payload)
        assertEquals("و1|3174|1|-|-|-|9|0|1|29784562", encoded)
        assertEquals(StatusDecodeResult.Success(payload), StatusProtocol.decode(encoded))
    }

    @Test
    fun `internet state 0 unavailable round trips`() {
        val payload = samplePayload(internet = InternetState.UNAVAILABLE)
        assertEquals(StatusDecodeResult.Success(payload), StatusProtocol.decode(StatusProtocol.encode(payload)))
    }

    @Test
    fun `internet state 1 validated round trips`() {
        val payload = samplePayload(internet = InternetState.VALIDATED)
        assertEquals(StatusDecodeResult.Success(payload), StatusProtocol.decode(StatusProtocol.encode(payload)))
    }

    @Test
    fun `internet state 2 unknown round trips`() {
        val payload = samplePayload(internet = InternetState.UNKNOWN)
        assertEquals(StatusDecodeResult.Success(payload), StatusProtocol.decode(StatusProtocol.encode(payload)))
    }

    @Test
    fun `unsupported version is rejected`() {
        val result = StatusProtocol.decode("و2|3174|1|3629714|5912345|18|9|0|1|29784562")
        val failure = assertIs<StatusDecodeResult.Failure>(result)
        assertTrue(failure.reason.contains("version"))
    }

    @Test
    fun `malformed field count is rejected`() {
        val result = StatusProtocol.decode("و1|3174|1|3629714|5912345|18|9|0|1")
        assertIs<StatusDecodeResult.Failure>(result)
    }

    @Test
    fun `extra field count is also rejected`() {
        val result = StatusProtocol.decode("و1|3174|1|3629714|5912345|18|9|0|1|29784562|extra")
        assertIs<StatusDecodeResult.Failure>(result)
    }

    @Test
    fun `partial missing location fields are rejected rather than guessed`() {
        val result = StatusProtocol.decode("و1|3174|1|3629714|-|-|9|0|1|29784562")
        assertIs<StatusDecodeResult.Failure>(result)
    }

    @Test
    fun `non-numeric field is rejected`() {
        val result = StatusProtocol.decode("و1|3174|one|3629714|5912345|18|9|0|1|29784562")
        assertIs<StatusDecodeResult.Failure>(result)
    }

    @Test
    fun `battery out of range is rejected`() {
        val result = StatusProtocol.decode("و1|3174|1|3629714|5912345|18|150|0|1|29784562")
        assertIs<StatusDecodeResult.Failure>(result)
    }

    @Test
    fun `garbage input never throws and always fails cleanly`() {
        val result = StatusProtocol.decode("not a payload at all")
        assertIs<StatusDecodeResult.Failure>(result)
    }

    @Test
    fun `carrier-added surrounding whitespace is trimmed before decoding`() {
        val result = StatusProtocol.decode("  و1|3174|1|3629714|5912345|18|9|0|1|29784562  \n")
        assertIs<StatusDecodeResult.Success>(result)
    }

    @Test
    fun `default status payload fits a single unicode SMS segment`() {
        val encoded = StatusProtocol.encode(samplePayload())
        assertTrue(SmsSegmentEstimator.fitsSingleSegment(encoded), "expected 1 segment, got ${SmsSegmentEstimator.estimate(encoded)}")
    }

    @Test
    fun `worst-case-length payload still fits a single unicode SMS segment`() {
        val worstCase = samplePayload(
            sessionId = "999999",
            sequence = 9,
            location = EncodedLocation(-89.99999, -179.99999, 9999),
            battery = 100,
            charging = true,
            internet = InternetState.VALIDATED,
            timestamp = 9_999_999_999L,
        )
        val encoded = StatusProtocol.encode(worstCase)
        assertTrue(SmsSegmentEstimator.fitsSingleSegment(encoded), "expected 1 segment for '$encoded', got ${SmsSegmentEstimator.estimate(encoded)}")
    }
}
