package com.whereareyou.core.rules

/**
 * Bounded recently-seen-id set. Call-state broadcasts can be noisy/duplicated
 * (ANDROID_ARCHITECTURE.md section 4), so the same call-log row must never be counted
 * twice. Bounded to avoid unbounded growth from a long-running process.
 */
class CallLogDeduplicator(private val capacity: Int = 500) {
    private val seen = LinkedHashSet<String>()

    /** Returns true and records [callLogId] if it has not been seen before; false if it was a repeat. */
    fun recordIfNew(callLogId: String): Boolean {
        if (!seen.add(callLogId)) return false
        if (seen.size > capacity) {
            val oldest = seen.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
        return true
    }
}
