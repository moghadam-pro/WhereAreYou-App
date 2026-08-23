package com.whereareyou.core.rules

import com.whereareyou.core.model.TriggerEvent
import com.whereareyou.core.model.TrustedContact
import java.time.Instant

/** What an SMS command turned out to request, once authorized. */
enum class SmsCommandKind { STATUS_REQUEST, EMERGENCY_CALLBACK_REQUEST }

sealed interface SmsAuthorizationResult {
    data class Authorized(
        val kind: SmsCommandKind,
        val requestId: String,
        val evidence: String,
    ) : SmsAuthorizationResult

    /** [reason] is for local audit logging only — never echoed back to the sender. */
    data class Rejected(val reason: String) : SmsAuthorizationResult
}

/**
 * Port the trigger engine uses to authorize an inbound SMS command
 * (ANDROID_ARCHITECTURE.md section 5 "SMS command rule"). Implemented by
 * [com.whereareyou.core.security.SmsCommandAuthenticator]; declared in `rules` because that
 * is the consuming layer, keeping the dependency direction rules -> (port only), security ->
 * (implementation), both within the same module but decoupled call sites.
 */
interface SmsCommandAuthorizer {
    fun authorize(event: TriggerEvent.SmsCommandReceived, contact: TrustedContact, at: Instant): SmsAuthorizationResult
}
