package com.whereareyou.core.model

import java.time.Instant

/**
 * A normalized event handed from a platform event adapter (BroadcastReceiver, etc.)
 * to the domain layer. See ANDROID_ARCHITECTURE.md section 3 "TriggerEvent".
 *
 * Event adapters must do the minimal work required to produce one of these and must
 * not evaluate rules themselves — that belongs to [com.whereareyou.core.rules.TriggerEngine].
 */
sealed interface TriggerEvent {
    val occurredAt: Instant

    /** A call from [rawNumber] rang without being answered. */
    data class MissedCallObserved(
        val rawNumber: String,
        /** Call-log row identity used for reconciliation/dedup, see ANDROID_ARCHITECTURE.md section 4. */
        val callLogId: String,
        override val occurredAt: Instant,
    ) : TriggerEvent

    /** A call between the protected device and [rawNumber] was answered (either direction). */
    data class AnsweredCallObserved(
        val rawNumber: String,
        override val occurredAt: Instant,
    ) : TriggerEvent

    /** The protected user placed an outgoing call to a trusted contact. */
    data class OutgoingCallToTrustedContact(
        val contactId: TrustedContactId,
        override val occurredAt: Instant,
    ) : TriggerEvent

    /** Raw inbound SMS content from [rawNumber], not yet authorized or parsed. */
    data class SmsCommandReceived(
        val rawNumber: String,
        val body: String,
        override val occurredAt: Instant,
    ) : TriggerEvent

    /** Android's system low-battery broadcast fired. */
    data class SystemLowBattery(
        val batteryPercent: Int,
        override val occurredAt: Instant,
    ) : TriggerEvent

    /** The protected user tapped "I'm OK" / manual status send in the Protect UI. */
    data class ManualStatusRequested(
        val recipientIds: List<TrustedContactId>,
        override val occurredAt: Instant,
    ) : TriggerEvent

    /** The protected user cancelled an active session from the Protect UI. */
    data class SessionCancelledLocally(
        val sessionId: String,
        override val occurredAt: Instant,
    ) : TriggerEvent
}
