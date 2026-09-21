package io.github.painter99.wswolomouc.data

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.db.MeasurementDao
import io.github.painter99.wswolomouc.db.MeasurementEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repository logic tests (M1.4) with a fake DAO and fake data sources —
 * pure JVM, no Android runtime, no network (TDD: fixture/fake first).
 *
 * Covers PRD F1.3 (save values + timestamps), F1.4 (fallback, offline),
 * F1.5 (rate limit 1 request / source / 10 min).
 */
class WeatherRepositoryTest {

    // --- fakes --------------------------------------------------------------

    private class FakeDao : MeasurementDao {
        val rows = mutableListOf<MeasurementEntity>()
        override suspend fun insert(m: MeasurementEntity): Long { rows.add(m); return rows.size.toLong() }
        override suspend fun latestForStation(station: String): MeasurementEntity? =
            rows.filter { it.station == station }.maxByOrNull { it.measuredAt }
        override suspend fun since(fromEpochMs: Long): List<MeasurementEntity> =
            rows.filter { it.measuredAt >= fromEpochMs }.sortedBy { it.measuredAt }
        override suspend fun deleteFetchedBefore(beforeEpochMs: Long): Int {
            val toRemove = rows.filter { it.fetchedAt < beforeEpochMs }
            rows.removeAll(toRemove)
            return toRemove.size
        }
    }

    private class FakeSource(
        override val id: String,
        private var result: StationMeasurement?
    ) : StationDataSource {
        var fetchCount = 0
        override suspend fun fetch(): StationMeasurement? {
            fetchCount++
            return result
        }
    }

    private fun measurement(
        station: String,
        measuredAtMs: Long,
        temp: Float = 15f
    ) = StationMeasurement(
        station = station, temperatureC = temp, humidityPct = 60, pressureHpa = 1013f,
        windMs = 2f, windGustMs = 5f, windDirDeg = 200, rainMm = 0f,
        measuredAtMs = measuredAtMs, fetchedAtMs = measuredAtMs
    )

    // --- constants ----------------------------------------------------------

    private val now = 1_758_000_000_000L
    private val tenMin = Sources.FETCH_RATE_LIMIT_MS

    // --- tests ---------------------------------------------------------------

    @Test
    fun refresh_primaryOk_fallbackNotCalled_resultSavedToDao() = runTest {
        val dao = FakeDao()
        val primary = FakeSource(Sources.STATION_INFOPOCASI, measurement(Sources.STATION_INFOPOCASI, now))
        val fallback = FakeSource(Sources.STATION_CHMU, measurement(Sources.STATION_CHMU, now))
        val repo = WeatherRepository(listOf(primary, fallback), dao, clock = { now })

        val snap = repo.refresh()

        assertEquals(1, primary.fetchCount)
        assertEquals(1, fallback.fetchCount)
        assertEquals(2, dao.rows.size)
        assertEquals(WeatherRepository.Freshness.FRESH, snap.freshness)
        assertEquals(2, snap.measurements.size)
    }

    @Test
    fun refresh_primaryFails_fallbackUsed() = runTest {
        val dao = FakeDao()
        val primary = FakeSource(Sources.STATION_INFOPOCASI, null)
        val fallback = FakeSource(Sources.STATION_CHMU, measurement(Sources.STATION_CHMU, now))
        val repo = WeatherRepository(listOf(primary, fallback), dao, clock = { now })

        val snap = repo.refresh()

        assertEquals(null, snap.measurements[Sources.STATION_INFOPOCASI])
        assertEquals(15f, snap.measurements[Sources.STATION_CHMU]!!.temperatureC!!, 0.001f)
        assertEquals(WeatherRepository.Freshness.FRESH, snap.freshness)
    }

