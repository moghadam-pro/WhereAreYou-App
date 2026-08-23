package com.whereareyou.app.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for a trusted contact. Structured/queryable local data — this is the case
 * ANDROID_ARCHITECTURE.md section 12 calls out Room for ("trusted contacts if richer
 * structured data is useful"), as opposed to DataStore which holds small scalar settings.
 */
@Entity(tableName = "trusted_contacts")
data class TrustedContactEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val canonicalPhoneNumber: String,
    val displayPhoneNumber: String,
    val enabled: Boolean,
    val missedCallTrigger: Boolean,
    val smsCommand: Boolean,
    val receiveLowBatteryAlert: Boolean,
    val manualStatusRecipient: Boolean,
    /** Stored as its enum name; see [com.whereareyou.core.model.EmergencyCallbackMode]. */
    val emergencyCallbackMode: String,
    val commandSecret: String?,
)
