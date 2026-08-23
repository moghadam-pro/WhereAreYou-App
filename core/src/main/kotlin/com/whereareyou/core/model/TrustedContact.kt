package com.whereareyou.core.model

/**
 * Opaque local identifier for a [TrustedContact]. Generated locally
 * (see AGENTS.md "Trusted contacts") — never derived from a remote service.
 */
@JvmInline
value class TrustedContactId(val value: String)

/** How an Emergency Callback request from this contact is handled. Defaults to [OFF]. */
enum class EmergencyCallbackMode {
    OFF,
    ASK_FIRST,
    COUNTDOWN_AUTO_CALL,
}

/**
 * Independent per-contact permission flags (PRODUCT_SPEC.md section 5,
 * SECURITY_PRIVACY.md section 4). A contact is never granted a capability
 * implicitly by being trusted; each flag is explicit and revocable.
 */
data class ContactCapabilities(
    val missedCallTrigger: Boolean,
    val smsCommand: Boolean,
    val receiveLowBatteryAlert: Boolean,
    val manualStatusRecipient: Boolean,
    val emergencyCallback: EmergencyCallbackMode,
) {
    companion object {
        /** Recommended defaults for a newly added contact (SECURITY_PRIVACY.md section 4). */
        fun default() = ContactCapabilities(
            missedCallTrigger = true,
            smsCommand = true,
            receiveLowBatteryAlert = false,
            manualStatusRecipient = true,
            emergencyCallback = EmergencyCallbackMode.OFF,
        )
    }
}

/**
 * A family member/caregiver explicitly added by the protected user.
 *
 * [canonicalPhoneNumber] is the normalized E.164-ish comparison value produced by
 * [com.whereareyou.core.model.phone.PhoneNumberNormalizer]; [displayPhoneNumber] preserves
 * what the user actually typed for the UI (ANDROID_ARCHITECTURE.md section 13).
 */
data class TrustedContact(
    val id: TrustedContactId,
    val displayName: String,
    val canonicalPhoneNumber: String,
    val displayPhoneNumber: String,
    val enabled: Boolean = true,
    val capabilities: ContactCapabilities = ContactCapabilities.default(),
    val commandSecret: String? = null,
) {
    /** A disabled or unset-secret contact still exists but cannot authorize anything. */
    fun canUse(capability: (ContactCapabilities) -> Boolean): Boolean =
        enabled && capability(capabilities)
}
