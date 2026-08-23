package com.whereareyou.core.rules

import com.whereareyou.core.model.SafetySessionLimits
import com.whereareyou.core.model.SafetySessionState
import com.whereareyou.core.model.SafetyTrigger
import com.whereareyou.core.model.SafetyTriggerType
import com.whereareyou.core.model.SessionCompletionReason
import com.whereareyou.core.model.TrustedContactId
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SafetySessionMachineTest {

    private val base: Instant = Instant.parse("2026-08-23T12:00:00Z")

    private val trigger = SafetyTrigger(
        type = SafetyTriggerType.REPEATED_MISSED_CALLS,
        contactId = TrustedContactId("a"),
        occurredAt = base,
        evidence = "3 missed calls",
    )

    @Test
    fun `a freshly created session starts in CREATED state`() {
        val session = SafetySessionMachine.create("s1", trigger, base)
        assertEquals(SafetySessionState.CREATED, session.state)
        assertTrue(session.isActive)
    }

    @Test
    fun `begin moves a created session into collecting-initial`() {
        val session = SafetySessionMachine.begin(SafetySessionMachine.create("s1", trigger, base))
        assertEquals(SafetySessionState.COLLECTING_INITIAL, session.state)
    }

    @Test
    fun `first message sent from collecting-initial reaches FIRST_RESPONSE_SENT`() {
        var session = SafetySessionMachine.begin(SafetySessionMachine.create("s1", trigger, base, SafetySessionLimits(maxSamples = 3, maxMessages = 3)))
        session = SafetySessionMachine.reduce(session, SessionEvent.SampleCollected(base))
        session = SafetySessionMachine.reduce(session, SessionEvent.MessageSent(base))

        assertEquals(SafetySessionState.FIRST_RESPONSE_SENT, session.state)
        assertEquals(1, session.messagesSent)
        assertEquals(1, session.samplesCollected)
    }

    @Test
    fun `bounded follow-up cycles progress through waiting and collecting states`() {
        val limits = SafetySessionLimits(maxSamples = 3, maxMessages = 3)
        var session = SafetySessionMachine.begin(SafetySessionMachine.create("s1", trigger, base, limits))

        // T+0: initial sample + first response.
        session = SafetySessionMachine.reduce(session, SessionEvent.SampleCollected(base))
        session = SafetySessionMachine.reduce(session, SessionEvent.MessageSent(base))
        assertEquals(SafetySessionState.FIRST_RESPONSE_SENT, session.state)

        // ~T+2m follow-up.
        val tPlus2 = base.plus(Duration.ofMinutes(2))
        session = SafetySessionMachine.reduce(session, SessionEvent.FollowUpDue(tPlus2))
        assertEquals(SafetySessionState.COLLECTING_FOLLOWUP, session.state)
        session = SafetySessionMachine.reduce(session, SessionEvent.SampleCollected(tPlus2))
        session = SafetySessionMachine.reduce(session, SessionEvent.MessageSent(tPlus2))
        assertEquals(SafetySessionState.WAITING_FOR_FOLLOWUP, session.state)
        assertEquals(2, session.samplesCollected)
        assertEquals(2, session.messagesSent)
    }

    @Test
    fun `session completes once max samples is reached`() {
        val limits = SafetySessionLimits(maxSamples = 2, maxMessages = 5)
        var session = SafetySessionMachine.begin(SafetySessionMachine.create("s1", trigger, base, limits))

        session = SafetySessionMachine.reduce(session, SessionEvent.SampleCollected(base))
        session = SafetySessionMachine.reduce(session, SessionEvent.MessageSent(base))

        val tPlus2 = base.plus(Duration.ofMinutes(2))
        session = SafetySessionMachine.reduce(session, SessionEvent.FollowUpDue(tPlus2))
        session = SafetySessionMachine.reduce(session, SessionEvent.SampleCollected(tPlus2))
        session = SafetySessionMachine.reduce(session, SessionEvent.MessageSent(tPlus2))

        assertEquals(SafetySessionState.COMPLETED, session.state)
        assertEquals(SessionCompletionReason.MAX_SAMPLES_REACHED, session.completionReason)
        assertTrue(session.isTerminal)
    }

    @Test
    fun `session completes once max messages is reached even with samples remaining`() {
        val limits = SafetySessionLimits(maxSamples = 5, maxMessages = 1)
        var session = SafetySessionMachine.begin(SafetySessionMachine.create("s1", trigger, base, limits))

        session = SafetySessionMachine.reduce(session, SessionEvent.SampleCollected(base))
        session = SafetySessionMachine.reduce(session, SessionEvent.MessageSent(base))

        assertEquals(SafetySessionState.COMPLETED, session.state)
        assertEquals(SessionCompletionReason.MAX_MESSAGES_REACHED, session.completionReason)
    }

    @Test
    fun `a follow-up that fires after samples are already exhausted completes the session`() {
        val limits = SafetySessionLimits(maxSamples = 1, maxMessages = 5)
        var session = SafetySessionMachine.begin(SafetySessionMachine.create("s1", trigger, base, limits))
        session = SafetySessionMachine.reduce(session, SessionEvent.SampleCollected(base))
        // Message not yet sent for the collected sample when the follow-up fires again.
        session = SafetySessionMachine.reduce(session, SessionEvent.FollowUpDue(base.plus(Duration.ofMinutes(2))))

        assertEquals(SafetySessionState.COMPLETED, session.state)
        assertEquals(SessionCompletionReason.MAX_SAMPLES_REACHED, session.completionReason)
    }

    @Test
    fun `explicit timeout completes the session`() {
        var session = SafetySessionMachine.begin(SafetySessionMachine.create("s1", trigger, base))
        session = SafetySessionMachine.reduce(session, SessionEvent.TimedOut(base.plus(Duration.ofMinutes(10))))

        assertEquals(SafetySessionState.COMPLETED, session.state)
        assertEquals(SessionCompletionReason.TIMEOUT, session.completionReason)
    }

    @Test
    fun `local cancellation stops the session and records the reason`() {
        var session = SafetySessionMachine.begin(SafetySessionMachine.create("s1", trigger, base))
        session = SafetySessionMachine.reduce(session, SessionEvent.CancelledLocally(base, "user tapped cancel"))

        assertEquals(SafetySessionState.CANCELLED, session.state)
        assertEquals("user tapped cancel", session.cancellationReason)
        assertEquals(SessionCompletionReason.CANCELLED_LOCALLY, session.completionReason)
    }

    @Test
    fun `transport failure marks the session failed`() {
        var session = SafetySessionMachine.begin(SafetySessionMachine.create("s1", trigger, base))
        session = SafetySessionMachine.reduce(session, SessionEvent.TransportFailed(base, "no SIM"))

        assertEquals(SafetySessionState.FAILED, session.state)
        assertEquals(SessionCompletionReason.FAILED_TRANSPORT, session.completionReason)
    }

    @Test
    fun `events after a terminal state are ignored`() {
        var session = SafetySessionMachine.begin(SafetySessionMachine.create("s1", trigger, base))
        session = SafetySessionMachine.reduce(session, SessionEvent.CancelledLocally(base, "cancelled"))
        val afterCancel = SafetySessionMachine.reduce(session, SessionEvent.MessageSent(base.plusSeconds(1)))

        assertEquals(session, afterCancel)
    }

    @Test
    fun `a trigger suppressed by cooldown produces an inactive audit-only record`() {
        val session = SafetySessionMachine.createSuppressedByCooldown("s1", trigger, base)
        assertEquals(SafetySessionState.COOLDOWN, session.state)
        assertTrue(!session.isActive)
        assertTrue(!session.isTerminal)
    }

    @Test
    fun `reduce is a no-op on a cooldown-suppressed record`() {
        val session = SafetySessionMachine.createSuppressedByCooldown("s1", trigger, base)
        val result = SafetySessionMachine.reduce(session, SessionEvent.MessageSent(base))
        assertEquals(session, result)
    }
}
