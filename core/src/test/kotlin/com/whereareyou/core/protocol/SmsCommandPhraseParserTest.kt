package com.whereareyou.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmsCommandPhraseParserTest {

    @Test
    fun `recognized phrase with latin digits parses`() {
        val result = SmsCommandPhraseParser.parse("وضعیت 7314")
        assertEquals(ParsedPhraseCommand("وضعیت", "7314"), result)
    }

    @Test
    fun `recognized phrase with persian digits normalizes to ascii`() {
        val result = SmsCommandPhraseParser.parse("باباکجایی ۷۳۱۴")
        assertEquals(ParsedPhraseCommand("باباکجایی", "7314"), result)
    }

    @Test
    fun `recognized phrase with arabic-indic digits normalizes to ascii`() {
        val result = SmsCommandPhraseParser.parse("تماس ٧٣١٤")
        assertEquals(ParsedPhraseCommand("تماس", "7314"), result)
    }

    @Test
    fun `surrounding and extra internal whitespace is tolerated`() {
        val result = SmsCommandPhraseParser.parse("  وضعیت    7314  ")
        assertEquals(ParsedPhraseCommand("وضعیت", "7314"), result)
    }

    @Test
    fun `unrecognized phrase is rejected`() {
        assertNull(SmsCommandPhraseParser.parse("سلام 7314"))
    }

    @Test
    fun `non-numeric code is rejected`() {
        assertNull(SmsCommandPhraseParser.parse("وضعیت abcd"))
    }

    @Test
    fun `missing code is rejected`() {
        assertNull(SmsCommandPhraseParser.parse("وضعیت"))
    }

    @Test
    fun `too many tokens is rejected`() {
        assertNull(SmsCommandPhraseParser.parse("وضعیت 7314 extra"))
    }

    @Test
    fun `empty body is rejected`() {
        assertNull(SmsCommandPhraseParser.parse(""))
    }
}
