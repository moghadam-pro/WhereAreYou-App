package com.whereareyou.core.model.phone

/** Result of attempting to normalize a raw, user-entered phone number. */
sealed interface NormalizedPhoneNumber {
    /**
     * @property canonical E.164-style comparison value, e.g. "+989121234567". Used for
     *   all trusted-contact lookups/equality — never compare raw strings
     *   (ANDROID_ARCHITECTURE.md section 13).
     * @property display the original, human-entered value, preserved for the UI.
     */
    data class Valid(val canonical: String, val display: String) : NormalizedPhoneNumber

    data class Invalid(val reason: String, val display: String) : NormalizedPhoneNumber
}

/**
 * Hand-rolled phone-number canonicalizer.
 *
 * ANDROID_ARCHITECTURE.md section 13 recommends "a tested phone-number library or Android
 * normalization utilities" (e.g. libphonenumber, or `PhoneNumberUtils` on-device). This
 * :core module intentionally has zero third-party dependencies so it stays a plain-JVM,
 * network-independent build target (see AGENTS.md "Architecture rules" and the root
 * README note on sandbox limitations) — a real libphonenumber dependency can be swapped
 * in behind this same [NormalizedPhoneNumber] contract without touching call sites, and
 * should be evaluated before a public release per DISCOVERY_NOTES.md open question #6.
 *
 * Supported today: `+`-prefixed E.164-style numbers, and Iranian national numbers
 * (leading `0` national trunk prefix, e.g. `0912...` <-> `+98912...`), which was the
 * origin scenario's region (DISCOVERY_NOTES.md section 1). Ambiguous local numbers
 * without a default region are rejected rather than guessed.
 */
object PhoneNumberNormalizer {

    /** Default country calling code applied to a bare national number, e.g. "98" for Iran. */
    private const val DEFAULT_COUNTRY_CODE = "98"

    /** National trunk prefix stripped before applying [DEFAULT_COUNTRY_CODE]. */
    private const val NATIONAL_TRUNK_PREFIX = "0"

    private val ALLOWED_SEPARATORS = charArrayOf(' ', '-', '(', ')', ' ', '‌')

    fun normalize(raw: String, defaultCountryCode: String = DEFAULT_COUNTRY_CODE): NormalizedPhoneNumber {
        val display = raw.trim()
        if (display.isEmpty()) {
            return NormalizedPhoneNumber.Invalid("empty", display)
        }

        val digitsWithPlus = stripFormatting(display)
        if (digitsWithPlus.isEmpty() || digitsWithPlus == "+") {
            return NormalizedPhoneNumber.Invalid("no digits", display)
        }

        val canonicalDigits: String = when {
            digitsWithPlus.startsWith("+") -> {
                val digits = digitsWithPlus.drop(1)
                if (!isPlausibleE164Digits(digits)) {
                    return NormalizedPhoneNumber.Invalid("implausible E.164 length", display)
                }
                digits
            }
            digitsWithPlus.startsWith(NATIONAL_TRUNK_PREFIX) -> {
                val national = digitsWithPlus.drop(NATIONAL_TRUNK_PREFIX.length)
                if (national.isEmpty() || !national.all(Char::isDigit)) {
                    return NormalizedPhoneNumber.Invalid("malformed national number", display)
                }
                val full = defaultCountryCode + national
                if (!isPlausibleE164Digits(full)) {
                    return NormalizedPhoneNumber.Invalid("implausible national length", display)
                }
                full
            }
            digitsWithPlus.all(Char::isDigit) && digitsWithPlus.length >= 8 -> {
                // Already looks like a full country-code + subscriber number without '+'.
                digitsWithPlus
            }
            else -> return NormalizedPhoneNumber.Invalid("ambiguous local number", display)
        }

        return NormalizedPhoneNumber.Valid(canonical = "+$canonicalDigits", display = display)
    }

    private fun isPlausibleE164Digits(digits: String): Boolean =
        digits.length in 8..15 && digits.all(Char::isDigit)

    private fun stripFormatting(raw: String): String {
        val normalizedDigits = raw.map { toAsciiDigitOrSelf(it) }.joinToString("")
        val builder = StringBuilder()
        for ((index, ch) in normalizedDigits.withIndex()) {
            when {
                ch == '+' && index == 0 -> builder.append(ch)
                ch.isDigit() -> builder.append(ch)
                ch in ALLOWED_SEPARATORS -> Unit
                else -> Unit // drop any other stray character rather than fail the whole parse
            }
        }
        return builder.toString()
    }

    /** Converts Persian/Arabic-Indic digits to ASCII so mixed-script input still normalizes. */
    private fun toAsciiDigitOrSelf(ch: Char): Char = when (ch) {
        in '۰'..'۹' -> '0' + (ch - '۰') // Persian digits
        in '٠'..'٩' -> '0' + (ch - '٠') // Arabic-Indic digits
        else -> ch
    }
}
