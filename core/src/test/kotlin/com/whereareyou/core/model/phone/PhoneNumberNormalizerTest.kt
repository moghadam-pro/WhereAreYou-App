package com.whereareyou.core.model.phone

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PhoneNumberNormalizerTest {

    private fun canonicalOf(raw: String, defaultCountryCode: String = "98"): String {
        val result = PhoneNumberNormalizer.normalize(raw, defaultCountryCode)
        val valid = assertIs<NormalizedPhoneNumber.Valid>(result, "expected valid for '$raw' but was $result")
        return valid.canonical
    }

    @Test
    fun `plus-prefixed international number stays as-is`() {
        assertEquals("+989121234567", canonicalOf("+989121234567"))
    }

    @Test
    fun `local Iranian number with national trunk prefix normalizes to plus98`() {
        assertEquals("+989121234567", canonicalOf("0912 123 4567"))
    }

    @Test
    fun `international and local forms of the same number are equal`() {
        assertEquals(canonicalOf("+989121234567"), canonicalOf("0912-123-4567"))
    }

    @Test
    fun `hyphens spaces and parentheses are stripped`() {
        assertEquals("+14155552671", canonicalOf("+1 (415) 555-2671"))
    }

    @Test
    fun `generic E164 number without formatting`() {
        assertEquals("+442071838750", canonicalOf("+442071838750"))
    }

    @Test
    fun `persian digits are normalized before parsing`() {
        assertEquals("+989121234567", canonicalOf("۰۹۱۲۱۲۳۴۵۶۷"))
    }

    @Test
    fun `arabic-indic digits are normalized before parsing`() {
        assertEquals("+989121234567", canonicalOf("٠٩١٢١٢٣٤٥٦٧"))
    }

    @Test
    fun `default country code is configurable per normalize call`() {
        assertEquals("+14155552671", canonicalOf("04155552671", defaultCountryCode = "1"))
    }

    @Test
    fun `empty input is invalid`() {
        val result = PhoneNumberNormalizer.normalize("")
        assertIs<NormalizedPhoneNumber.Invalid>(result)
    }

    @Test
    fun `blank input is invalid`() {
        val result = PhoneNumberNormalizer.normalize("   ")
        assertIs<NormalizedPhoneNumber.Invalid>(result)
    }

    @Test
    fun `ambiguous short local number without trunk prefix is invalid`() {
        val result = PhoneNumberNormalizer.normalize("1234")
        assertIs<NormalizedPhoneNumber.Invalid>(result)
    }

    @Test
    fun `letters produce invalid rather than a silently wrong number`() {
        val result = PhoneNumberNormalizer.normalize("call-me-now")
        assertIs<NormalizedPhoneNumber.Invalid>(result)
    }

    @Test
    fun `implausibly long digit string is invalid`() {
        val result = PhoneNumberNormalizer.normalize("+12345678901234567890")
        assertIs<NormalizedPhoneNumber.Invalid>(result)
    }

    @Test
    fun `display value preserves original user input`() {
        val result = PhoneNumberNormalizer.normalize("  0912 123 4567  ")
        val valid = assertIs<NormalizedPhoneNumber.Valid>(result)
        assertEquals("0912 123 4567", valid.display)
    }
}
