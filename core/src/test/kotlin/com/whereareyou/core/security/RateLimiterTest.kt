package com.whereareyou.core.security

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimiterTest {

    private val base: Instant = Instant.parse("2026-08-23T12:00:00Z")

    @Test
    fun `allows up to the configured max within the window`() {
        val limiter = RateLimiter(maxEvents = 2, window = Duration.ofMinutes(60))
        assertTrue(limiter.tryAcquire("contact-a", base))
        assertTrue(limiter.tryAcquire("contact-a", base.plus(Duration.ofMinutes(1))))
    }

    @Test
    fun `rejects once the limit is exceeded within the window`() {
        val limiter = RateLimiter(maxEvents = 2, window = Duration.ofMinutes(60))
        limiter.tryAcquire("contact-a", base)
        limiter.tryAcquire("contact-a", base.plus(Duration.ofMinutes(1)))
        assertFalse(limiter.tryAcquire("contact-a", base.plus(Duration.ofMinutes(2))))
    }

    @Test
    fun `old events roll off the window and free up capacity`() {
        val limiter = RateLimiter(maxEvents = 1, window = Duration.ofMinutes(60))
        limiter.tryAcquire("contact-a", base)
        assertFalse(limiter.tryAcquire("contact-a", base.plus(Duration.ofMinutes(30))))
        assertTrue(limiter.tryAcquire("contact-a", base.plus(Duration.ofMinutes(61))))
    }

    @Test
    fun `different keys have independent limits`() {
        val limiter = RateLimiter(maxEvents = 1, window = Duration.ofMinutes(60))
        assertTrue(limiter.tryAcquire("contact-a", base))
        assertTrue(limiter.tryAcquire("contact-b", base))
    }
}
