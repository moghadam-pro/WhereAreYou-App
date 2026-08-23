package com.whereareyou.core.rules

import com.whereareyou.core.model.SafetySession
import com.whereareyou.core.model.SafetySessionLimits
import com.whereareyou.core.model.SafetySessionState
import com.whereareyou.core.model.SafetyTrigger
import com.whereareyou.core.model.SessionCompletionReason
import java.time.Instant

/** Inputs the [SafetySessionMachine] reducer accepts, driven by the app-layer orchestrator/scheduler. */
sealed interface SessionEvent {
    val at: Instant

    /** A location/device snapshot was gathered (does not by itself change state). */
    data class SampleCollected(override val at: Instant) : SessionEvent

    /** An SMS response was sent; may complete the session if a bound is now reached. */
    data class MessageSent(override val at: Instant) : SessionEvent

    /** The best-effort follow-up scheduler fired (~T+2m / ~T+5m, ANDROID_ARCHITECTURE.md section 7). */
    data class FollowUpDue(override val at: Instant) : SessionEvent

    /** Sending failed in a way that cannot be retried within this session (no SIM, permission revoked, ...). */
    data class TransportFailed(override val at: Instant, val reason: String) : SessionEvent

    /** The protected user cancelled the session from the Protect UI. */
    data class CancelledLocally(override val at: Instant, val reason: String) : SessionEvent

    /** The session's max-lifetime bound was reached without hitting max samples/messages. */
    data class TimedOut(override val at: Instant) : SessionEvent
}

/**
 * Pure reducer over [SafetySession] (ANDROID_ARCHITECTURE.md section 3 state diagram +
 * PRODUCT_SPEC.md section 7). Every transition is a `copy()`; nothing here touches a
 * clock, a scheduler or Android — callers decide *when* to feed events, this only decides
 * what a given event does to the session, so the whole lifecycle is unit-testable
 * (AGENTS.md "Testing priority": "safety-session termination").
 */
object SafetySessionMachine {

    fun create(id: String, trigger: SafetyTrigger, at: Instant, limits: SafetySessionLimits = SafetySessionLimits()): SafetySession =
        SafetySession(
            id = id,
            triggerType = trigger.type,
            requesterContactId = trigger.contactId,
            startedAt = at,
            state = SafetySessionState.CREATED,
            limits = limits,
        )

    /**
     * Creates an audit-only record for a trigger that arrived while its contact/scope was
     * already in cooldown (SECURITY_PRIVACY.md section 12: rejected attempts are still
     * logged locally). This record starts and stays in [SafetySessionState.COOLDOWN] and
     * is never fed to [reduce].
     */
    fun createSuppressedByCooldown(
        id: String,
        trigger: SafetyTrigger,
        at: Instant,
        limits: SafetySessionLimits = SafetySessionLimits(),
    ): SafetySession =
        SafetySession(
            id = id,
            triggerType = trigger.type,
            requesterContactId = trigger.contactId,
            startedAt = at,
            state = SafetySessionState.COOLDOWN,
            limits = limits,
        )

    /** Starts collection for a freshly [create]d session. */
    fun begin(session: SafetySession): SafetySession {
        if (session.state != SafetySessionState.CREATED) return session
        return session.copy(state = SafetySessionState.COLLECTING_INITIAL)
    }

    fun reduce(session: SafetySession, event: SessionEvent): SafetySession {
        if (!session.isActive) return session // terminal or suppressed: no-op, safe against late/duplicate events

        return when (event) {
            is SessionEvent.SampleCollected ->
                session.copy(samplesCollected = session.samplesCollected + 1)

            is SessionEvent.MessageSent -> {
                val messagesSent = session.messagesSent + 1
                val withMessage = session.copy(messagesSent = messagesSent)
                when {
                    messagesSent >= session.limits.maxMessages ->
                        withMessage.complete(SessionCompletionReason.MAX_MESSAGES_REACHED)
                    session.samplesCollected >= session.limits.maxSamples ->
                        withMessage.complete(SessionCompletionReason.MAX_SAMPLES_REACHED)
                    session.state == SafetySessionState.CREATED || session.state == SafetySessionState.COLLECTING_INITIAL ->
                        withMessage.copy(state = SafetySessionState.FIRST_RESPONSE_SENT)
                    else ->
                        withMessage.copy(state = SafetySessionState.WAITING_FOR_FOLLOWUP)
                }
            }

            is SessionEvent.FollowUpDue -> {
                if (session.samplesCollected >= session.limits.maxSamples) {
                    session.complete(SessionCompletionReason.MAX_SAMPLES_REACHED)
                } else {
                    session.copy(state = SafetySessionState.COLLECTING_FOLLOWUP)
                }
            }

            is SessionEvent.TransportFailed ->
                session.copy(state = SafetySessionState.FAILED, completionReason = SessionCompletionReason.FAILED_TRANSPORT)

            is SessionEvent.CancelledLocally ->
                session.copy(
                    state = SafetySessionState.CANCELLED,
                    cancellationReason = event.reason,
                    completionReason = SessionCompletionReason.CANCELLED_LOCALLY,
                )

            is SessionEvent.TimedOut ->
                session.complete(SessionCompletionReason.TIMEOUT)
        }
    }

    private fun SafetySession.complete(reason: SessionCompletionReason): SafetySession =
        copy(state = SafetySessionState.COMPLETED, completionReason = reason)
}
