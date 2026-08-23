package com.whereareyou.core.rules

import com.whereareyou.core.model.SafetyTrigger
import com.whereareyou.core.model.SafetyTriggerType
import com.whereareyou.core.model.TriggerEvent
import com.whereareyou.core.model.TrustedContact
import com.whereareyou.core.model.TrustedContactId
import com.whereareyou.core.model.phone.NormalizedPhoneNumber
import com.whereareyou.core.model.phone.PhoneNumberNormalizer

/**
 * Central rule evaluator (ANDROID_ARCHITECTURE.md section 5). Consumes normalized
 * [TriggerEvent]s and returns a [SafetyTrigger] only when authorization + rule
 * evaluation both allow starting a safety session — never any protected data.
 *
 * All dependencies are ports/pure evaluators so the whole engine can be driven from a
 * unit test without Android, per AGENTS.md "Testing priority".
 */
class TriggerEngine(
    private val contacts: TrustedContactLookup,
    private val missedCallEvaluator: MissedCallEvaluator,
    private val aggregateMissedCallEvaluator: AggregateMissedCallEvaluator? = null,
    private val smsCommandAuthorizer: SmsCommandAuthorizer,
    private val callLogDeduplicator: CallLogDeduplicator = CallLogDeduplicator(),
    private val defaultCountryCode: String = "98",
    /** True while a session caused by this contact (or any contact, if null) is already active. */
    private val hasActiveSession: (TrustedContactId?) -> Boolean = { false },
) {

    fun evaluate(event: TriggerEvent): SafetyTrigger? = when (event) {
        is TriggerEvent.MissedCallObserved -> handleMissedCall(event)
        is TriggerEvent.AnsweredCallObserved -> {
            handleAnsweredCall(event)
            null
        }
        is TriggerEvent.OutgoingCallToTrustedContact -> {
            missedCallEvaluator.onOutgoingCallToContact(event.contactId, event.occurredAt)
            null
        }
        is TriggerEvent.SmsCommandReceived -> handleSmsCommand(event)
        is TriggerEvent.SystemLowBattery -> handleLowBattery(event)
        is TriggerEvent.ManualStatusRequested -> handleManualStatus(event)
        is TriggerEvent.SessionCancelledLocally -> null // session lifecycle owns this, not rule evaluation
    }

    /** Call this when a session started by [contactId] finishes, to reset/cooldown its counters. */
    fun notifySessionCompleted(contactId: TrustedContactId?, at: java.time.Instant) {
        if (contactId != null) {
            missedCallEvaluator.onSessionCompleted(contactId, at)
        }
        aggregateMissedCallEvaluator?.onSessionCompleted(at)
    }

    private fun handleMissedCall(event: TriggerEvent.MissedCallObserved): SafetyTrigger? {
        if (!callLogDeduplicator.recordIfNew(event.callLogId)) return null

        val contact = resolveContact(event.rawNumber) ?: return null
        if (!contact.enabled) return null

        // Aggregate rule counts any enabled trusted contact regardless of that contact's
        // own missedCallTrigger flag (PRODUCT_SPEC.md section 6: "independently switchable").
        val aggregateEvaluation = aggregateMissedCallEvaluator?.onMissedCall(contact.id, event.occurredAt)

        val perContactTrigger = if (contact.capabilities.missedCallTrigger && !hasActiveSession(contact.id)) {
            when (val evaluation = missedCallEvaluator.onMissedCall(contact.id, event.occurredAt)) {
                is MissedCallEvaluation.Triggered -> SafetyTrigger(
                    type = SafetyTriggerType.REPEATED_MISSED_CALLS,
                    contactId = contact.id,
                    occurredAt = event.occurredAt,
                    evidence = "${evaluation.count} missed calls from ${contact.displayName}",
                )
                else -> null
            }
        } else null

        if (perContactTrigger != null) return perContactTrigger

        if (aggregateEvaluation is MissedCallEvaluation.Triggered && !hasActiveSession(null)) {
            return SafetyTrigger(
                type = SafetyTriggerType.AGGREGATE_MISSED_CALLS,
                contactId = contact.id,
                occurredAt = event.occurredAt,
                evidence = "${aggregateEvaluation.count} missed calls across trusted contacts",
            )
        }

        return null
    }

    private fun handleAnsweredCall(event: TriggerEvent.AnsweredCallObserved) {
        val contact = resolveContact(event.rawNumber) ?: return
        missedCallEvaluator.onAnsweredCall(contact.id, event.occurredAt)
        aggregateMissedCallEvaluator?.onAnsweredCall(event.occurredAt)
    }

    private fun handleSmsCommand(event: TriggerEvent.SmsCommandReceived): SafetyTrigger? {
        val contact = resolveContact(event.rawNumber) ?: return null
        if (!contact.enabled || !contact.capabilities.smsCommand) return null

        return when (val result = smsCommandAuthorizer.authorize(event, contact, event.occurredAt)) {
            is SmsAuthorizationResult.Authorized -> SafetyTrigger(
                type = SafetyTriggerType.SMS_COMMAND,
                contactId = contact.id,
                occurredAt = event.occurredAt,
                evidence = result.evidence,
                replayKey = result.requestId,
            )
            is SmsAuthorizationResult.Rejected -> null
        }
    }

    private fun handleLowBattery(event: TriggerEvent.SystemLowBattery): SafetyTrigger =
        SafetyTrigger(
            type = SafetyTriggerType.LOW_BATTERY,
            contactId = null,
            occurredAt = event.occurredAt,
            evidence = "system low battery at ${event.batteryPercent}%",
        )

    private fun handleManualStatus(event: TriggerEvent.ManualStatusRequested): SafetyTrigger =
        SafetyTrigger(
            type = SafetyTriggerType.MANUAL_STATUS,
            contactId = event.recipientIds.firstOrNull(),
            occurredAt = event.occurredAt,
            evidence = "manual status requested for ${event.recipientIds.size} recipient(s)",
        )

    private fun resolveContact(rawNumber: String): TrustedContact? {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber, defaultCountryCode)
        val canonical = (normalized as? NormalizedPhoneNumber.Valid)?.canonical ?: return null
        return contacts.findByCanonicalNumber(canonical)
    }
}
