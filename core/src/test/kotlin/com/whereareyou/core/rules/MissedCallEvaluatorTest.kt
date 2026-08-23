package com.whereareyou.core.rules

import com.whereareyou.core.model.TrustedContactId
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MissedCallEvaluatorTest {

    private val contactA = TrustedContactId("a")
    private val contactB = TrustedContactId("b")
    private val base: Instant = Instant.parse("2026-08-23T12:00:00Z")

    private fun evaluator(
        threshold: Int = 3,
        window: Duration = Duration.ofMinutes(60),
        cooldown: Duration = Duration.ofMinutes(60),
    ) = MissedCallEvaluator(MissedCallRuleConfig(threshold, window, cooldown))

    @Test
    fun `3 missed in 60 minutes triggers`() {
        val eval = evaluator()
        eval.onMissedCall(contactA, base)
        eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(30)))
        val result = eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(60)))
        assertIs<MissedCallEvaluation.Triggered>(result)
    }

    @Test
    fun `2 missed in 60 minutes does not trigger`() {
        val eval = evaluator()
        eval.onMissedCall(contactA, base)
        val result = eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(30)))
        assertIs<MissedCallEvaluation.NotTriggered>(result)
    }

    @Test
    fun `3 missed spanning 61 minutes does not trigger`() {
        val eval = evaluator()
        eval.onMissedCall(contactA, base)
        eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(30)))
        val result = eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(61)))
        assertIs<MissedCallEvaluation.NotTriggered>(result)
    }

    @Test
    fun `non-consecutive calls from the same contact still count`() {
        val eval = evaluator()
        eval.onMissedCall(contactA, base)
        eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(16)))
        val result = eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(48)))
        assertIs<MissedCallEvaluation.Triggered>(result)
    }

    @Test
    fun `calls from two different contacts use independent counters`() {
        val eval = evaluator()
        eval.onMissedCall(contactA, base)
        eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(10)))
        val bResult = eval.onMissedCall(contactB, base.plus(Duration.ofMinutes(11)))
        assertIs<MissedCallEvaluation.NotTriggered>(bResult)
    }

    @Test
    fun `answered call in between resets the window`() {
        val eval = evaluator()
        eval.onMissedCall(contactA, base)
        eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(10)))
        eval.onAnsweredCall(contactA, base.plus(Duration.ofMinutes(15)))
        val result = eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(20)))
        assertIs<MissedCallEvaluation.NotTriggered>(result)
        assertTrue((result as MissedCallEvaluation.NotTriggered).count == 1)
    }

    @Test
    fun `outgoing callback resets the window`() {
        val eval = evaluator()
        eval.onMissedCall(contactA, base)
        eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(10)))
        eval.onOutgoingCallToContact(contactA, base.plus(Duration.ofMinutes(12)))
        val result = eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(20)))
        assertIs<MissedCallEvaluation.NotTriggered>(result)
    }

    @Test
    fun `session completion starts a cooldown that suppresses new triggers`() {
        val eval = evaluator(cooldown = Duration.ofMinutes(30))
        eval.onMissedCall(contactA, base)
        eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(1)))
        val firstTrigger = eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(2)))
        assertIs<MissedCallEvaluation.Triggered>(firstTrigger)

        eval.onSessionCompleted(contactA, base.plus(Duration.ofMinutes(3)))

        val duringCooldown = eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(10)))
        assertIs<MissedCallEvaluation.InCooldown>(duringCooldown)
    }

    @Test
    fun `new session is authorized again after cooldown expires`() {
        val eval = evaluator(cooldown = Duration.ofMinutes(30))
        eval.onMissedCall(contactA, base)
        eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(1)))
        eval.onMissedCall(contactA, base.plus(Duration.ofMinutes(2)))
        eval.onSessionCompleted(contactA, base.plus(Duration.ofMinutes(3)))

        val afterCooldown = base.plus(Duration.ofMinutes(3)).plus(Duration.ofMinutes(31))
        eval.onMissedCall(contactA, afterCooldown)
        eval.onMissedCall(contactA, afterCooldown.plus(Duration.ofMinutes(1)))
        val result = eval.onMissedCall(contactA, afterCooldown.plus(Duration.ofMinutes(2)))
        assertIs<MissedCallEvaluation.Triggered>(result)
    }
}
