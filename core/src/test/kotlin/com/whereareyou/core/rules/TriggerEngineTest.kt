package com.whereareyou.core.rules

import com.whereareyou.core.model.ContactCapabilities
import com.whereareyou.core.model.EmergencyCallbackMode
import com.whereareyou.core.model.SafetyTriggerType
import com.whereareyou.core.model.TriggerEvent
import com.whereareyou.core.model.TrustedContact
import com.whereareyou.core.model.TrustedContactId
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private class FakeSmsCommandAuthorizer(
    private val result: (TriggerEvent.SmsCommandReceived, TrustedContact) -> SmsAuthorizationResult,
) : SmsCommandAuthorizer {
    override fun authorize(
        event: TriggerEvent.SmsCommandReceived,
        contact: TrustedContact,
        at: Instant,
    ): SmsAuthorizationResult = result(event, contact)
}

class TriggerEngineTest {

    private val base: Instant = Instant.parse("2026-08-23T12:00:00Z")

    private fun contact(
        id: String = "a",
        canonical: String = "+989121234567",
        enabled: Boolean = true,
        missedCallTrigger: Boolean = true,
        smsCommand: Boolean = true,
    ) = TrustedContact(
        id = TrustedContactId(id),
        displayName = "Contact $id",
        canonicalPhoneNumber = canonical,
        displayPhoneNumber = canonical,
        enabled = enabled,
        capabilities = ContactCapabilities(
            missedCallTrigger = missedCallTrigger,
            smsCommand = smsCommand,
            receiveLowBatteryAlert = false,
            manualStatusRecipient = true,
            emergencyCallback = EmergencyCallbackMode.OFF,
        ),
        commandSecret = "7314",
    )

    private fun engine(
        contacts: TrustedContactLookup,
        smsAuthorizer: SmsCommandAuthorizer = FakeSmsCommandAuthorizer { _, _ -> SmsAuthorizationResult.Rejected("unused") },
        hasActiveSession: (TrustedContactId?) -> Boolean = { false },
    ) = TriggerEngine(
        contacts = contacts,
        missedCallEvaluator = MissedCallEvaluator(),
        smsCommandAuthorizer = smsAuthorizer,
        hasActiveSession = hasActiveSession,
    )

    @Test
    fun `three missed calls from a trusted authorized contact trigger a session`() {
        val a = contact()
        val lookup = InMemoryTrustedContactLookup(listOf(a))
        val engine = engine(lookup)

        engine.evaluate(TriggerEvent.MissedCallObserved("0912 123 4567", "call-1", base))
        engine.evaluate(TriggerEvent.MissedCallObserved("0912 123 4567", "call-2", base.plus(Duration.ofMinutes(20))))
        val trigger = engine.evaluate(
            TriggerEvent.MissedCallObserved("0912 123 4567", "call-3", base.plus(Duration.ofMinutes(40))),
        )

        assertNotNull(trigger)
        assertEquals(SafetyTriggerType.REPEATED_MISSED_CALLS, trigger.type)
        assertEquals(a.id, trigger.contactId)
    }

    @Test
    fun `missed calls from an unknown number never trigger anything`() {
        val lookup = InMemoryTrustedContactLookup(listOf(contact()))
        val engine = engine(lookup)

        var trigger = engine.evaluate(TriggerEvent.MissedCallObserved("+15555550100", "call-1", base))
        trigger = engine.evaluate(TriggerEvent.MissedCallObserved("+15555550100", "call-2", base.plus(Duration.ofMinutes(1))))
            ?: trigger
        trigger = engine.evaluate(TriggerEvent.MissedCallObserved("+15555550100", "call-3", base.plus(Duration.ofMinutes(2))))
            ?: trigger

        assertNull(trigger)
    }

    @Test
    fun `disabled contact cannot trigger a session`() {
        val a = contact(enabled = false)
        val lookup = InMemoryTrustedContactLookup(listOf(a))
        val engine = engine(lookup)

        var trigger: com.whereareyou.core.model.SafetyTrigger? = null
        repeat(3) { i ->
            trigger = engine.evaluate(
                TriggerEvent.MissedCallObserved("0912 123 4567", "call-$i", base.plus(Duration.ofMinutes(i.toLong()))),
            ) ?: trigger
        }
        assertNull(trigger)
    }

    @Test
    fun `contact without missedCallTrigger capability cannot trigger via missed calls`() {
        val a = contact(missedCallTrigger = false)
        val lookup = InMemoryTrustedContactLookup(listOf(a))
        val engine = engine(lookup)

        var trigger: com.whereareyou.core.model.SafetyTrigger? = null
        repeat(3) { i ->
            trigger = engine.evaluate(
                TriggerEvent.MissedCallObserved("0912 123 4567", "call-$i", base.plus(Duration.ofMinutes(i.toLong()))),
            ) ?: trigger
        }
        assertNull(trigger)
    }

