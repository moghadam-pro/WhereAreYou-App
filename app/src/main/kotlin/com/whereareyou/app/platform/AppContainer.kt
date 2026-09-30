package com.whereareyou.app.platform

import android.content.Context
import androidx.room.Room
import com.whereareyou.app.contacts.TrustedContactRepository
import com.whereareyou.app.persistence.RuleSettingsStore
import com.whereareyou.app.persistence.TriggerStateStore
import com.whereareyou.app.persistence.WhereAreYouDatabase
import com.whereareyou.core.rules.MissedCallEvaluator
import com.whereareyou.core.rules.TriggerEngine
import com.whereareyou.core.security.RateLimiter
import com.whereareyou.core.security.ReplayGuard
import com.whereareyou.core.security.SmsCommandAuthenticator
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency container (no DI framework) — small enough for Phase 1 that a
 * framework like Hilt would add build-graph risk without paying for itself yet.
 *
 * [triggerEngine] is fully wired against real persistence, but nothing in the `app`
 * module feeds it events yet: no BroadcastReceiver for SMS/calls/battery exists in this
 * build. That is intentional — AGENTS.md "Before real SMS, call-log, telecom, or
 * background-location integration, make sure the domain layer and tests are complete" and
 * the "First coding milestone" list platform receivers as the *last* step, after the
 * domain layer (done, see :core) and this CRUD/persistence scaffold (this module).
 */
interface AppContainer {
    val applicationScope: CoroutineScope
    val database: WhereAreYouDatabase
    val ruleSettingsStore: RuleSettingsStore
    val contactRepository: TrustedContactRepository
    val triggerEngine: TriggerEngine

    /** Restores persisted rule state; call once, before the first event is evaluated. */
    suspend fun restoreTriggerState()

    /** Persists rule state; call after every evaluated event (cheap: timestamps only). */
    suspend fun persistTriggerState()
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob())

    override val database: WhereAreYouDatabase = Room.databaseBuilder(
        context.applicationContext,
        WhereAreYouDatabase::class.java,
        WhereAreYouDatabase.DATABASE_NAME,
    ).build()

    override val ruleSettingsStore = RuleSettingsStore(context.applicationContext)

    override val contactRepository = TrustedContactRepository(
        dao = database.trustedContactDao(),
        externalScope = applicationScope,
    )

    // Built once, from the default rule config, and held as a stable singleton: the
    // MissedCallEvaluator and CallLogDeduplicator it wraps are stateful (in-memory
    // per-contact windows/dedup sets), so re-constructing TriggerEngine on every access
    // would silently discard that history. Live-reloading rule config from
    // RuleSettingsStore without losing in-flight window state is deferred until a
    // platform event adapter actually calls triggerEngine.evaluate(...) (Phase 1C/1D) —
    // nothing in this build does yet.
    private val triggerStateStore = TriggerStateStore(context.applicationContext)
    private val missedCallEvaluator = MissedCallEvaluator(RuleSettingsStore.DEFAULT)
    private val replayGuard = ReplayGuard()
    private val commandRateLimiter = RateLimiter(maxEvents = 5, window = Duration.ofHours(1))
    private val failureLimiter = RateLimiter(maxEvents = 10, window = Duration.ofHours(1))

    override val triggerEngine: TriggerEngine = TriggerEngine(
        contacts = contactRepository.contactLookup,
        missedCallEvaluator = missedCallEvaluator,
        smsCommandAuthorizer = SmsCommandAuthenticator(replayGuard, commandRateLimiter, failureLimiter),
    )

    override suspend fun restoreTriggerState() {
        val now = Instant.now()
        missedCallEvaluator.restoreState(triggerStateStore.load("missed_calls"))
        replayGuard.restoreState(triggerStateStore.load("replay"), now)
        commandRateLimiter.restoreState(triggerStateStore.load("command_rate"), now)
        failureLimiter.restoreState(triggerStateStore.load("failed_codes"), now)
    }

    override suspend fun persistTriggerState() {
        triggerStateStore.save("missed_calls", missedCallEvaluator.exportState())
        triggerStateStore.save("replay", replayGuard.exportState())
        triggerStateStore.save("command_rate", commandRateLimiter.exportState())
        triggerStateStore.save("failed_codes", failureLimiter.exportState())
    }
}
