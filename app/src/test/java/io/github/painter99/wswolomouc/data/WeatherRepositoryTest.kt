package io.github.painter99.wswolomouc.data

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.db.MeasurementDao
import io.github.painter99.wswolomouc.db.MeasurementEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        override suspend fun latestBefore(station: String, beforeEpochMs: Long): MeasurementEntity? =
            rows.filter { it.station == station && it.measuredAt <= beforeEpochMs }
                .maxByOrNull { it.measuredAt }
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
        private var result: StationMeasurement?,
        private var detailedResult: FetchResult? = null
    ) : StationDataSource {
        var fetchCount = 0
        override suspend fun fetch(): StationMeasurement? {
            fetchCount++
            return result
        }
        override suspend fun fetchResult(): FetchResult {
            fetchCount++
            return detailedResult
                ?: result?.let { FetchResult.Success(it) }
                ?: FetchResult.ParseError("fake default")
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

    // --- M1.6b-3: per-source outcome recording -------------------------------

    @Test
    fun refresh_recordsPerSourceOutcome() = runTest {
        val ok = FakeSource(
            "A", measurement("A", now),
            FetchResult.Success(measurement("A", now))
        )
        val bad = FakeSource("B", null, FetchResult.HttpError(404))
        val repo = WeatherRepository(listOf(ok, bad), FakeDao(), clock = { now })

        val snap = repo.refresh()

        assertTrue("was ${snap.sourceResults["A"]}", snap.sourceResults["A"] is FetchResult.Success)
        assertEquals(404, (snap.sourceResults["B"] as FetchResult.HttpError).code)
    }

    @Test
    fun refresh_fetchException_recordsNetworkError_notCrash() = runTest {
        val exploding = object : StationDataSource {
            override val id = "EXPLODING"
            override suspend fun fetch(): StationMeasurement? =
                throw java.io.IOException("network down")
        }
        val repo = WeatherRepository(listOf(exploding), FakeDao(), clock = { now })

        val snap = repo.refresh()

        assertTrue(
            "was ${snap.sourceResults["EXPLODING"]}",
            snap.sourceResults["EXPLODING"] is FetchResult.NetworkError
        )
    }

    @Test
    fun refresh_rateLimitedSource_keepsLastOutcome() = runTest {
        val bad = FakeSource("B", null, FetchResult.NetworkError("timeout"))
        val repo = WeatherRepository(listOf(bad), FakeDao(), clock = { now })

        repo.refresh()
        val second = repo.refresh()   // rate limited -> no new fetch

        assertEquals(1, bad.fetchCount)
        assertTrue(
            "was ${second.sourceResults["B"]}",
            second.sourceResults["B"] is FetchResult.NetworkError
        )
    }

    @Test
    fun refresh_setsNextRefreshAllowedAt() = runTest {
        val ok = FakeSource("A", measurement("A", now), FetchResult.Success(measurement("A", now)))
        val repo = WeatherRepository(listOf(ok), FakeDao(), clock = { now })

        val snap = repo.refresh()

        assertEquals(now + Sources.FETCH_RATE_LIMIT_MS, snap.nextRefreshAllowedAtMs)
    }

    @Test
    fun refresh_allSourcesRateLimited_reportsSkip() = runTest {
        val ok = FakeSource("A", measurement("A", now), FetchResult.Success(measurement("A", now)))
        val repo = WeatherRepository(listOf(ok), FakeDao(), clock = { now })

        repo.refresh()
        val second = repo.refresh()   // rate limited -> nothing attempted

        assertTrue(second.skippedByRateLimit)
        assertEquals(now + Sources.FETCH_RATE_LIMIT_MS, second.nextRefreshAllowedAtMs)
    }

    // --- M1.7-trend: past temperature lookup (trend arrows) ------------------

    @Test
    fun pastTemperature_returnsLatestRowAtOrBeforeTheCutoff() = runTest {
        val hour = 3_600_000L
        val dao = FakeDao()
        dao.insert(measurement(Sources.STATION_INFOPOCASI, now - 3 * hour, temp = 12f).toEntity())
        dao.insert(measurement(Sources.STATION_INFOPOCASI, now - 1 * hour, temp = 14f).toEntity())
        val repo = WeatherRepository(
            listOf(FakeSource(Sources.STATION_INFOPOCASI, null)), dao, clock = { now }
        )

        // Latest row at or before the cutoff (boundary inclusive).
        assertEquals(12f, repo.pastTemperature(Sources.STATION_INFOPOCASI, now - 3 * hour)!!, 0.001f)
        assertEquals(14f, repo.pastTemperature(Sources.STATION_INFOPOCASI, now - 1 * hour)!!, 0.001f)
        assertNull(repo.pastTemperature(Sources.STATION_INFOPOCASI, now - 5 * hour))
    }
}
