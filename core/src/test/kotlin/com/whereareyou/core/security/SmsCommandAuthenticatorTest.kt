package com.whereareyou.core.security

import com.whereareyou.core.model.ContactCapabilities
import com.whereareyou.core.model.EmergencyCallbackMode
import com.whereareyou.core.model.TriggerEvent
import com.whereareyou.core.model.TrustedContact
import com.whereareyou.core.model.TrustedContactId
import com.whereareyou.core.rules.SmsAuthorizationResult
import com.whereareyou.core.rules.SmsCommandKind
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SmsCommandAuthenticatorTest {

    private val base: Instant = Instant.parse("2026-08-23T12:00:00Z")

    private fun contact(secret: String? = "7314", smsCommand: Boolean = true, enabled: Boolean = true) = TrustedContact(
        id = TrustedContactId("a"),
        displayName = "Sayid",
        canonicalPhoneNumber = "+989121234567",
        displayPhoneNumber = "0912 123 4567",
        enabled = enabled,
        capabilities = ContactCapabilities(
            missedCallTrigger = true,
            smsCommand = smsCommand,
            receiveLowBatteryAlert = false,
            manualStatusRecipient = true,
            emergencyCallback = EmergencyCallbackMode.OFF,
        ),
        commandSecret = secret,
    )

    private fun event(body: String, at: Instant = base) =
        TriggerEvent.SmsCommandReceived("+989121234567", body, at)

    @Test
    fun `correct phrase and auth code is accepted`() {
        val authenticator = SmsCommandAuthenticator()
        val result = authenticator.authorize(event("وضعیت 7314"), contact(), base)
        val authorized = assertIs<SmsAuthorizationResult.Authorized>(result)
        assertEquals(SmsCommandKind.STATUS_REQUEST, authorized.kind)
    }

    @Test
    fun `wrong auth code is rejected`() {
        val authenticator = SmsCommandAuthenticator()
        val result = authenticator.authorize(event("وضعیت 0000"), contact(), base)
        assertIs<SmsAuthorizationResult.Rejected>(result)
    }

    @Test
    fun `capability disabled is rejected`() {
        val authenticator = SmsCommandAuthenticator()
        val result = authenticator.authorize(event("وضعیت 7314"), contact(smsCommand = false), base)
        assertIs<SmsAuthorizationResult.Rejected>(result)
    }

    @Test
    fun `disabled contact is rejected`() {
        val authenticator = SmsCommandAuthenticator()
        val result = authenticator.authorize(event("وضعیت 7314"), contact(enabled = false), base)
        assertIs<SmsAuthorizationResult.Rejected>(result)
    }

    @Test
    fun `no shared secret configured is rejected`() {
        val authenticator = SmsCommandAuthenticator()
        val result = authenticator.authorize(event("وضعیت 7314"), contact(secret = null), base)
        assertIs<SmsAuthorizationResult.Rejected>(result)
    }

    @Test
    fun `malformed command is rejected`() {
        val authenticator = SmsCommandAuthenticator()
        val result = authenticator.authorize(event("hello there"), contact(), base)
        assertIs<SmsAuthorizationResult.Rejected>(result)
    }

    @Test
    fun `duplicate request within the replay window is rejected`() {
        val authenticator = SmsCommandAuthenticator()
        val c = contact()
        val first = authenticator.authorize(event("وضعیت 7314"), c, base)
        assertIs<SmsAuthorizationResult.Authorized>(first)

        val duplicate = authenticator.authorize(event("وضعیت 7314", base.plusSeconds(2)), c, base.plusSeconds(2))
        assertIs<SmsAuthorizationResult.Rejected>(duplicate)
    }

    @Test
    fun `same command is accepted again after the replay window passes`() {
        val authenticator = SmsCommandAuthenticator(replayGuard = ReplayGuard(ttl = Duration.ofMinutes(5)))
        val c = contact()
        authenticator.authorize(event("وضعیت 7314"), c, base)

        val later = base.plus(Duration.ofMinutes(6))
        val result = authenticator.authorize(event("وضعیت 7314", later), c, later)
        assertIs<SmsAuthorizationResult.Authorized>(result)
    }

    @Test
    fun `persian digit auth code matches an ascii-stored secret`() {
        val authenticator = SmsCommandAuthenticator()
        val result = authenticator.authorize(event("وضعیت ۷۳۱۴"), contact(), base)
        assertIs<SmsAuthorizationResult.Authorized>(result)
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        val authenticator = SmsCommandAuthenticator()
        val result = authenticator.authorize(event("  وضعیت 7314  "), contact(), base)
        assertIs<SmsAuthorizationResult.Authorized>(result)
    }

    @Test
    fun `rate limit blocks a burst beyond the configured cap`() {
        val authenticator = SmsCommandAuthenticator(
            rateLimiter = RateLimiter(maxEvents = 1, window = Duration.ofHours(1)),
            replayGuard = ReplayGuard(ttl = Duration.ofSeconds(1)),
        )
        val c = contact()
        val first = authenticator.authorize(event("وضعیت 7314", base), c, base)
        assertIs<SmsAuthorizationResult.Authorized>(first)

        val second = authenticator.authorize(event("تماس 7314", base.plusSeconds(2)), c, base.plusSeconds(2))
        assertIs<SmsAuthorizationResult.Rejected>(second)
    }

    @Test
    fun `compact machine command form is accepted with correct auth`() {
        val authenticator = SmsCommandAuthenticator()
        val result = authenticator.authorize(event("ک1|1|4821|7314"), contact(), base)
        val authorized = assertIs<SmsAuthorizationResult.Authorized>(result)
        assertEquals(SmsCommandKind.STATUS_REQUEST, authorized.kind)
    }

    @Test
    fun `compact emergency callback command maps to the right kind`() {
        val authenticator = SmsCommandAuthenticator()
        val result = authenticator.authorize(event("ک1|2|9001|7314"), contact(), base)
        val authorized = assertIs<SmsAuthorizationResult.Authorized>(result)
        assertEquals(SmsCommandKind.EMERGENCY_CALLBACK_REQUEST, authorized.kind)
    }
}
