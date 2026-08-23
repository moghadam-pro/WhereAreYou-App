package com.whereareyou.core.protocol

import com.whereareyou.core.model.text.DigitNormalization

/** A recognized human-friendly command phrase plus its trailing shared code. */
data class ParsedPhraseCommand(val phrase: String, val code: String)

/**
 * Parses the user-facing phrase form of a status request, e.g. `"وضعیت 7314"`
 * (SMS_PROTOCOL.md section 9). The phrase itself is not security — see
 * [com.whereareyou.core.security.SmsCommandAuthenticator] for the actual authorization
 * checks (trusted sender, capability, shared code, replay, rate limit).
 */
object SmsCommandPhraseParser {

    /** Default localized phrases recognized out of the box (PRODUCT_SPEC.md section 6, T3). */
    val DEFAULT_PHRASES: Set<String> = setOf("باباکجایی", "وضعیت", "تماس")

    private val WHITESPACE = Regex("\\s+")

    /**
     * Returns the parsed phrase+code, or null if [rawBody] does not match `"<phrase> <code>"`
     * where phrase is one of [recognizedPhrases] and code is all-digit after normalizing
     * Persian/Arabic-Indic digits to ASCII. Leading/trailing/extra internal whitespace is
     * tolerated (carrier-added padding).
     */
    fun parse(rawBody: String, recognizedPhrases: Set<String> = DEFAULT_PHRASES): ParsedPhraseCommand? {
        val normalized = DigitNormalization.toAsciiDigits(rawBody.trim())
        val parts = normalized.split(WHITESPACE).filter { it.isNotBlank() }
        if (parts.size != 2) return null

        val (phrase, code) = parts
        if (phrase !in recognizedPhrases) return null
        if (code.isEmpty() || !code.all(Char::isDigit)) return null

        return ParsedPhraseCommand(phrase, code)
    }
}
