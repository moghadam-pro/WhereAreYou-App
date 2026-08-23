package com.whereareyou.core.model

import java.time.Instant

/** Which rule authorized starting a [SafetySession]. */
enum class SafetyTriggerType {
    REPEATED_MISSED_CALLS,
    AGGREGATE_MISSED_CALLS,
    SMS_COMMAND,
    LOW_BATTERY,
    MANUAL_STATUS,
}

/**
 * A [TriggerEvent] that has already passed authorization/rule evaluation and is
 * allowed to start a [SafetySession] (ANDROID_ARCHITECTURE.md section 3 "SafetyTrigger").
 *
 * This type only ever gets constructed by rule-evaluation code
 * (com.whereareyou.core.rules / com.whereareyou.core.security) — never directly
 * from a platform event adapter.
 */
data class SafetyTrigger(
    val type: SafetyTriggerType,
    val contactId: TrustedContactId?,
    val occurredAt: Instant,
    /** Human/log-readable evidence, e.g. "3 missed calls in 47m". Never sent over the wire. */
    val evidence: String,
    /** Replay/dedup key when this trigger originated from a message with a request ID. */
    val replayKey: String? = null,
)
