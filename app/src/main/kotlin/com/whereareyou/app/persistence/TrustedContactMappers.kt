package com.whereareyou.app.persistence

import com.whereareyou.core.model.ContactCapabilities
import com.whereareyou.core.model.EmergencyCallbackMode
import com.whereareyou.core.model.TrustedContact
import com.whereareyou.core.model.TrustedContactId

fun TrustedContactEntity.toDomain(): TrustedContact = TrustedContact(
    id = TrustedContactId(id),
    displayName = displayName,
    canonicalPhoneNumber = canonicalPhoneNumber,
    displayPhoneNumber = displayPhoneNumber,
    enabled = enabled,
    capabilities = ContactCapabilities(
        missedCallTrigger = missedCallTrigger,
        smsCommand = smsCommand,
        receiveLowBatteryAlert = receiveLowBatteryAlert,
        manualStatusRecipient = manualStatusRecipient,
        emergencyCallback = runCatching { EmergencyCallbackMode.valueOf(emergencyCallbackMode) }
            .getOrDefault(EmergencyCallbackMode.OFF),
    ),
    commandSecret = commandSecret,
)

fun TrustedContact.toEntity(): TrustedContactEntity = TrustedContactEntity(
    id = id.value,
    displayName = displayName,
    canonicalPhoneNumber = canonicalPhoneNumber,
    displayPhoneNumber = displayPhoneNumber,
    enabled = enabled,
    missedCallTrigger = capabilities.missedCallTrigger,
    smsCommand = capabilities.smsCommand,
    receiveLowBatteryAlert = capabilities.receiveLowBatteryAlert,
    manualStatusRecipient = capabilities.manualStatusRecipient,
    emergencyCallbackMode = capabilities.emergencyCallback.name,
    commandSecret = commandSecret,
)
