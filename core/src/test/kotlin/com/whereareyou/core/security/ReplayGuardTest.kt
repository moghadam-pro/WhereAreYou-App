package com.whereareyou.core.security

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplayGuardTest {

    private val base: Instant = Instant.parse("2026-08-23T12:00:00Z")

    @Test
    fun `first sighting of a key is not a replay`() {
        val guard = ReplayGuard(ttl = Duration.ofMinutes(5))
        assertTrue(guard.recordIfNew("rid-1", base))
    }

    @Test
    fun `immediate duplicate within ttl is a replay`() {
        val guard = ReplayGuard(ttl = Duration.ofMinutes(5))
        guard.recordIfNew("rid-1", base)
        assertFalse(guard.recordIfNew("rid-1", base.plusSeconds(5)))
    }

    @Test
    fun `same key after ttl expires is allowed again`() {
        val guard = ReplayGuard(ttl = Duration.ofMinutes(5))
        guard.recordIfNew("rid-1", base)
        assertTrue(guard.recordIfNew("rid-1", base.plus(Duration.ofMinutes(6))))
    }

    @Test
    fun `out-of-order delayed duplicate within ttl is still a replay`() {
        val guard = ReplayGuard(ttl = Duration.ofMinutes(5))
        guard.recordIfNew("rid-1", base)
        assertFalse(guard.recordIfNew("rid-1", base.minusSeconds(30)))
    }

    @Test
    fun `different keys do not interfere with each other`() {
        val guard = ReplayGuard(ttl = Duration.ofMinutes(5))
        guard.recordIfNew("rid-1", base)
        assertTrue(guard.recordIfNew("rid-2", base))
    }
}
