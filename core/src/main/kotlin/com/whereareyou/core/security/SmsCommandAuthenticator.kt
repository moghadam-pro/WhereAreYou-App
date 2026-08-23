package com.whereareyou.core.security

import com.whereareyou.core.model.TriggerEvent
import com.whereareyou.core.model.TrustedContact
import com.whereareyou.core.protocol.CommandCode
import com.whereareyou.core.protocol.CommandDecodeResult
import com.whereareyou.core.protocol.CommandProtocol
import com.whereareyou.core.protocol.SmsCommandPhraseParser
import com.whereareyou.core.rules.SmsAuthorizationResult
import com.whereareyou.core.rules.SmsCommandAuthorizer
import com.whereareyou.core.rules.SmsCommandKind
import java.time.Duration
import java.time.Instant

/**
 * Concrete [SmsCommandAuthorizer]: the full authorization pipeline from
 * ANDROID_ARCHITECTURE.md section 5 "SMS command rule" and SMS_PROTOCOL.md section 10.
 *
 * Checks, in order: capability enabled -> command syntax recognized (compact machine form
 * or human phrase form) -> shared secret matches -> not a replay -> under the per-contact
 * rate limit. Never returns any protected data on rejection — only a local-audit reason.
 */
class SmsCommandAuthenticator(
    private val replayGuard: ReplayGuard = ReplayGuard(),
    private val rateLimiter: RateLimiter = RateLimiter(maxEvents = 5, window = Duration.ofHours(1)),
    private val recognizedPhrases: Set<String> = SmsCommandPhraseParser.DEFAULT_PHRASES,
) : SmsCommandAuthorizer {

    private data class ParsedCommand(val kind: SmsCommandKind, val requestId: String, val authCode: String)

    override fun authorize(
        event: TriggerEvent.SmsCommandReceived,
        contact: TrustedContact,
        at: Instant,
    ): SmsAuthorizationResult {
        if (!contact.enabled || !contact.capabilities.smsCommand) {
            return SmsAuthorizationResult.Rejected("capability disabled")
        }

        val secret = contact.commandSecret
        if (secret.isNullOrBlank()) {
            return SmsAuthorizationResult.Rejected("no shared secret configured")
        }

        val parsed = parseCommand(event.body) ?: return SmsAuthorizationResult.Rejected("unrecognized command format")

        if (parsed.authCode != secret) {
            return SmsAuthorizationResult.Rejected("invalid auth code")
        }

        val replayKey = "${contact.id.value}:${parsed.requestId}"
        if (!replayGuard.recordIfNew(replayKey, at)) {
            return SmsAuthorizationResult.Rejected("replayed request")
        }

        if (!rateLimiter.tryAcquire(contact.id.value, at)) {
            return SmsAuthorizationResult.Rejected("rate limit exceeded")
        }

        return SmsAuthorizationResult.Authorized(
            kind = parsed.kind,
            requestId = replayKey,
            evidence = "sms ${parsed.kind.name.lowercase()} from ${contact.displayName}",
        )
    }

    private fun parseCommand(body: String): ParsedCommand? {
        val trimmed = body.trim()
        if (trimmed.startsWith(COMPACT_MARKER_PREFIX)) {
            return when (val result = CommandProtocol.decode(trimmed)) {
                is CommandDecodeResult.Success -> ParsedCommand(
                    kind = result.payload.command.toSmsCommandKind(),
                    requestId = "cmd:${result.payload.requestId}",
                    authCode = result.payload.authCode,
                )
                is CommandDecodeResult.Failure -> null
            }
        }

        val phrase = SmsCommandPhraseParser.parse(trimmed, recognizedPhrases) ?: return null
        return ParsedCommand(
            kind = SmsCommandKind.STATUS_REQUEST,
            requestId = "phrase:${phrase.phrase}",
            authCode = phrase.code,
        )
    }

    private fun CommandCode.toSmsCommandKind(): SmsCommandKind = when (this) {
        CommandCode.STATUS_REQUEST -> SmsCommandKind.STATUS_REQUEST
        CommandCode.EMERGENCY_CALLBACK_REQUEST -> SmsCommandKind.EMERGENCY_CALLBACK_REQUEST
    }

    private companion object {
        const val COMPACT_MARKER_PREFIX = "ک"
    }
}
