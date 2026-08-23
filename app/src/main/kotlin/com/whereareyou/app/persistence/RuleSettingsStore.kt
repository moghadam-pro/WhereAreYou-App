package com.whereareyou.app.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whereareyou.core.rules.MissedCallRuleConfig
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "whereareyou_settings")

/**
 * Small scalar configuration — the "configuration, not hard-coded product policy" case
 * AGENTS.md "Trigger defaults for development" calls out, and exactly what DataStore is
 * for per ANDROID_ARCHITECTURE.md section 12 ("trigger thresholds/windows").
 */
class RuleSettingsStore(private val context: Context) {

    private object Keys {
        val THRESHOLD = intPreferencesKey("missed_call_threshold")
        val WINDOW_MINUTES = intPreferencesKey("missed_call_window_minutes")
        val COOLDOWN_MINUTES = intPreferencesKey("missed_call_cooldown_minutes")
    }

    val missedCallRuleConfig: Flow<MissedCallRuleConfig> = context.settingsDataStore.data.map { prefs ->
        MissedCallRuleConfig(
            threshold = prefs[Keys.THRESHOLD] ?: DEFAULT.threshold,
            window = Duration.ofMinutes((prefs[Keys.WINDOW_MINUTES] ?: DEFAULT.window.toMinutes().toInt()).toLong()),
            cooldown = Duration.ofMinutes((prefs[Keys.COOLDOWN_MINUTES] ?: DEFAULT.cooldown.toMinutes().toInt()).toLong()),
        )
    }

    suspend fun update(config: MissedCallRuleConfig) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THRESHOLD] = config.threshold
            prefs[Keys.WINDOW_MINUTES] = config.window.toMinutes().toInt()
            prefs[Keys.COOLDOWN_MINUTES] = config.cooldown.toMinutes().toInt()
        }
    }

    companion object {
        val DEFAULT = MissedCallRuleConfig()
    }
}
