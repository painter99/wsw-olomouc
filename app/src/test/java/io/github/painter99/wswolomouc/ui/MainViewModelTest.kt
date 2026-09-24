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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        private val result: StationMeasurement?
    ) : StationDataSource {
        var fetchCount = 0
        override suspend fun fetch(): StationMeasurement? {
            fetchCount++
            return result
        }
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
        secondary: StationMeasurement? = null,
        themeStore: ThemeStore = FakeThemeStore()
    ): MainViewModel {
        val repo = WeatherRepository(
            sources = listOf(
                FakeSource(Sources.STATION_INFOPOCASI, primary),
                FakeSource(Sources.STATION_CHMU, secondary)
            ),
            dao = dao,
            clock = { now }
        )
        return MainViewModel(repo, themeStore, clock = { now })
    }

    private class FakeThemeStore : ThemeStore {
        private val flow = MutableStateFlow(ThemeMode.SYSTEM)
        override val mode: Flow<ThemeMode> = flow
        override suspend fun set(mode: ThemeMode) { flow.value = mode }
    }

    /** The repository refresh runs on Dispatchers.IO — wait for it in real time. */
    private suspend fun awaitLoaded(vm: MainViewModel): MainUiState =
        withContext(Dispatchers.IO) {
            withTimeout(TimeUnit.SECONDS.toMillis(5)) {
                while (vm.uiState.value.isLoading || vm.uiState.value.isRefreshing) delay(10)
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
    fun sourcesFail_noCache_bothStationsArePlaceholders() = runTest {
        val state = awaitLoaded(viewModel())
        assertEquals(WeatherRepository.Freshness.OFFLINE, state.freshness)
        // M1.6a.1: a station without data is a placeholder card, not null.
        assertEquals(false, state.primary?.hasData)
        assertNull(state.primary?.measuredAtMs)
        assertEquals(false, state.secondary?.hasData)
    }

    @Test
    fun sourcesFail_withCache_freshCacheServedWithoutNetwork() = runTest {
        val dao = FakeDao()
        dao.insert(measurement(Sources.STATION_INFOPOCASI).toEntity())
        val state = awaitLoaded(viewModel(dao = dao))

        // Round 6 fix (Pavel 23. 9.): fresh cache (< 10 min) must NOT trigger
        // a network refresh on startup — opening the app must not burn the
        // 10-min manual-refresh budget. Fresh data = FRESH, not OFFLINE.
        assertEquals(WeatherRepository.Freshness.FRESH, state.freshness)
        assertEquals(Sources.STATION_INFOPOCASI, state.primary?.station)
        assertEquals(true, state.primary?.hasData)
        assertEquals(Sources.STATION_CHMU, state.secondary?.station)
        assertEquals(false, state.secondary?.hasData)
    }

    @Test
    fun sourcesFail_withStaleCache_staleStillServesLatestValues() = runTest {
        val dao = FakeDao()
        dao.insert(measurement(Sources.STATION_INFOPOCASI, now - 45 * 60_000L).toEntity())
        val state = awaitLoaded(viewModel(dao = dao))

        // G6: the last known values are still served. M1.7b (Pavel 24. 9.):
        // 45-min-old data is STALE (data age), not "Offline" — the network
        // cycle outcome no longer drives the freshness label.
        assertEquals(WeatherRepository.Freshness.STALE, state.freshness)
        assertEquals(Sources.STATION_INFOPOCASI, state.primary?.station)
        assertEquals(true, state.primary?.hasData)
        assertEquals(Sources.STATION_CHMU, state.secondary?.station)
        assertEquals(false, state.secondary?.hasData)
    }

    @Test
    fun startup_freshCache_skipsNetworkRefresh() = runTest {
        val dao = FakeDao()
        dao.insert(measurement(Sources.STATION_INFOPOCASI, now - 4 * 60_000L).toEntity())
        val primary = FakeSource(Sources.STATION_INFOPOCASI, measurement(Sources.STATION_INFOPOCASI))
        val repo = WeatherRepository(
            listOf(primary, FakeSource(Sources.STATION_CHMU, null)), dao, clock = { now }
        )
        val vm = MainViewModel(repo, FakeThemeStore(), clock = { now })
        awaitLoaded(vm)

        assertEquals(0, primary.fetchCount)
    }

    @Test
    fun refresh_rateLimited_messageShowsDataAgeAndRemaining() = runTest {
        val dao = FakeDao()
        dao.insert(measurement(Sources.STATION_INFOPOCASI, now - 4 * 60_000L).toEntity())
        val primary = FakeSource(
            Sources.STATION_INFOPOCASI, measurement(Sources.STATION_INFOPOCASI, now - 4 * 60_000L)
        )
        val repo = WeatherRepository(
            listOf(primary, FakeSource(Sources.STATION_CHMU, null)), dao, clock = { now }
        )
        val vm = MainViewModel(repo, FakeThemeStore(), clock = { now })
        awaitLoaded(vm)      // init: fresh cache -> no network
        vm.refresh()         // manual #1: fetches (data age 4 min)
        awaitLoaded(vm)      // single-flight: #1 must FINISH before #2
        vm.refresh()         // manual #2: rate limited (fixed clock)

        val state = awaitLoaded(vm)
        val msg = state.rateLimitMessage!!
        // Round 6 fix (Pavel 23. 9.): the message must be HONEST — it must
        // say how old the shown data is, not just "wait 10 min".
        assertTrue("was: $msg", msg.contains("před 4 min"))
        assertTrue("was: $msg", msg.contains("za"))
    }

    // --- M1.6b-3: refresh feedback -------------------------------------------

    @Test
    fun refresh_completes_isRefreshingFalse() = runTest {
        val vm = viewModel(
            primary = measurement(Sources.STATION_INFOPOCASI),
            secondary = measurement(Sources.STATION_CHMU)
        )
        val state = awaitLoaded(vm)

        assertFalse(state.isRefreshing)
        assertNull(state.rateLimitMessage)
    }

    @Test
    fun refresh_withinRateLimit_showsRateLimitMessage() = runTest {
        val vm = viewModel(
            primary = measurement(Sources.STATION_INFOPOCASI),
            secondary = measurement(Sources.STATION_CHMU)
        )
        awaitLoaded(vm)

        vm.refresh()   // fixed clock -> every source is rate limited
        val state = awaitLoaded(vm)

        assertTrue("was ${state.rateLimitMessage}", state.rateLimitMessage != null)
        assertTrue("was ${state.rateLimitMessage}", state.rateLimitMessage!!.contains("min"))
    }

    @Test
    fun refresh_rateLimited_messageSaysUpdateWasSkipped() = runTest {
        // 24. 9. 2026: tapping "Aktualizovat" within the rate limit must NOT
        // read like a completed update — the message must say the tap was
        // SKIPPED. The data age is NOT part of this message anymore (it is
        // visible in the station cards right above) — only the wait time.
        val vm = viewModel(
            primary = measurement(Sources.STATION_INFOPOCASI),
            secondary = measurement(Sources.STATION_CHMU)
        )
        awaitLoaded(vm)

        vm.refresh()   // fixed clock -> every source is rate limited
        val state = awaitLoaded(vm)

        val msg = state.rateLimitMessage!!
        assertTrue("was: $msg", msg.contains("přeskočena"))
        assertTrue("was: $msg", msg.contains("min"))
        assertFalse("was: $msg", msg.contains("data před"))
    }

    // --- M1.7-trend: app trend arrows -----------------------------------------

    @Test
    fun trends_computedForPrimary_fromRoomHistory() = runTest {
        val hour = 3_600_000L
        val dao = FakeDao()
        dao.insert(
            measurement(Sources.STATION_INFOPOCASI, now - 7 * hour).copy(temperatureC = 20f).toEntity()
        )
        dao.insert(
            measurement(Sources.STATION_INFOPOCASI, now - 3 * hour - 30 * 60_000L)
                .copy(temperatureC = 10f).toEntity()
        )
        val vm = viewModel(dao = dao, primary = measurement(Sources.STATION_INFOPOCASI))
        val state = awaitLoaded(vm)

        assertEquals(listOf("1 h", "3 h", "6 h"), state.trends.map { it.label })
        // 15 °C now vs 10 °C 3.5 h ago -> rising for the 1 h and 3 h windows;
        // vs 20 °C 7 h ago -> falling for the 6 h window.
        assertEquals(TrendDirection.RISING, state.trends[0].direction)
        assertEquals(TrendDirection.RISING, state.trends[1].direction)
        assertEquals(TrendDirection.FALLING, state.trends[2].direction)
    }

    // --- round 2: trend windows without history are hidden --------------------

    @Test
    fun trends_windowsWithoutHistory_areHidden() = runTest {
        val dao = FakeDao()
        dao.insert(
            measurement(Sources.STATION_INFOPOCASI, now - 2 * 3_600_000L)
                .copy(temperatureC = 10f).toEntity()
        )
        val vm = viewModel(dao = dao, primary = measurement(Sources.STATION_INFOPOCASI))
        val state = awaitLoaded(vm)

        // Only the 1 h window has a past row (2 h old); 3 h / 6 h are hidden
        // instead of showing "–" (Pavel 23. 9., round 2: "jen okna, kde
        // už historie je").
        assertEquals(listOf("1 h"), state.trends.map { it.label })
        assertEquals(TrendDirection.RISING, state.trends[0].direction)
    }

    // --- round 2: theme setting ------------------------------------------------

    @Test
    fun themeMode_exposesStoreFlow_andSetThemePersists() = runTest {
        val store = FakeThemeStore()
        val vm = viewModel(themeStore = store)
        assertEquals(ThemeMode.SYSTEM, vm.themeMode.first())
        vm.setTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.themeMode.first())
    }
}
