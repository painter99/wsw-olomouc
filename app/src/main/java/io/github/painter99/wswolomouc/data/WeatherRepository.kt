package io.github.painter99.wswolomouc.data

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.db.MeasurementDao
import io.github.painter99.wswolomouc.db.MeasurementEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Entity <-> model mapping (kept next to the repository). */
fun StationMeasurement.toEntity(): MeasurementEntity = MeasurementEntity(
    station = station,
    temperatureC = temperatureC,
    humidityPct = humidityPct,
    pressureHpa = pressureHpa,
    windMs = windMs,
    windGustMs = windGustMs,
    windDirDeg = windDirDeg,
    rainMm = rainMm,
    rainDailyMm = rainDailyMm,
    measuredAt = measuredAtMs,
    fetchedAt = fetchedAtMs
)

fun MeasurementEntity.toModel(): StationMeasurement = StationMeasurement(
    station = station,
    temperatureC = temperatureC,
    humidityPct = humidityPct,
    pressureHpa = pressureHpa,
    windMs = windMs,
    windGustMs = windGustMs,
    windDirDeg = windDirDeg,
    rainMm = rainMm,
    rainDailyMm = rainDailyMm,
    measuredAtMs = measuredAt,
    fetchedAtMs = fetchedAt
)

/**
 * Central weather repository (M1.4): fetches both stations with fallback
 * (F1.4), enforces the per-source rate limit (F1.5) and persists every
 * successful fetch into Room (F1.3, F4.1).
 *
 * [sources] is ordered — the first entry is the primary station (F1.4).
 */
class WeatherRepository(
    private val sources: List<StationDataSource>,
    private val dao: MeasurementDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val rateLimitMs: Long = Sources.FETCH_RATE_LIMIT_MS,
    /**
     * Persistent rate-limit state (M1.7b, Pavel 24. 9. 2026) — production
     * wiring uses DataStore so the 10-min budget survives process death
     * (widget refresh must not bypass the limit after the app is closed).
     */
    private val rateLimitStore: RateLimitStore = NoopRateLimitStore
) {

    enum class Freshness {
        /** All shown values are within the staleness threshold — network or fresh cache. */
        FRESH,

        /** Data shown, but older than the staleness threshold. */
        STALE,

        /** Nothing to show at all — no network data and no cache (G6). */
        OFFLINE
    }

    data class Snapshot(
        val freshness: Freshness,
        /** Latest value per station: network result wins over cache. */
        val measurements: Map<String, StationMeasurement>,
        /**
         * Per-source outcome of the LAST refresh attempt (M1.6b-3).
         * Sources skipped by the rate limit keep their previous entry.
         */
        val sourceResults: Map<String, FetchResult> = emptyMap(),
        /**
         * Earliest time the next refresh attempt is allowed (M1.6b-3,
         * F1.5) — max lastAttempt + rate limit; null = nothing attempted yet.
         */
        val nextRefreshAllowedAtMs: Long? = null,
        /** True when THIS refresh skipped every source due to the rate limit. */
        val skippedByRateLimit: Boolean = false
    )

    private val lastAttemptAt = mutableMapOf<String, Long>()
    private var rateLimitLoaded = false

    /** Load persisted attempts once per process (M1.7b). */
    private suspend fun ensureRateLimitLoaded() {
        if (rateLimitLoaded) return
        rateLimitLoaded = true
        try {
            lastAttemptAt.putAll(rateLimitStore.loadAll())
        } catch (e: Exception) {
            // F1.4 defense in depth: limiter degrades to in-memory state
        }
    }

    /** Last fetch outcome per source (M1.6b-3); rate-limited skips keep it. */
    private val lastOutcome = mutableMapOf<String, FetchResult>()

    suspend fun refresh(): Snapshot = withContext(Dispatchers.IO) {
        val now = clock()
        ensureRateLimitLoaded()
        val fetched = mutableMapOf<String, StationMeasurement>()
        var attemptedAny = false

        for (source in sources) {
            val last = lastAttemptAt[source.id]
            if (last != null && now - last < rateLimitMs) continue // F1.5
            lastAttemptAt[source.id] = now
            try {
                rateLimitStore.put(source.id, now)
            } catch (e: Exception) {
                // F1.4 defense in depth: persistence failure must not break refresh
            }
            attemptedAny = true
            val outcome = try {
                source.fetchResult()
            } catch (e: Exception) {
                FetchResult.NetworkError(e.message ?: "exception") // F1.4 defense in depth
            }
            lastOutcome[source.id] = outcome
            if (outcome is FetchResult.Success) {
                dao.insert(outcome.measurement.toEntity())
                fetched[outcome.measurement.station] = outcome.measurement
            }
        }

        val merged = latestFromCache() + fetched

        // M1.7b (Pavel 24. 9. 2026): freshness describes the DATA being shown,
        // not whether the network answered this cycle. A skipped refresh with
        // a 2-min-old cache must be FRESH, not "Offline".
        val freshness = when {
            merged.isEmpty() -> Freshness.OFFLINE
            merged.values.all { isFresh(it, now) } -> Freshness.FRESH
            else -> Freshness.STALE
        }
        Snapshot(
            freshness = freshness,
            measurements = merged,
            sourceResults = lastOutcome.toMap(),
            nextRefreshAllowedAtMs = lastAttemptAt.values.maxOrNull()?.plus(rateLimitMs),
            skippedByRateLimit = !attemptedAny && lastAttemptAt.isNotEmpty()
        )
    }

    /** Latest persisted value per station (cache-first reads, NF3). */
    suspend fun latestFromCache(): Map<String, StationMeasurement> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, StationMeasurement>()
        for (id in sources.map { it.id }) {
            dao.latestForStation(id)?.let { result[it.station] = it.toModel() }
        }
        result
    }

    /**
     * Temperature of the latest measurement at or before [beforeEpochMs] —
     * the "past" point for the trend arrows (PRD Fáze 2). Null when the
     * station has no history that far back.
     */
    suspend fun pastTemperature(station: String, beforeEpochMs: Long): Float? =
        withContext(Dispatchers.IO) {
            dao.latestBefore(station, beforeEpochMs)?.temperatureC
        }

    private fun isFresh(m: StationMeasurement, now: Long): Boolean =
        now - m.measuredAtMs <= Sources.STALE_THRESHOLD_MIN * 60_000
}
