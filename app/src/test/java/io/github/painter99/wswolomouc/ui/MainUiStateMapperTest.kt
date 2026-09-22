package io.github.painter99.wswolomouc.ui

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.FetchResult
import io.github.painter99.wswolomouc.data.StationMeasurement
import io.github.painter99.wswolomouc.data.WeatherRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI state mapping tests (M1.5 + M1.6a.1 hotfix, PRD F3.1/F3.2) — pure JVM.
 *
 * M1.6a.1 additions:
 *  - wind display contract pinned end-to-end (mapper -> Format) after the
 *    double x3.6 conversion bug seen on device (80 km/h shown for 6.2 m/s);
 *  - a station without data renders as a placeholder card ("bez dat"),
 *    it no longer disappears silently (US-002, G3 transparency).
 */
class MainUiStateMapperTest {

    private val now = 1_758_000_000_000L
    private val minute = 60_000L

    private fun measurement(
        station: String,
        measuredAtMs: Long = now,
        rainMm: Float? = 1.5f,
        rainDailyMm: Float? = 1.2f
    ) = StationMeasurement(
        station = station,
        temperatureC = 21.4f,
        humidityPct = 55,
        pressureHpa = if (station == Sources.STATION_INFOPOCASI) 1013f else null,
        windMs = 2.5f,
        windGustMs = 8f,
        windDirDeg = 200,
        rainMm = rainMm,
        rainDailyMm = rainDailyMm,
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
        assertTrue(ui.hasData)
    }

    @Test
    fun stationUi_chmuHasNoDisplayNamePressure() {
        val ui = MainUiStateMapper.stationUi(measurement(Sources.STATION_CHMU), now)
        assertEquals("ČHMÚ Olomouc–Holice", ui.displayName)
        assertNull(ui.pressureHpa) // 10M feed has no P element (M1.3)
    }

    @Test
    fun stationUi_rainLabelUnified_dailyForBothSources() {
        // Pavel 22. 9.: both stations must show the SAME parameter (daily total)
        assertEquals(
            "úhrn za den",
            MainUiStateMapper.stationUi(measurement(Sources.STATION_INFOPOCASI), now).rainLabel
        )
        assertEquals(
            "úhrn za den",
            MainUiStateMapper.stationUi(measurement(Sources.STATION_CHMU), now).rainLabel
        )
    }

    @Test
    fun stationUi_showsDailyRain_notRawTenMinuteValue() {
        // CHMU raw rainMm = 0.3 (last 10 min) must NOT be displayed;
        // the card shows the unified daily total (0.6) instead.
        val ui = MainUiStateMapper.stationUi(
            measurement(Sources.STATION_CHMU, rainMm = 0.3f, rainDailyMm = 0.6f), now
        )
        assertEquals(0.6f, ui.rainDailyMm!!, 0.001f)
        assertEquals("úhrn za den", ui.rainLabel)
    }

    /**
     * Prove-It chain test (M1.6a.1): the value that reaches Format.wind must
     * be m/s — exactly one x3.6 conversion end-to-end. 2.5 m/s -> "9 km/h",
     * 8 m/s -> "29 km/h". (On device the card showed 80/184 km/h for a
     * 6.2/14.2 m/s day because Format.wind received already-converted km/h.)
     */
    @Test
    fun wind_chain_mapperToFormat_convertsExactlyOnce() {
        val ui = MainUiStateMapper.stationUi(measurement(Sources.STATION_CHMU), now)
        assertEquals("9 km/h", Format.wind(ui.windMs))
        assertEquals("29 km/h", Format.wind(ui.windGustMs))
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

    /**
     * M1.6a.1: a missing station is a placeholder card, not a silent gap —
     * the user must see WHICH station has no data (G3, US-002).
     */
    @Test
    fun fromSnapshot_missingStation_isPlaceholderWithoutData() {
        val snapshot = WeatherRepository.Snapshot(
            freshness = WeatherRepository.Freshness.OFFLINE,
            measurements = mapOf(
                Sources.STATION_CHMU to measurement(Sources.STATION_CHMU, now - 2 * minute)
            )
        )
        val state = MainUiStateMapper.from(snapshot, now)

        val infopocasi = state.primary
        assertEquals(Sources.STATION_INFOPOCASI, infopocasi?.station)
        assertEquals("Infopocasi Olomouc", infopocasi?.displayName)
        assertEquals(false, infopocasi?.hasData)
        assertNull(infopocasi?.measuredAtMs)
        assertNull(infopocasi?.temperatureC)
        assertNull(infopocasi?.windMs)
        assertEquals(false, infopocasi?.isStale)

        val chmu = state.secondary
        assertEquals(Sources.STATION_CHMU, chmu?.station)
        assertEquals(true, chmu?.hasData)
        assertEquals(now - 2 * minute, chmu?.measuredAtMs)
    }

    @Test
    fun fromSnapshot_offlineEmpty_bothPlaceholders() {
        val snapshot = WeatherRepository.Snapshot(
            freshness = WeatherRepository.Freshness.OFFLINE,
            measurements = emptyMap()
        )
        val state = MainUiStateMapper.from(snapshot, now)
        assertEquals(false, state.primary?.hasData)
        assertEquals(false, state.secondary?.hasData)
        assertEquals(WeatherRepository.Freshness.OFFLINE, state.freshness)
    }

    // --- M1.6b-3: per-source status labels -----------------------------------

    @Test
    fun fromSnapshot_mapsPerSourceStatusLabels() {
        val snapshot = WeatherRepository.Snapshot(
            freshness = WeatherRepository.Freshness.STALE,
            measurements = mapOf(Sources.STATION_INFOPOCASI to measurement(Sources.STATION_INFOPOCASI)),
            sourceResults = mapOf(
                Sources.STATION_INFOPOCASI to
                    FetchResult.Success(measurement(Sources.STATION_INFOPOCASI)),
                Sources.STATION_CHMU to FetchResult.HttpError(404)
            )
        )

        val state = MainUiStateMapper.from(snapshot, now)

        assertEquals("OK", state.sourceStatus[Sources.STATION_INFOPOCASI])
        assertEquals("HTTP 404", state.sourceStatus[Sources.STATION_CHMU])
    }

    @Test
    fun fromSnapshot_networkAndParseErrors_getHumanLabels() {
        val snapshot = WeatherRepository.Snapshot(
            freshness = WeatherRepository.Freshness.OFFLINE,
            measurements = emptyMap(),
            sourceResults = mapOf(
                Sources.STATION_INFOPOCASI to FetchResult.NetworkError("timeout"),
                Sources.STATION_CHMU to FetchResult.ParseError("bad json")
            )
        )

        val state = MainUiStateMapper.from(snapshot, now)

        assertEquals("síť", state.sourceStatus[Sources.STATION_INFOPOCASI])
        assertEquals("data", state.sourceStatus[Sources.STATION_CHMU])
    }

    @Test
    fun fromSnapshot_placeholderCarriesSourceStatusNote() {
        val snapshot = WeatherRepository.Snapshot(
            freshness = WeatherRepository.Freshness.OFFLINE,
            measurements = emptyMap(),
            sourceResults = mapOf(
                Sources.STATION_CHMU to FetchResult.HttpError(404)
            )
        )

        val state = MainUiStateMapper.from(snapshot, now)

        assertEquals("HTTP 404", state.secondary?.statusNote)
        assertNull(state.primary?.statusNote)
    }
}
