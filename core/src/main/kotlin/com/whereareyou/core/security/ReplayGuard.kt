package com.whereareyou.core.security

import java.time.Duration
import java.time.Instant

/**
 * TTL-bounded duplicate/replay detector for command request identifiers
 * (SMS_PROTOCOL.md section 10, SECURITY_PRIVACY.md section 11).
 *
 * A short TTL (rather than "remember forever") is deliberate: a carrier redelivering the
 * same SMS bytes seconds apart must be rejected as a replay, but a protected user's family
 * member legitimately re-sending the exact same phrase+code command weeks later must not be
 * permanently locked out.
 */
class ReplayGuard(private val ttl: Duration = Duration.ofMinutes(5), private val capacity: Int = 1000) {
    init {
        require(!ttl.isNegative && !ttl.isZero) { "ttl must be positive" }
        require(capacity > 0) { "capacity must be positive" }
    }

    private val lastSeenAt = LinkedHashMap<String, Instant>()

    /** Returns true and records [key]@[at] if it is not a replay within [ttl]; false if it is. */
    fun recordIfNew(key: String, at: Instant): Boolean {
        val last = lastSeenAt[key]
        if (last != null && Duration.between(last, at).abs() < ttl) {
            return false
        }
        lastSeenAt[key] = at
        if (lastSeenAt.size > capacity) {
            val oldest = lastSeenAt.entries.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
        return true
    }
}
