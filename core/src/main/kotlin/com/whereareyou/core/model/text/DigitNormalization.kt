package com.whereareyou.core.model.text

/**
 * Converts Persian and Arabic-Indic digits to ASCII. Shared by phone-number normalization
 * and SMS command parsing so a Persian-script keyboard works consistently everywhere
 * (SMS_PROTOCOL.md section 9: "Normalize Persian/Arabic/Latin digits before parsing").
 */
object DigitNormalization {
    fun toAsciiDigits(input: String): String = input.map(::toAsciiDigitOrSelf).joinToString("")

    private fun toAsciiDigitOrSelf(ch: Char): Char = when (ch) {
        in '۰'..'۹' -> '0' + (ch - '۰') // Persian digits
        in '٠'..'٩' -> '0' + (ch - '٠') // Arabic-Indic digits
        else -> ch
    }
}
