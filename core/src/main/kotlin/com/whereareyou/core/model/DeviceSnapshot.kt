package com.whereareyou.core.model

import java.time.Duration
import java.time.Instant

/** Usable/validated internet reachability, not merely a radio being "on" (ANDROID_ARCHITECTURE.md section 10). */
enum class InternetState {
    VALIDATED,
    UNAVAILABLE,
    UNKNOWN,
}

/**
 * A single location fix. Every instance must be honest about [accuracyMeters] and
 * [capturedAt] — invariant #5/#6 in AGENTS.md: never let the UI/decoder treat a
 * stale sample as current.
 */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAt: Instant,
    /** Internal/debug only (e.g. "fused", "last-known", "network") — not necessarily transmitted. */
    val source: String? = null,
) {
    fun ageAt(now: Instant): Duration = Duration.between(capturedAt, now)
}

/**
 * The smallest useful set of device status data collected at session start
 * (ANDROID_ARCHITECTURE.md section 6 "Snapshot provider"). [location] is nullable:
 * a snapshot must remain useful even with no location available.
 */
data class DeviceSnapshot(
    val batteryPercent: Int,
    val charging: Boolean,
    val internetState: InternetState,
    val location: LocationSample?,
    val capturedAt: Instant,
) {
    init {
        require(batteryPercent in 0..100) { "batteryPercent must be 0..100, was $batteryPercent" }
    }
}
