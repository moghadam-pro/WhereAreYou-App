package com.whereareyou.core.rules

import com.whereareyou.core.model.TrustedContactId
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertIs

class AggregateMissedCallEvaluatorTest {

    private val contactA = TrustedContactId("a")
    private val contactB = TrustedContactId("b")
    private val base: Instant = Instant.parse("2026-08-23T12:00:00Z")

    @Test
    fun `disabled config never triggers`() {
        val eval = AggregateMissedCallEvaluator(AggregateMissedCallRuleConfig(enabled = false, threshold = 2))
        eval.onMissedCall(contactA, base)
        val result = eval.onMissedCall(contactB, base.plus(Duration.ofMinutes(1)))
        assertIs<MissedCallEvaluation.NotTriggered>(result)
    }

    @Test
    fun `enabled config triggers once threshold reached across multiple contacts`() {
        val eval = AggregateMissedCallEvaluator(AggregateMissedCallRuleConfig(enabled = true, threshold = 3))
        eval.onMissedCall(contactA, base)
        eval.onMissedCall(contactB, base.plus(Duration.ofMinutes(1)))
        val result = eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(2)))
        assertIs<MissedCallEvaluation.Triggered>(result)
    }

    @Test
    fun `answered call clears the shared counter`() {
        val eval = AggregateMissedCallEvaluator(AggregateMissedCallRuleConfig(enabled = true, threshold = 2))
        eval.onMissedCall(contactA, base)
        eval.onAnsweredCall(base.plus(Duration.ofMinutes(1)))
        val result = eval.onMissedCall(contactB, base.plus(Duration.ofMinutes(2)))
        assertIs<MissedCallEvaluation.NotTriggered>(result)
    }

    @Test
    fun `session completion starts cooldown`() {
        val eval = AggregateMissedCallEvaluator(
            AggregateMissedCallRuleConfig(enabled = true, threshold = 2, cooldown = Duration.ofMinutes(30)),
        )
        eval.onMissedCall(contactA, base)
        eval.onMissedCall(contactB, base.plus(Duration.ofMinutes(1)))
        eval.onSessionCompleted(base.plus(Duration.ofMinutes(2)))

        val duringCooldown = eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(10)))
        assertIs<MissedCallEvaluation.InCooldown>(duringCooldown)
    }
}
