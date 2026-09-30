package com.whereareyou.app.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whereareyou.core.security.StateCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.triggerStateDataStore by preferencesDataStore(name = "whereareyou_trigger_state")

/**
 * Durable copy of the in-memory rule state (missed-call counters, replay guard, rate
 * limiters). Android can kill the process between two broadcasts, so without this a 3rd
 * missed call would start counting from zero and a replayed SMS command would be accepted
 * again. Timestamps only — no message bodies, numbers or secrets. Local only, never backed up.
 */
class TriggerStateStore(private val context: Context) {

    suspend fun load(name: String): Map<String, List<Long>> =
        context.triggerStateDataStore.data
            .map { it[stringPreferencesKey(name)].orEmpty() }
            .first()
            .let(StateCodec::decode)

    suspend fun save(name: String, state: Map<String, List<Long>>) {
        context.triggerStateDataStore.edit { it[stringPreferencesKey(name)] = StateCodec.encode(state) }
    }
}
