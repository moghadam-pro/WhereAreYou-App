package com.whereareyou.core.protocol

import com.whereareyou.core.model.InternetState
import kotlin.math.roundToLong

/** A location component of a [StatusPayload], or absent if no valid fix exists. */
data class EncodedLocation(
    val latitude: Double,
    val longitude: Double,
    /** Meters, clamped/rounded to the wire representable range 0..9999 (SMS_PROTOCOL.md section 5). */
    val accuracyMeters: Int,
)

/**
 * Phase 1 status response payload (SMS_PROTOCOL.md section 4). [location] is `null` when no
 * valid last-known/fresh fix exists — the wire format never fabricates coordinates.
 */
data class StatusPayload(
    val version: Int = StatusProtocol.VERSION,
    val sessionId: String,
    val sequence: Int,
    val location: EncodedLocation?,
    val batteryPercent: Int,
    val charging: Boolean,
    val internetState: InternetState,
    /** Epoch time in whole minutes (SMS_PROTOCOL.md section 5 "TIME"). */
    val timestampEpochMinute: Long,
)

sealed interface StatusDecodeResult {
    data class Success(val payload: StatusPayload) : StatusDecodeResult
    data class Failure(val reason: String) : StatusDecodeResult
}

/**
 * Encoder/decoder for the compact status response wire format described in
 * SMS_PROTOCOL.md:
 *
 * ```
 * و1|SID|SEQ|LAT|LON|ACC|BAT|CHG|NET|TIME
 * ```
 *
 * Deterministic and malformed-input-safe by design (AGENTS.md "SMS protocol" requirements):
 * [decode] never throws, always returns a [StatusDecodeResult].
 */
object StatusProtocol {
    const val VERSION = 1
    private const val MARKER_PREFIX = "و"
    private const val FIELD_SEPARATOR = "|"
    private const val MISSING = "-"
    private const val E5_SCALE = 100_000.0
    private const val FIELD_COUNT = 10
    private const val MAX_ACCURACY_METERS = 9999

    private val SUPPORTED_VERSIONS = setOf(1)

    fun encode(payload: StatusPayload): String {
        val location = payload.location
        val fields = listOf(
            "$MARKER_PREFIX${payload.version}",
            payload.sessionId,
            payload.sequence.toString(),
            location?.let { encodeCoordinate(it.latitude) } ?: MISSING,
            location?.let { encodeCoordinate(it.longitude) } ?: MISSING,
            location?.let { clampAccuracy(it.accuracyMeters).toString() } ?: MISSING,
            payload.batteryPercent.toString(),
            if (payload.charging) "1" else "0",
            encodeInternetState(payload.internetState),
            payload.timestampEpochMinute.toString(),
        )
        return fields.joinToString(FIELD_SEPARATOR)
    }

    fun decode(raw: String): StatusDecodeResult {
        val trimmed = raw.trim()
        val fields = trimmed.split(FIELD_SEPARATOR)
        if (fields.size != FIELD_COUNT) {
            return StatusDecodeResult.Failure("expected $FIELD_COUNT fields, got ${fields.size}")
        }

        val (markerField, sessionId, seqField, latField, lonField, accField, batField, chgField, netField, timeField) =
            FieldWindow(fields)

        val version = parseMarkerVersion(markerField)
            ?: return StatusDecodeResult.Failure("malformed version marker: '$markerField'")
        if (version !in SUPPORTED_VERSIONS) {
            return StatusDecodeResult.Failure("unsupported protocol version: $version")
        }

        if (sessionId.isBlank()) return StatusDecodeResult.Failure("missing session id")

        val sequence = seqField.toIntOrNull()
            ?: return StatusDecodeResult.Failure("malformed sequence: '$seqField'")

        val location = when {
            latField == MISSING && lonField == MISSING && accField == MISSING -> null
            latField == MISSING || lonField == MISSING || accField == MISSING ->
                return StatusDecodeResult.Failure("partial location fields")
            else -> {
                val lat = decodeCoordinate(latField)
                    ?: return StatusDecodeResult.Failure("malformed latitude: '$latField'")
                val lon = decodeCoordinate(lonField)
                    ?: return StatusDecodeResult.Failure("malformed longitude: '$lonField'")
                val acc = accField.toIntOrNull()
                    ?: return StatusDecodeResult.Failure("malformed accuracy: '$accField'")
                EncodedLocation(lat, lon, clampAccuracy(acc))
            }
        }

        val battery = batField.toIntOrNull()
            ?: return StatusDecodeResult.Failure("malformed battery: '$batField'")
        if (battery !in 0..100) return StatusDecodeResult.Failure("battery out of range: $battery")

        val charging = when (chgField) {
            "0" -> false
            "1" -> true
            else -> return StatusDecodeResult.Failure("malformed charging flag: '$chgField'")
        }

        val internetState = decodeInternetState(netField)
            ?: return StatusDecodeResult.Failure("malformed network state: '$netField'")

        val timestamp = timeField.toLongOrNull()
            ?: return StatusDecodeResult.Failure("malformed timestamp: '$timeField'")

        return StatusDecodeResult.Success(
            StatusPayload(
                version = version,
                sessionId = sessionId,
                sequence = sequence,
                location = location,
                batteryPercent = battery,
                charging = charging,
                internetState = internetState,
                timestampEpochMinute = timestamp,
            ),
        )
    }

    private fun encodeCoordinate(value: Double): String = (value * E5_SCALE).roundToLong().toString()

    private fun decodeCoordinate(field: String): Double? = field.toLongOrNull()?.let { it / E5_SCALE }

    private fun clampAccuracy(meters: Int): Int = meters.coerceIn(0, MAX_ACCURACY_METERS)

    private fun encodeInternetState(state: InternetState): String = when (state) {
        InternetState.UNAVAILABLE -> "0"
        InternetState.VALIDATED -> "1"
        InternetState.UNKNOWN -> "2"
    }

    private fun decodeInternetState(field: String): InternetState? = when (field) {
        "0" -> InternetState.UNAVAILABLE
        "1" -> InternetState.VALIDATED
        "2" -> InternetState.UNKNOWN
        else -> null
    }

    private fun parseMarkerVersion(field: String): Int? {
        if (!field.startsWith(MARKER_PREFIX)) return null
        return field.removePrefix(MARKER_PREFIX).toIntOrNull()
    }

    /** Destructuring helper for the fixed 10-field wire format (data classes auto-generate componentN). */
    private data class FieldWindow(
        val f0: String, val f1: String, val f2: String, val f3: String, val f4: String,
        val f5: String, val f6: String, val f7: String, val f8: String, val f9: String,
    ) {
        companion object {
            operator fun invoke(fields: List<String>) = FieldWindow(
                fields[0], fields[1], fields[2], fields[3], fields[4],
                fields[5], fields[6], fields[7], fields[8], fields[9],
            )
        }
    }
}
