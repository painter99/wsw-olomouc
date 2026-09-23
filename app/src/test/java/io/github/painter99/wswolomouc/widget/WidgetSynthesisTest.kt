package io.github.painter99.wswolomouc.widget

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.StationMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Widget synthesis tests (F2.5 revision, Pavel 23. 9. 2026) — pure JVM.
 *
 * Rules under test:
 *  - both stations fresh (<= 30 min) -> arithmetic mean + badge "Ø 2 stanice"
 *  - OTHERWISE the PRIMARY station (Infopocasi) always wins as long as it has
 *    any temperature — even when stale (Pavel: "jinak jede vlevo infopocasi")
 *  - only when Infopocasi has no temperature at all -> newest available value
 *  - no data at all -> OFFLINE, no value (G6)
 */
class WidgetSynthesisTest {

    private val now = 1_758_000_000_000L
    private val minute = 60_000L

    private fun measurement(
        station: String,
        tempC: Float?,
        ageMin: Long
    ): StationMeasurement = StationMeasurement(
        station = station,
        temperatureC = tempC,
        humidityPct = 60,
        pressureHpa = null,
        windMs = null,
        windGustMs = null,
        windDirDeg = null,
        rainMm = null,
        measuredAtMs = now - ageMin * minute,
        fetchedAtMs = now
    )

    private fun synthesize(
        infopocasi: StationMeasurement?,
        chmu: StationMeasurement?
    ): WidgetState {
        val map = buildMap {
            infopocasi?.let { put(Sources.STATION_INFOPOCASI, it) }
            chmu?.let { put(Sources.STATION_CHMU, it) }
        }
        return WidgetSynthesis.synthesize(map, now)
    }

    @Test
    fun bothFresh_weightedByFreshness_notSimpleMean() {
        // Round 3 (Pavel 23. 9.): the average is weighted by freshness —
        // w = 1/(age_min + 15). 21.0 °C @5 min (w=1/20) + 23.0 °C @10 min
        // (w=1/25) -> 21.889, NOT the plain mean 22.0.
        val s = synthesize(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 21.0f, 5),
            chmu = measurement(Sources.STATION_CHMU, 23.0f, 10)
        )
        assertEquals(21.889f, s.temperatureC!!, 0.01f)
        assertEquals("Ø 2 stanice", s.badge)
        assertEquals(WidgetStatus.OK, s.status)
        // Honest age = the OLDER of the two measurements
        assertEquals(now - 10 * minute, s.measuredAtMs)
    }

    @Test
    fun weighted_fresherStationHasMoreWeight() {
        // 14.0 °C @2 min (w=1/17) vs 16.0 °C @28 min (w=1/43) -> 14.565:
        // the fresher Infopocasi value pulls the result towards itself.
        val s = synthesize(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 14.0f, 2),
            chmu = measurement(Sources.STATION_CHMU, 16.0f, 28)
        )
        assertEquals(14.565f, s.temperatureC!!, 0.01f)
        assertEquals("Ø 2 stanice", s.badge)
    }

    @Test
    fun exactlyAtThreshold_isStillFresh() {
        val s = synthesize(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 20.0f, 30),
            chmu = measurement(Sources.STATION_CHMU, 22.0f, 30)
        )
        assertEquals(WidgetStatus.OK, s.status)
        assertEquals(21.0f, s.temperatureC!!, 0.001f)
    }

    @Test
    fun onlyPrimaryFresh_showsPrimaryValueAndBadge() {
        val s = synthesize(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 19.5f, 8),
            chmu = measurement(Sources.STATION_CHMU, 99.0f, 120)
        )
        assertEquals(19.5f, s.temperatureC!!, 0.001f)
        assertEquals("Infopocasi", s.badge)
        assertEquals(WidgetStatus.OK, s.status)
        assertEquals(now - 8 * minute, s.measuredAtMs)
    }

    /**
     * Pavel 23. 9.: "jinak jede vlevo infopocasi" — a fresh CHMU value must
     * NOT override the primary station; the primary wins whenever it has any
     * temperature, and its staleness is shown honestly.
     */
    @Test
    fun otherwise_primaryAlwaysWins_evenWhenStaleAndSecondaryFresh() {
        val s = synthesize(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 19.0f, 120),
            chmu = measurement(Sources.STATION_CHMU, 18.0f, 12)
        )
        assertEquals(19.0f, s.temperatureC!!, 0.001f)
        assertEquals("Infopocasi", s.badge)
        assertEquals(WidgetStatus.STALE, s.status)
        assertEquals(now - 120 * minute, s.measuredAtMs)
    }

    @Test
    fun bothStale_primaryWins_withStaleStatus() {
        val s = synthesize(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 15.0f, 90),
            chmu = measurement(Sources.STATION_CHMU, 14.0f, 45)
        )
        assertEquals(15.0f, s.temperatureC!!, 0.001f)
        assertEquals("Infopocasi", s.badge)
        assertEquals(WidgetStatus.STALE, s.status)
        assertEquals(now - 90 * minute, s.measuredAtMs)
    }

    @Test
    fun primaryWithoutTemperature_fallsBackToNewestAvailable() {
        val s = synthesize(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, null, 5),
            chmu = measurement(Sources.STATION_CHMU, 17.0f, 60)
        )
        assertEquals(17.0f, s.temperatureC!!, 0.001f)
        assertEquals("ČHMÚ Holice", s.badge)
        assertEquals(WidgetStatus.STALE, s.status)
    }

    @Test
    fun noData_isOfflineWithoutValue() {
        val s = synthesize(null, null)
        assertNull(s.temperatureC)
        assertNull(s.measuredAtMs)
        assertEquals(WidgetStatus.OFFLINE, s.status)
    }

    @Test
    fun oneStationOnly_chmu_works() {
        val s = synthesize(
            infopocasi = null,
            chmu = measurement(Sources.STATION_CHMU, 20.0f, 3)
        )
        assertEquals(20.0f, s.temperatureC!!, 0.001f)
        assertEquals("ČHMÚ Holice", s.badge)
        assertEquals(WidgetStatus.OK, s.status)
    }

    @Test
    fun noTemperaturesAtAll_isOffline() {
        val s = synthesize(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, null, 5),
            chmu = measurement(Sources.STATION_CHMU, null, 10)
        )
        assertNull(s.temperatureC)
        assertEquals(WidgetStatus.OFFLINE, s.status)
    }
}