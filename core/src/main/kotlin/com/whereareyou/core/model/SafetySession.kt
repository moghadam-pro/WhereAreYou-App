package com.whereareyou.core.model

import java.time.Duration
import java.time.Instant

/** Lifecycle states from ANDROID_ARCHITECTURE.md section 3 "SafetySession". */
enum class SafetySessionState {
    CREATED,
    COLLECTING_INITIAL,
    FIRST_RESPONSE_SENT,
    WAITING_FOR_FOLLOWUP,
    COLLECTING_FOLLOWUP,
    COMPLETED,
    CANCELLED,
    FAILED,
    COOLDOWN,
}

/** Why a session stopped collecting/sending. */
enum class SessionCompletionReason {
    MAX_SAMPLES_REACHED,
    MAX_MESSAGES_REACHED,
    TIMEOUT,
    CANCELLED_LOCALLY,
    FAILED_TRANSPORT,
    SUPERSEDED,
}

/** Bounds a session must respect (AGENTS.md invariant #9). */
data class SafetySessionLimits(
    val maxSamples: Int = 3,
    val maxMessages: Int = 3,
    val maxLifetime: Duration = Duration.ofMinutes(10),
) {
    init {
        require(maxSamples > 0) { "maxSamples must be positive" }
        require(maxMessages > 0) { "maxMessages must be positive" }
        require(!maxLifetime.isNegative && !maxLifetime.isZero) { "maxLifetime must be positive" }
    }
}

/**
 * A short-lived, event-scoped safety session (PRODUCT_SPEC.md section 7).
 *
 * Instances are immutable; transitions are produced by
 * [com.whereareyou.core.rules.SafetySessionMachine] as pure `copy()`-based reductions,
 * so the whole lifecycle is unit-testable without any Android scheduler.
 */
data class SafetySession(
    val id: String,
    val triggerType: SafetyTriggerType,
    val requesterContactId: TrustedContactId?,
    val startedAt: Instant,
    val state: SafetySessionState,
    val limits: SafetySessionLimits = SafetySessionLimits(),
    val samplesCollected: Int = 0,
    val messagesSent: Int = 0,
    val cancellationReason: String? = null,
    val completionReason: SessionCompletionReason? = null,
) {
    /** True while still progressing through the normal collect/send lifecycle. */
    val isActive: Boolean
        get() = state in ACTIVE_STATES

    /** True once finished — no further reduction should be applied. */
    val isTerminal: Boolean
        get() = state in TERMINAL_STATES

    companion object {
        val ACTIVE_STATES = setOf(
            SafetySessionState.CREATED,
            SafetySessionState.COLLECTING_INITIAL,
            SafetySessionState.FIRST_RESPONSE_SENT,
            SafetySessionState.WAITING_FOR_FOLLOWUP,
            SafetySessionState.COLLECTING_FOLLOWUP,
        )

        val TERMINAL_STATES = setOf(
            SafetySessionState.COMPLETED,
            SafetySessionState.CANCELLED,
            SafetySessionState.FAILED,
        )

        // COOLDOWN is neither: it represents an audit record for a trigger that was
        // suppressed because the contact/session scope was already in cooldown
        // (see SafetySessionMachine.createSuppressedByCooldown) — it never progresses.
    }
}
