package com.whereareyou.core.rules

import com.whereareyou.core.model.TrustedContactId
import java.time.Duration
import java.time.Instant

/** Configuration for the optional T2 aggregate-across-contacts rule (PRODUCT_SPEC.md section 6). */
data class AggregateMissedCallRuleConfig(
    val enabled: Boolean = false,
    val threshold: Int = 5,
    val window: Duration = Duration.ofMinutes(60),
    val cooldown: Duration = Duration.ofMinutes(60),
) {
    init {
        require(threshold > 0) { "threshold must be positive" }
        require(!window.isNegative && !window.isZero) { "window must be positive" }
        require(!cooldown.isNegative) { "cooldown must not be negative" }
    }
}

/**
 * Counts missed calls from *any* trusted, enabled contact towards one shared threshold,
 * independent of each contact's own [MissedCallEvaluator] state (T2, distinct/independently
 * switchable from T1 per PRODUCT_SPEC.md section 6). Only trusted contacts ever reach this
 * evaluator — non-trusted callers must be filtered out by the caller (TriggerEngine) before
 * calling [onMissedCall].
 */
class AggregateMissedCallEvaluator(private val config: AggregateMissedCallRuleConfig) {

    private data class Entry(val contactId: TrustedContactId, val at: Instant)

    private val recentCalls = mutableListOf<Entry>()
    private var cooldownUntil: Instant? = null

    fun onMissedCall(contactId: TrustedContactId, at: Instant): MissedCallEvaluation {
        if (!config.enabled) return MissedCallEvaluation.NotTriggered(0)

        val activeCooldown = cooldownUntil
        if (activeCooldown != null && at.isBefore(activeCooldown)) {
            return MissedCallEvaluation.InCooldown
        }

        recentCalls.add(Entry(contactId, at))
        val windowStart = at.minus(config.window)
        val relevant = recentCalls.filter { !it.at.isBefore(windowStart) }.sortedBy { it.at }

        return if (relevant.size >= config.threshold) {
            MissedCallEvaluation.Triggered(relevant.size, relevant.map { it.at })
        } else {
            MissedCallEvaluation.NotTriggered(relevant.size)
        }
    }

    fun onAnsweredCall(at: Instant) {
        // An answered call establishes contact was reachable; clear the shared counter
        // the same way a single-contact reset marker would.
        recentCalls.removeAll { !it.at.isAfter(at) }
    }

    fun onSessionCompleted(at: Instant) {
        recentCalls.clear()
        if (!config.cooldown.isZero) {
            cooldownUntil = at.plus(config.cooldown)
        }
    }
}
