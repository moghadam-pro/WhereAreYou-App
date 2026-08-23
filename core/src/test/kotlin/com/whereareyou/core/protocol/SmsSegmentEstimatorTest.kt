package com.whereareyou.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmsSegmentEstimatorTest {

    @Test
    fun `ascii-only text under 160 chars is one gsm7 segment`() {
        val estimate = SmsSegmentEstimator.estimate("STATUS OK")
        assertFalse(estimate.isUnicode)
        assertEquals(1, estimate.segments)
    }

    @Test
    fun `text containing persian marker forces unicode encoding`() {
        val estimate = SmsSegmentEstimator.estimate("و1|1|1|-|-|-|9|0|1|1")
        assertTrue(estimate.isUnicode)
    }

    @Test
    fun `70 unicode chars is exactly one segment`() {
        val text = "و" + "1".repeat(69)
        val estimate = SmsSegmentEstimator.estimate(text)
        assertEquals(70, estimate.characterCount)
        assertEquals(1, estimate.segments)
    }

    @Test
    fun `71 unicode chars becomes multipart`() {
        val text = "و" + "1".repeat(70)
        val estimate = SmsSegmentEstimator.estimate(text)
        assertEquals(71, estimate.characterCount)
        assertEquals(2, estimate.segments)
    }

    @Test
    fun `fitsSingleSegment matches the segments count`() {
        val fits = "و" + "1".repeat(69)
        val overflows = "و" + "1".repeat(70)
        assertTrue(SmsSegmentEstimator.fitsSingleSegment(fits))
        assertFalse(SmsSegmentEstimator.fitsSingleSegment(overflows))
    }
}
