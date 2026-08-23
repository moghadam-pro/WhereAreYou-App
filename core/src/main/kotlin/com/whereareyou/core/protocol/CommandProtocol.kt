package com.whereareyou.core.protocol

/** Wire command codes (SMS_PROTOCOL.md section 9). */
enum class CommandCode(val wireValue: Int) {
    STATUS_REQUEST(1),
    EMERGENCY_CALLBACK_REQUEST(2),
    ;

    companion object {
        fun fromWireValue(value: Int): CommandCode? = values().firstOrNull { it.wireValue == value }
    }
}

/** Compact machine command payload, reserved for a future PWA/Shortcut command generator. */
data class CommandPayload(
    val version: Int = CommandProtocol.VERSION,
    val command: CommandCode,
    /** Request ID used for deduplication/replay handling. */
    val requestId: String,
    /** User-configured shared code in the first prototype (SMS_PROTOCOL.md section 9). */
    val authCode: String,
)

sealed interface CommandDecodeResult {
    data class Success(val payload: CommandPayload) : CommandDecodeResult
    data class Failure(val reason: String) : CommandDecodeResult
}

/**
 * Encoder/decoder for the compact machine command form:
 *
 * ```
 * ک1|CMD|RID|AUTH
 * ```
 *
 * (SMS_PROTOCOL.md section 9 "Compact machine command form"). This is distinct from the
 * human-friendly phrase form handled by [SmsCommandPhraseParser].
 */
object CommandProtocol {
    const val VERSION = 1
    private const val MARKER_PREFIX = "ک"
    private const val FIELD_SEPARATOR = "|"
    private const val FIELD_COUNT = 4

    private val SUPPORTED_VERSIONS = setOf(1)

    fun encode(payload: CommandPayload): String =
        listOf(
            "$MARKER_PREFIX${payload.version}",
            payload.command.wireValue.toString(),
            payload.requestId,
            payload.authCode,
        ).joinToString(FIELD_SEPARATOR)

    fun decode(raw: String): CommandDecodeResult {
        val fields = raw.trim().split(FIELD_SEPARATOR)
        if (fields.size != FIELD_COUNT) {
            return CommandDecodeResult.Failure("expected $FIELD_COUNT fields, got ${fields.size}")
        }
        val (markerField, cmdField, rid, auth) = fields

        if (!markerField.startsWith(MARKER_PREFIX)) {
            return CommandDecodeResult.Failure("malformed version marker: '$markerField'")
        }
        val version = markerField.removePrefix(MARKER_PREFIX).toIntOrNull()
            ?: return CommandDecodeResult.Failure("malformed version marker: '$markerField'")
        if (version !in SUPPORTED_VERSIONS) {
            return CommandDecodeResult.Failure("unsupported protocol version: $version")
        }

        val cmdValue = cmdField.toIntOrNull()
            ?: return CommandDecodeResult.Failure("malformed command code: '$cmdField'")
        val command = CommandCode.fromWireValue(cmdValue)
            ?: return CommandDecodeResult.Failure("unknown command code: $cmdValue")

        if (rid.isBlank()) return CommandDecodeResult.Failure("missing request id")
        if (auth.isBlank()) return CommandDecodeResult.Failure("missing auth code")

        return CommandDecodeResult.Success(CommandPayload(version, command, rid, auth))
    }
}
