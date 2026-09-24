package io.github.painter99.wswolouc.data

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
internal object NoopRateLimitStore : RateLimitStore {
    override suspend fun loadAll(): Map<String, Long> = emptyMap()
    override suspend fun put(sourceId: String, lastAttemptAtMs: Long) = Unit
}