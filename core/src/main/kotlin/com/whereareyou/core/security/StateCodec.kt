package com.whereareyou.core.security

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Text codec for the `exportState()` snapshots of the stateful rule/security classes
 * (missed-call counters, replay guard, rate limiters), so the app can persist them in
 * DataStore and survive process death. One line per key: `urlencoded-key<TAB>millis,millis`.
 * Malformed lines are skipped rather than failing the whole restore.
 */
object StateCodec {
    fun encode(state: Map<String, List<Long>>): String =
        state.entries.joinToString("\n") { (key, values) ->
            URLEncoder.encode(key, "UTF-8") + "\t" + values.joinToString(",")
        }

    fun decode(text: String): Map<String, List<Long>> {
        val result = linkedMapOf<String, List<Long>>()
        for (line in text.lineSequence()) {
            val parts = line.split('\t')
            if (parts.size != 2) continue
            val key = runCatching { URLDecoder.decode(parts[0], "UTF-8") }.getOrNull() ?: continue
            val numbers = if (parts[1].isEmpty()) emptyList() else parts[1].split(',').map { it.toLongOrNull() }
            if (numbers.any { it == null }) continue
            result[key] = numbers.filterNotNull()
        }
        return result
    }
}
