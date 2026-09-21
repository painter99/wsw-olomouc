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
    private val rateLimitMs: Long = Sources.FETCH_RATE_LIMIT_MS
) {

    enum class Freshness {
        /** All stations served fresh data (network fetch succeeded). */
        FRESH,

        /** Network fetch succeeded but data is older than the staleness threshold. */
        STALE,

        /** No source answered — cached values (possibly none) are served (G6). */
        OFFLINE
    }

    data class Snapshot(
        val freshness: Freshness,
        /** Latest value per station: network result wins over cache. */
        val measurements: Map<String, StationMeasurement>
    )

    private val lastAttemptAt = mutableMapOf<String, Long>()

    suspend fun refresh(): Snapshot = withContext(Dispatchers.IO) {
        val now = clock()
        val fetched = mutableMapOf<String, StationMeasurement>()

        for (source in sources) {
            val last = lastAttemptAt[source.id]
            if (last != null && now - last < rateLimitMs) continue // F1.5
            lastAttemptAt[source.id] = now
            val m = try {
                source.fetch()
            } catch (e: Exception) {
                null // F1.4: a failing source must never crash the app
            }
            if (m != null) {
                dao.insert(m.toEntity())
                fetched[m.station] = m
            }
        }

        val merged = latestFromCache() + fetched

        val freshness = when {
            merged.isEmpty() -> Freshness.OFFLINE
            fetched.isEmpty() -> Freshness.OFFLINE // nothing answered, cache only
            merged.values.all { isFresh(it, now) } -> Freshness.FRESH
            else -> Freshness.STALE
        }
        Snapshot(freshness, merged)
    }

    /** Latest persisted value per station (cache-first reads, NF3). */
    suspend fun latestFromCache(): Map<String, StationMeasurement> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, StationMeasurement>()
        for (id in sources.map { it.id }) {
            dao.latestForStation(id)?.let { result[it.station] = it.toModel() }
        }
        result
    }

    private fun isFresh(m: StationMeasurement, now: Long): Boolean =
        now - m.measuredAtMs <= Sources.STALE_THRESHOLD_MIN * 60_000
}
