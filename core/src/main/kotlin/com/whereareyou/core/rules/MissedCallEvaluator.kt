package com.whereareyou.core.rules

import com.whereareyou.core.model.TrustedContactId
import java.time.Duration
import java.time.Instant

/**
 * Configuration for the same-contact repeated missed-call rule (T1, PRODUCT_SPEC.md
 * section 6). This is configuration, not hard-coded policy (AGENTS.md "Trigger defaults
 * for development") — callers may load a different [MissedCallRuleConfig] per install.
 */
data class MissedCallRuleConfig(
    val threshold: Int = 3,
    val window: Duration = Duration.ofMinutes(60),
    val cooldown: Duration = Duration.ofMinutes(60),
) {
    init {
        require(threshold > 0) { "threshold must be positive" }
        require(!window.isNegative && !window.isZero) { "window must be positive" }
        require(!cooldown.isNegative) { "cooldown must not be negative" }
    }
}

sealed interface MissedCallEvaluation {
    /** Calls counted towards this evaluation, oldest first. */
    data class Triggered(val count: Int, val evidence: List<Instant>) : MissedCallEvaluation
    data class NotTriggered(val count: Int) : MissedCallEvaluation
    object InCooldown : MissedCallEvaluation
}

/**
 * Per-contact "N missed calls within a trailing window" rule
 * (ANDROID_ARCHITECTURE.md section 5 "Same-contact missed-call rule").
 *
 * Stateful but pure/deterministic: all "now" values are passed in rather than read from
 * the system clock, so tests can construct exact scenarios from TEST_PLAN.md.
 */
class MissedCallEvaluator(private val config: MissedCallRuleConfig = MissedCallRuleConfig()) {

    private val callsByContact = mutableMapOf<TrustedContactId, MutableList<Instant>>()
    private val resetMarkerByContact = mutableMapOf<TrustedContactId, Instant>()
    private val cooldownUntilByContact = mutableMapOf<TrustedContactId, Instant>()

    /**
     * Epoch-millis snapshot for durable storage (see [com.whereareyou.core.security.StateCodec]).
     * Android may kill the process between broadcasts, so counters must survive restarts.
     */
    fun exportState(): Map<String, List<Long>> = buildMap {
        callsByContact.forEach { (id, calls) -> put("c:${id.value}", calls.map { it.toEpochMilli() }) }
        resetMarkerByContact.forEach { (id, at) -> put("r:${id.value}", listOf(at.toEpochMilli())) }
        cooldownUntilByContact.forEach { (id, at) -> put("d:${id.value}", listOf(at.toEpochMilli())) }
    }

    fun restoreState(state: Map<String, List<Long>>) {
        callsByContact.clear(); resetMarkerByContact.clear(); cooldownUntilByContact.clear()
        state.forEach { (key, values) ->
            val id = TrustedContactId(key.drop(2))
            when (key.take(2)) {
                "c:" -> callsByContact[id] = values.map(Instant::ofEpochMilli).toMutableList()
                "r:" -> values.firstOrNull()?.let { resetMarkerByContact[id] = Instant.ofEpochMilli(it) }
                "d:" -> values.firstOrNull()?.let { cooldownUntilByContact[id] = Instant.ofEpochMilli(it) }
            }
        }
    }

    /** Records a missed call and evaluates whether the rule now fires for [contactId]. */
    fun onMissedCall(contactId: TrustedContactId, at: Instant): MissedCallEvaluation {
        val cooldownUntil = cooldownUntilByContact[contactId]
        if (cooldownUntil != null && at.isBefore(cooldownUntil)) {
            return MissedCallEvaluation.InCooldown
        }

        val calls = callsByContact.getOrPut(contactId) { mutableListOf() }
        val windowStart = at.minus(config.window)
        calls.removeAll { it.isBefore(windowStart) } // bound memory: expired calls can never count again
        calls.add(at)

        val relevant = relevantCalls(contactId, calls, at)
        return if (relevant.size >= config.threshold) {
            MissedCallEvaluation.Triggered(relevant.size, relevant)
        } else {
            MissedCallEvaluation.NotTriggered(relevant.size)
        }
    }

    /** A call with [contactId] was answered — resets the counting window (PRODUCT_SPEC.md section 6). */
    fun onAnsweredCall(contactId: TrustedContactId, at: Instant) {
        resetMarkerByContact[contactId] = at
    }

    /** The protected user placed an outgoing call to [contactId] — also resets the window. */
    fun onOutgoingCallToContact(contactId: TrustedContactId, at: Instant) {
        resetMarkerByContact[contactId] = at
    }

    /** The session caused by [contactId]'s trigger finished — starts the rule cooldown. */
    fun onSessionCompleted(contactId: TrustedContactId, at: Instant) {
        resetMarkerByContact[contactId] = at
        if (!config.cooldown.isZero) {
            cooldownUntilByContact[contactId] = at.plus(config.cooldown)
        }
    }

    /** Local cancellation with an explicit suppression window (PRODUCT_SPEC.md section 6). */
    fun onLocalSuppression(contactId: TrustedContactId, at: Instant, suppressFor: Duration) {
        resetMarkerByContact[contactId] = at
        if (!suppressFor.isNegative && !suppressFor.isZero) {
            cooldownUntilByContact[contactId] = at.plus(suppressFor)
        }
    }

    /** Current relevant-window count without recording a new call, for UI/diagnostics. */
    fun currentCount(contactId: TrustedContactId, at: Instant): Int {
        val calls = callsByContact[contactId] ?: return 0
        return relevantCalls(contactId, calls, at).size
    }

    private fun relevantCalls(contactId: TrustedContactId, calls: List<Instant>, at: Instant): List<Instant> {
        val windowStart = at.minus(config.window)
        val resetMarker = resetMarkerByContact[contactId]
        return calls
            .filter { t -> !t.isBefore(windowStart) && (resetMarker == null || t.isAfter(resetMarker)) }
            .sorted()
    }
}
