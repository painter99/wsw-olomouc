package io.github.painter99.wswolomouc.ui

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.StationDataSource
import io.github.painter99.wswolomouc.data.StationMeasurement
import io.github.painter99.wswolomouc.data.WeatherRepository
import io.github.painter99.wswolomouc.data.toEntity
import io.github.painter99.wswolomouc.db.MeasurementDao
import io.github.painter99.wswolomouc.db.MeasurementEntity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * MainViewModel tests (M1.5) — real WeatherRepository wired to fake data
 * sources and a fake DAO (same pattern as M1.4), pure JVM, no network.
 */
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- fakes (duplicated from WeatherRepositoryTest, kept independent) ---

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
        private val result: StationMeasurement?
    ) : StationDataSource {
        override suspend fun fetch(): StationMeasurement? = result
    }

    // --- helpers ------------------------------------------------------------

    private val now = 1_758_000_000_000L

    private fun measurement(station: String, measuredAtMs: Long = now) = StationMeasurement(
        station = station,
        temperatureC = 15f,
        humidityPct = 60,
        pressureHpa = if (station == Sources.STATION_INFOPOCASI) 1013f else null,
        windMs = 2f,
        windGustMs = 5f,
        windDirDeg = 200,
        rainMm = 0f,
        measuredAtMs = measuredAtMs,
        fetchedAtMs = measuredAtMs
    )

    private fun viewModel(
        dao: MeasurementDao = FakeDao(),
        primary: StationMeasurement? = null,
        secondary: StationMeasurement? = null
    ): MainViewModel {
        val repo = WeatherRepository(
            sources = listOf(
                FakeSource(Sources.STATION_INFOPOCASI, primary),
                FakeSource(Sources.STATION_CHMU, secondary)
            ),
            dao = dao,
            clock = { now }
        )
        return MainViewModel(repo, clock = { now })
    }

    /** The repository refresh runs on Dispatchers.IO — wait for it in real time. */
    private suspend fun awaitLoaded(vm: MainViewModel): MainUiState =
        withContext(Dispatchers.IO) {
            withTimeout(TimeUnit.SECONDS.toMillis(5)) {
                while (vm.uiState.value.isLoading) delay(10)
            }
            vm.uiState.value
        }

    // --- tests ----------------------------------------------------------------

    @Test
    fun bothSourcesOk_primaryInfopocasi_secondaryChmu() = runTest {
        val vm = viewModel(
            primary = measurement(Sources.STATION_INFOPOCASI),
            secondary = measurement(Sources.STATION_CHMU)
        )
        val state = awaitLoaded(vm)

        assertFalse(state.isLoading)
        assertEquals(WeatherRepository.Freshness.FRESH, state.freshness)
        assertEquals(Sources.STATION_INFOPOCASI, state.primary?.station)
        assertEquals(Sources.STATION_CHMU, state.secondary?.station)
    }

    @Test
    fun sourcesFail_noCache_offlineWithNoStations() = runTest {
        val state = awaitLoaded(viewModel())
        assertEquals(WeatherRepository.Freshness.OFFLINE, state.freshness)
        assertNull(state.primary)
        assertNull(state.secondary)
    }

    @Test
    fun sourcesFail_withCache_offlineStillServesLatestValues() = runTest {
        val dao = FakeDao()
        dao.insert(measurement(Sources.STATION_INFOPOCASI).toEntity())
        val state = awaitLoaded(viewModel(dao = dao))

        assertEquals(WeatherRepository.Freshness.OFFLINE, state.freshness)
        assertEquals(Sources.STATION_INFOPOCASI, state.primary?.station)
        assertNull(state.secondary)
    }
}
