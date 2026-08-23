package com.whereareyou.core.protocol

/**
 * Best-effort, platform-independent SMS segment estimate.
 *
 * SMS_PROTOCOL.md section 2 requires designing/testing against a one-Unicode-segment
 * budget (~70 UCS-2 code units) because the default wire marker contains Persian text.
 * AGENTS.md additionally requires that the real Android send path assert the actual
 * segment count with the platform API (the current equivalent of
 * `SmsMessage.calculateLength`) before sending — this estimator is for pure-JVM unit
 * tests and quick local iteration, not a replacement for that on-device check.
 *
 * Encoding selection here is intentionally simple: any character outside the GSM 7-bit
 * default alphabet's ASCII-range subset forces the whole message into UCS-2, matching
 * standard carrier behavior for a single mixed-script message.
 */
object SmsSegmentEstimator {
    private const val GSM7_SINGLE_SEGMENT = 160
    private const val GSM7_MULTIPART_SEGMENT = 153
    private const val UCS2_SINGLE_SEGMENT = 70
    private const val UCS2_MULTIPART_SEGMENT = 67

    /** A conservative approximation of the GSM 7-bit default alphabet: printable ASCII. */
    private fun isGsm7Compatible(ch: Char): Boolean = ch.code in 0x20..0x7E || ch == '\n' || ch == '\r'

    data class Estimate(val isUnicode: Boolean, val characterCount: Int, val segments: Int)

    fun estimate(text: String): Estimate {
        val isUnicode = text.any { !isGsm7Compatible(it) }
        val length = text.length
        val singleSegmentLimit = if (isUnicode) UCS2_SINGLE_SEGMENT else GSM7_SINGLE_SEGMENT
        val multipartSegmentLimit = if (isUnicode) UCS2_MULTIPART_SEGMENT else GSM7_MULTIPART_SEGMENT

        val segments = when {
            length == 0 -> 0
            length <= singleSegmentLimit -> 1
            else -> (length + multipartSegmentLimit - 1) / multipartSegmentLimit // ceiling division
        }

        return Estimate(isUnicode, length, segments)
    }

    fun fitsSingleSegment(text: String): Boolean = estimate(text).segments <= 1
}
