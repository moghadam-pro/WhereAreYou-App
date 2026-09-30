package com.whereareyou.core.security

import com.whereareyou.core.model.TrustedContactId
import com.whereareyou.core.rules.MissedCallEvaluation
import com.whereareyou.core.rules.MissedCallEvaluator
import com.whereareyou.core.rules.MissedCallRuleConfig
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class StateCodecTest {
    private val base: Instant = Instant.parse("2026-08-23T12:00:00Z")

    @Test
    fun `codec round trips keys containing separators`() {
        val state = mapOf("id:with|odd chars\t" to listOf(1L, 2L), "empty" to emptyList())
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
    }

    @Test
    fun `malformed lines are skipped`() {
        assertEquals(mapOf("ok" to listOf(5L)), StateCodec.decode("garbage\nok\t5\nbad\tx,y"))
    }

    @Test
    fun `missed call counters survive a simulated process restart`() {
        val id = TrustedContactId("a")
        val cfg = MissedCallRuleConfig()
        val before = MissedCallEvaluator(cfg)
        before.onMissedCall(id, base)
        before.onMissedCall(id, base.plus(Duration.ofMinutes(10)))

        val after = MissedCallEvaluator(cfg)
        after.restoreState(StateCodec.decode(StateCodec.encode(before.exportState())))
        assertIs<MissedCallEvaluation.Triggered>(after.onMissedCall(id, base.plus(Duration.ofMinutes(20))))
    }

    @Test
    fun `replay guard survives restart and drops expired entries`() {
        val ttl = Duration.ofMinutes(5)
        val before = ReplayGuard(ttl)
        before.recordIfNew("k", base)
        val after = ReplayGuard(ttl)
        after.restoreState(StateCodec.decode(StateCodec.encode(before.exportState())), base.plus(Duration.ofMinutes(1)))
        assertFalse(after.recordIfNew("k", base.plus(Duration.ofMinutes(2))))

        val expired = ReplayGuard(ttl)
        expired.restoreState(before.exportState(), base.plus(Duration.ofMinutes(30)))
        assertEquals(true, expired.recordIfNew("k", base.plus(Duration.ofMinutes(30))))
    }
}
