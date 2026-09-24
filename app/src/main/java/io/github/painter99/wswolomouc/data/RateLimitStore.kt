package io.github.painter99.wswolomouc.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.rateLimitDataStore by preferencesDataStore(name = "wsw_rate_limit")

/**
 * Persistent per-source rate-limit state (F1.5, M1.7b — Pavel 24. 9. 2026).
 *
 * The rate limit must survive process death: after the user closes the app,
 * the widget refresh button must not immediately re-fetch both sources
 * (ethics — the station consent assumes a polite fetch frequency).
 */
interface RateLimitStore {

    /** Last attempt time per source id; empty when nothing persisted. */
    suspend fun loadAll(): Map<String, Long>

    /** Persist the last attempt time for [sourceId]. */
    suspend fun put(sourceId: String, lastAttemptAtMs: Long)
}

/** Default in-memory-only store — preserves the pre-M1.7b behavior. */
object NoopRateLimitStore : RateLimitStore {
    override suspend fun loadAll(): Map<String, Long> = emptyMap()
    override suspend fun put(sourceId: String, lastAttemptAtMs: Long) = Unit
}

/**
 * DataStore-backed [RateLimitStore] (M1.7b, Pavel 24. 9. 2026): the 10-min
 * per-source fetch budget survives process death, so the widget refresh
 * button cannot bypass the limit right after the app is closed.
 */
class DataStoreRateLimitStore(private val context: Context) : RateLimitStore {

    override suspend fun loadAll(): Map<String, Long> = try {
        context.rateLimitDataStore.data.first().asMap()
            // M1.8d (Pavel 24. 9. 2026): strip the storage prefix so the map
            // is keyed by the SOURCE id the repository looks up. Before this
            // fix the loaded keys kept "rl_", the lookups missed, and the
            // 10-min limit silently reset on every process death.
            .mapKeys { entry -> storeKeyToSource(entry.key.name) }
            .mapValues { entry -> entry.value as? Long ?: 0L }
            .filterValues { value -> value > 0L }
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

    internal companion object {
        const val KEY_PREFIX = "rl_"

        /**
         * M1.8d: storage key -> source id. Pure function, unit-tested in
         * RateLimitStoreKeysTest (Prove-It for the process-death bypass).
         */
        fun storeKeyToSource(name: String): String = name.removePrefix(KEY_PREFIX)
    }
}