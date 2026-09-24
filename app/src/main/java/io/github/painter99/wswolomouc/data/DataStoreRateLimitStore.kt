package io.github.painter99.wswolouc.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.rateLimitDataStore by preferencesDataStore(name = "wsw_rate_limit")

/**
 * DataStore-backed [RateLimitStore] (M1.7b, Pavel 24. 9. 2026): the 10-min
 * per-source fetch budget survives process death, so the widget refresh
 * button cannot bypass the limit right after the app is closed.
 */
class DataStoreRateLimitStore(private val context: Context) : RateLimitStore {

    override suspend fun loadAll(): Map<String, Long> = try {
        context.rateLimitDataStore.data.first().asMap()
            .mapKeys { it.key.name }
            .mapValues { (_, v) -> v as? Long ?: 0L }
            .filterValues { it > 0L }
    } catch (e: Exception) {
        emptyMap() // F1.4 defense in depth: limiter degrades to in-memory
    }

    override suspend fun put(sourceId: String, lastAttemptAtMs: Long) {
        try {
            context.rateLimitDataStore.edit { prefs ->
                prefs[longPreferencesKey(KEY_PREFIX + sourceId)] = lastAttemptAtMs
            }
        } catch (e: Exception) {
            // F1.4 defense in depth: keep going with the in-memory state
        }
    }

    private companion object {
        const val KEY_PREFIX = "rl_"
    }
}