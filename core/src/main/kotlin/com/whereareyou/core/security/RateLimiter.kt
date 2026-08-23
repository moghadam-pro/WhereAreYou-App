package com.whereareyou.core.security

import java.time.Duration
import java.time.Instant

/**
 * Sliding-window rate limiter keyed by an arbitrary string (contact id, "global", etc).
 * Used to cap SMS command requests, safety sessions per contact, and Emergency Callback
 * attempts (SECURITY_PRIVACY.md section 11).
 */
class RateLimiter(private val maxEvents: Int, private val window: Duration) {
    init {
        require(maxEvents > 0) { "maxEvents must be positive" }
        require(!window.isNegative && !window.isZero) { "window must be positive" }
    }

    private val eventsByKey = mutableMapOf<String, MutableList<Instant>>()

    /** Returns true and records the attempt if [key] is under its limit at [at]; false otherwise. */
    fun tryAcquire(key: String, at: Instant): Boolean {
        val events = eventsByKey.getOrPut(key) { mutableListOf() }
        val windowStart = at.minus(window)
        events.removeAll { it.isBefore(windowStart) }
        if (events.size >= maxEvents) return false
        events.add(at)
        return true
    }

    /** Current in-window count for [key] without recording a new attempt, for UI/diagnostics. */
    fun currentCount(key: String, at: Instant): Int {
        val events = eventsByKey[key] ?: return 0
        val windowStart = at.minus(window)
        return events.count { !it.isBefore(windowStart) }
    }
}