    @Test
    fun `duplicate call-log id is not double counted`() {
        val a = contact()
        val lookup = InMemoryTrustedContactLookup(listOf(a))
        val engine = engine(lookup)

        engine.evaluate(TriggerEvent.MissedCallObserved("0912 123 4567", "call-1", base))
        // Same call-log id delivered twice (duplicated broadcast), must count once.
        engine.evaluate(TriggerEvent.MissedCallObserved("0912 123 4567", "call-1", base.plus(Duration.ofMinutes(1))))
        val trigger = engine.evaluate(
            TriggerEvent.MissedCallObserved("0912 123 4567", "call-2", base.plus(Duration.ofMinutes(2))),
        )

        assertNull(trigger) // only 2 distinct calls recorded, threshold is 3
    }

    @Test
    fun `repeated trigger is suppressed while a session is already active for that contact`() {
        val a = contact()
        val lookup = InMemoryTrustedContactLookup(listOf(a))
        val engine = engine(lookup, hasActiveSession = { it == a.id })

        val trigger = engine.evaluate(TriggerEvent.MissedCallObserved("0912 123 4567", "call-1", base))
        assertNull(trigger)
    }

    @Test
    fun `authorized sms command produces a trigger`() {
        val a = contact()
        val lookup = InMemoryTrustedContactLookup(listOf(a))
        val authorizer = FakeSmsCommandAuthorizer { _, _ ->
            SmsAuthorizationResult.Authorized(SmsCommandKind.STATUS_REQUEST, "rid-1", "status request")
        }
        val engine = engine(lookup, smsAuthorizer = authorizer)

        val trigger = engine.evaluate(TriggerEvent.SmsCommandReceived("0912 123 4567", "وضعیت 7314", base))

        assertNotNull(trigger)
        assertEquals(SafetyTriggerType.SMS_COMMAND, trigger.type)
        assertEquals("rid-1", trigger.replayKey)
    }

    @Test
    fun `rejected sms command never produces a trigger`() {
        val a = contact()
        val lookup = InMemoryTrustedContactLookup(listOf(a))
        val authorizer = FakeSmsCommandAuthorizer { _, _ -> SmsAuthorizationResult.Rejected("bad auth code") }
        val engine = engine(lookup, smsAuthorizer = authorizer)

        val trigger = engine.evaluate(TriggerEvent.SmsCommandReceived("0912 123 4567", "وضعیت 0000", base))

        assertNull(trigger)
    }

    @Test
    fun `sms command capability disabled rejects before authorizer is even consulted`() {
        val a = contact(smsCommand = false)
        val lookup = InMemoryTrustedContactLookup(listOf(a))
        val authorizer = FakeSmsCommandAuthorizer { _, _ ->
            SmsAuthorizationResult.Authorized(SmsCommandKind.STATUS_REQUEST, "rid-1", "status request")
        }
        val engine = engine(lookup, smsAuthorizer = authorizer)

        val trigger = engine.evaluate(TriggerEvent.SmsCommandReceived("0912 123 4567", "وضعیت 7314", base))

        assertNull(trigger)
    }

    @Test
    fun `low battery always produces a trigger with no contact`() {
        val lookup = InMemoryTrustedContactLookup()
        val engine = engine(lookup)

        val trigger = engine.evaluate(TriggerEvent.SystemLowBattery(9, base))

        assertNotNull(trigger)
        assertEquals(SafetyTriggerType.LOW_BATTERY, trigger.type)
        assertNull(trigger.contactId)
    }

    @Test
    fun `manual status always produces a trigger`() {
        val lookup = InMemoryTrustedContactLookup()
        val engine = engine(lookup)
        val recipient = TrustedContactId("a")

        val trigger = engine.evaluate(TriggerEvent.ManualStatusRequested(listOf(recipient), base))

        assertNotNull(trigger)
        assertEquals(SafetyTriggerType.MANUAL_STATUS, trigger.type)
        assertEquals(recipient, trigger.contactId)
    }

    @Test
    fun `answered call resets the missed-call window through the engine`() {
        val a = contact()
        val lookup = InMemoryTrustedContactLookup(listOf(a))
        val engine = engine(lookup)

        engine.evaluate(TriggerEvent.MissedCallObserved("0912 123 4567", "call-1", base))
        engine.evaluate(TriggerEvent.MissedCallObserved("0912 123 4567", "call-2", base.plus(Duration.ofMinutes(5))))
        engine.evaluate(TriggerEvent.AnsweredCallObserved("0912 123 4567", base.plus(Duration.ofMinutes(6))))
        val trigger = engine.evaluate(
            TriggerEvent.MissedCallObserved("0912 123 4567", "call-3", base.plus(Duration.ofMinutes(7))),
        )

        assertNull(trigger)
    }
}
