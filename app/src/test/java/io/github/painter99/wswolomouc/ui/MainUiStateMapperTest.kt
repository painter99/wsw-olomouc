package io.github.painter99.wswolomouc.ui

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.StationMeasurement
import io.github.painter99.wswolomouc.data.WeatherRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI state mapping tests (M1.5, PRD F3.1/F3.2) — pure JVM.
 */
class MainUiStateMapperTest {

    private val now = 1_758_000_000_000L
    private val minute = 60_000L

    private fun measurement(
        station: String,
        measuredAtMs: Long = now,
        rainMm: Float? = 1.5f
    ) = StationMeasurement(
        station = station,
        temperatureC = 21.4f,
        humidityPct = 55,
        pressureHpa = if (station == Sources.STATION_INFOPOCASI) 1013f else null,
        windMs = 2.5f,
        windGustMs = 8f,
        windDirDeg = 200,
        rainMm = rainMm,
        measuredAtMs = measuredAtMs,
        fetchedAtMs = now
    )

    // --- StationUi mapping ---------------------------------------------------

    @Test
    fun stationUi_copiesValues_andResolvesDisplayName() {
        val ui = MainUiStateMapper.stationUi(measurement(Sources.STATION_INFOPOCASI), now)
        assertEquals("Infopocasi Olomouc", ui.displayName)
        assertEquals(21.4f, ui.temperatureC)
        assertEquals(55, ui.humidityPct)
        assertEquals(1013f, ui.pressureHpa)
        assertFalse(ui.isStale)
    }

    @Test
    fun stationUi_chmuHasNoDisplayNamePressure() {
        val ui = MainUiStateMapper.stationUi(measurement(Sources.STATION_CHMU), now)
        assertEquals("ČHMÚ Olomouc–Holice", ui.displayName)
        assertNull(ui.pressureHpa) // 10M feed has no P element (M1.3)
    }

    @Test
    fun stationUi_rainLabelDiffersPerSource() {
        assertEquals(
            "úhrn za den",
            MainUiStateMapper.stationUi(measurement(Sources.STATION_INFOPOCASI), now).rainLabel
        )
        assertEquals(
            "úhrn za 10 min",
            MainUiStateMapper.stationUi(measurement(Sources.STATION_CHMU), now).rainLabel
        )
    }

    @Test
    fun stationUi_windConvertedToKmh() {
        val ui = MainUiStateMapper.stationUi(measurement(Sources.STATION_CHMU), now)
        assertEquals(9.0f, ui.windKmh)   // 2.5 m/s * 3.6
        assertEquals(28.8f, ui.gustKmh)  // 8 m/s * 3.6
    }

    @Test
    fun stationUi_stale_afterThreshold_notBefore() {
        val fresh = MainUiStateMapper.stationUi(measurement(Sources.STATION_CHMU, now - 29 * minute), now)
        val boundary = MainUiStateMapper.stationUi(measurement(Sources.STATION_CHMU, now - 30 * minute), now)
        val stale = MainUiStateMapper.stationUi(measurement(Sources.STATION_CHMU, now - 31 * minute), now)
        assertFalse(fresh.isStale)
        assertFalse(boundary.isStale) // exactly 30 min is still fresh (<=)
        assertTrue(stale.isStale)
    }

    // --- MainUiState from snapshot --------------------------------------------

    @Test
    fun fromSnapshot_mapsPrimaryAndSecondary_isNotLoading() {
        val snapshot = WeatherRepository.Snapshot(
            freshness = WeatherRepository.Freshness.FRESH,
            measurements = mapOf(
                Sources.STATION_INFOPOCASI to measurement(Sources.STATION_INFOPOCASI),
                Sources.STATION_CHMU to measurement(Sources.STATION_CHMU)
            )
        )
        val state = MainUiStateMapper.from(snapshot, now)
        assertFalse(state.isLoading)
        assertEquals(WeatherRepository.Freshness.FRESH, state.freshness)
        assertEquals(Sources.STATION_INFOPOCASI, state.primary?.station)
        assertEquals(Sources.STATION_CHMU, state.secondary?.station)
    }

    @Test
    fun fromSnapshot_offlineEmpty_hasNoStations() {
        val snapshot = WeatherRepository.Snapshot(
            freshness = WeatherRepository.Freshness.OFFLINE,
            measurements = emptyMap()
        )
        val state = MainUiStateMapper.from(snapshot, now)
        assertNull(state.primary)
        assertNull(state.secondary)
        assertEquals(WeatherRepository.Freshness.OFFLINE, state.freshness)
    }
}