    @Test
    fun refresh_bothFail_servesCacheAsOffline() = runTest {
        val dao = FakeDao()
        // Pre-existing cache from an earlier (old) fetch.
        dao.insert(measurement(Sources.STATION_INFOPOCASI, now - 3 * 60_000).toEntity())
        val primary = FakeSource(Sources.STATION_INFOPOCASI, null)
        val fallback = FakeSource(Sources.STATION_CHMU, null)
        val repo = WeatherRepository(listOf(primary, fallback), dao, clock = { now })

        val snap = repo.refresh()

        assertEquals(WeatherRepository.Freshness.OFFLINE, snap.freshness)
        assertEquals(1, snap.measurements.size) // cached value still served (G6)
    }

    @Test
    fun refresh_bothFail_noCache_offlineEmpty() = runTest {
        val repo = WeatherRepository(
            listOf(FakeSource("A", null), FakeSource("B", null)), FakeDao(), clock = { now }
        )
        val snap = repo.refresh()
        assertEquals(WeatherRepository.Freshness.OFFLINE, snap.freshness)
        assertTrue(snap.measurements.isEmpty())
    }

    @Test
    fun refresh_allDataTooOld_markedStale() = runTest {
        val old = now - (Sources.STALE_THRESHOLD_MIN + 5) * 60_000L
        val repo = WeatherRepository(
            listOf(
                FakeSource(Sources.STATION_INFOPOCASI, measurement(Sources.STATION_INFOPOCASI, old)),
                FakeSource(Sources.STATION_CHMU, null)
            ),
            FakeDao(), clock = { now }
        )
        val snap = repo.refresh()
        assertEquals(WeatherRepository.Freshness.STALE, snap.freshness)
    }

    @Test
    fun refresh_secondCallWithinRateLimit_skipsNetwork() = runTest {
        var time = now
        val primary = FakeSource(Sources.STATION_INFOPOCASI, measurement(Sources.STATION_INFOPOCASI, now))
        val fallback = FakeSource(Sources.STATION_CHMU, null)
        val repo = WeatherRepository(listOf(primary, fallback), FakeDao(), clock = { time })

        repo.refresh()
        time += tenMin - 1            // just below the 10 min limit
        repo.refresh()
        assertEquals(1, primary.fetchCount)
        assertEquals(1, fallback.fetchCount)

        time += 1                     // exactly 10 min after the first attempt
        repo.refresh()
        assertEquals(2, primary.fetchCount)
        assertEquals(2, fallback.fetchCount)
    }

    @Test
    fun refresh_rateLimitIsPerSource_failedPrimaryStillCountsItsOwnAttempt() = runTest {
        var time = now
        val primary = FakeSource(Sources.STATION_INFOPOCASI, null)   // fails -> recorded attempt
        val fallback = FakeSource(Sources.STATION_CHMU, measurement(Sources.STATION_CHMU, now))
        val repo = WeatherRepository(listOf(primary, fallback), FakeDao(), clock = { time })

        repo.refresh()
        time += tenMin - 1
        repo.refresh()
        // F1.5: max 1 request per source per 10 min — even failed attempts count.
        assertEquals(1, primary.fetchCount)
        assertEquals(1, fallback.fetchCount)
    }

    @Test
    fun refresh_fetchExceptionIsTreatedAsFailure_notCrash() = runTest {
        val exploding = object : StationDataSource {
            override val id = "EXPLODING"
            override suspend fun fetch(): StationMeasurement? =
                throw java.io.IOException("network down")
        }
        val repo = WeatherRepository(listOf(exploding), FakeDao(), clock = { now })
        val snap = repo.refresh()
        assertEquals(WeatherRepository.Freshness.OFFLINE, snap.freshness) // no crash, F1.4
    }

    @Test
    fun entityRoundtrip_toEntityToModel_preservesValues() = runTest {
        val m = StationMeasurement(
            station = "S", temperatureC = 1.5f, humidityPct = 80, pressureHpa = 1000f,
            windMs = 3.6f, windGustMs = 7.2f, windDirDeg = 90, rainMm = 2.5f,
            measuredAtMs = 111L, fetchedAtMs = 222L
        )
        val e = m.toEntity().copy(id = 7L)
        val back = e.toModel()
        assertEquals(m, back.copy(station = m.station))
    }
}
