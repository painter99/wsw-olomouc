package io.github.painter99.wswolomouc.widget

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.StationMeasurement
import io.github.painter99.wswolomouc.ui.TrendDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget layout tests v2 (M1.6b-2 redesign after Pavel's feedback 22. 9.):
 *
 *  1. LEFT = synthesis value (F2.5 fallback rules — ALWAYS some value) +
 *     secondary row (feels-like / wind / rain, max 3, from the station
 *     behind the badge) + age.
 *  2. BOTH stations must appear on the widget, each with the time of its
 *     last real measurement (Pavel: "na widgetu musí být zmíněné obě
 *     stanice a u každé čas poslední reálné aktualizace").
 *  3. NO gray text — every text color is white (WidgetPaletteTest); only
 *     the status dots are colored.
 *  4. Temperatures are shown to one decimal ("13,6 °C", Pavel 22. 9.).
 *  5. Rain label shortened but honest (M1.4): "(den)" / "(10 min)".
 */
class WidgetLayoutTest {

    private val now = 1_758_000_000_000L
    private val minute = 60_000L

    private fun measurement(
        station: String,
        tempC: Float?,
        ageMin: Long,
        humidityPct: Int? = 60,
        windMs: Float? = null,
        rainMm: Float? = null,
        rainDailyMm: Float? = null
    ): StationMeasurement = StationMeasurement(
        station = station,
        temperatureC = tempC,
        humidityPct = humidityPct,
        pressureHpa = null,
        windMs = windMs,
        windGustMs = null,
        windDirDeg = null,
        rainMm = rainMm,
        rainDailyMm = rainDailyMm,
        measuredAtMs = now - ageMin * minute,
        fetchedAtMs = now
    )

    private fun build(
        infopocasi: StationMeasurement?,
        chmu: StationMeasurement?,
        wide: Boolean = false,
        trend: TrendDirection? = null
    ): WidgetLayoutState {
        val map = buildMap {
            infopocasi?.let { put(Sources.STATION_INFOPOCASI, it) }
            chmu?.let { put(Sources.STATION_CHMU, it) }
        }
        return WidgetLayout.build(map, now, wide, trend)
    }

    @Test
    fun bothFresh_leftAverage_secondaryFromPrimary() {
        val s = build(
            infopocasi = measurement(
                Sources.STATION_INFOPOCASI, 21.0f, 5, windMs = 3.0f, rainDailyMm = 0.5f
            ),
            chmu = measurement(
                Sources.STATION_CHMU, 23.0f, 10, humidityPct = 68, windMs = 4.0f, rainDailyMm = 0.2f
            )
        )
        assertEquals(22.0f, s.left.temperatureC!!, 0.001f)
        assertEquals("Ø 2 stanice", s.left.badge)
        assertEquals(WidgetStatus.OK, s.left.status)
        assertEquals(Sources.STATION_INFOPOCASI, s.left.sourceStation)
        assertEquals(now - 10 * minute, s.left.measuredAtMs) // honest age = older
        // Secondary row from the PRIMARY station (F2.5: synthesis is temp only)
        assertEquals(listOf("Pocitová", "Vítr", "Srážky"), s.secondary.map { it.label })
        assertEquals("21,0 °C", s.secondary[0].text)  // one decimal (Pavel 22. 9.)
        assertEquals("11 km/h", s.secondary[1].text)  // 3.0 m/s -> ONCE to km/h
        assertEquals("0,5 mm (den)", s.secondary[2].text) // daily, BOTH sources
    }

    @Test
    fun bothStations_alwaysPresent_withTheirOwnMeasurementAge() {
        val s = build(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 13.6f, 3),
            chmu = measurement(Sources.STATION_CHMU, 14.2f, 34, humidityPct = 68)
        )
        assertEquals(2, s.stations.size)
        assertEquals("Infopocasi", s.stations[0].name)
        assertEquals(13.6f, s.stations[0].temperatureC!!, 0.001f)
        assertTrue(s.stations[0].hasData)
        assertFalse(s.stations[0].isStale)
        assertEquals(now - 3 * minute, s.stations[0].measuredAtMs)
        assertEquals("ČHMÚ Holice", s.stations[1].name)
        assertEquals(14.2f, s.stations[1].temperatureC!!, 0.001f)
        assertTrue(s.stations[1].hasData)
        assertTrue(s.stations[1].isStale)   // 34 min > 30 min threshold
        assertEquals(now - 34 * minute, s.stations[1].measuredAtMs)
    }

    @Test
    fun feelsLike_usesSingleWindConversion() {
        // 5 °C + 4.17 m/s = 15.012 km/h -> WC = 1.75 °C -> "1,7 °C".
        // Double conversion would give "-1,5 °C", no conversion "5,0 °C".
        val s = build(
            infopocasi = measurement(
                Sources.STATION_INFOPOCASI, 5.0f, 5, windMs = 4.17f
            ),
            chmu = measurement(Sources.STATION_CHMU, 12.0f, 120)
        )
        assertEquals("1,7 °C", s.secondary[0].text)
        assertEquals("15 km/h", s.secondary[1].text)
        assertEquals(2, s.secondary.size) // rain null -> omitted
    }

    @Test
    fun feelsLike_missingWind_equalsTemperature() {
        val s = build(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 5.0f, 5, humidityPct = null),
            chmu = null
        )
        assertEquals(listOf("Pocitová"), s.secondary.map { it.label })
        assertEquals("5,0 °C", s.secondary[0].text)
    }

    @Test
    fun onlyChmuFresh_secondaryFromChmu_dailyRainLabel() {
        val s = build(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 12.0f, 120),
            chmu = measurement(
                Sources.STATION_CHMU, 18.0f, 12, humidityPct = 68, windMs = 4.0f, rainDailyMm = 0.2f
            )
        )
        assertEquals("ČHMÚ Holice", s.left.badge)
        assertEquals(Sources.STATION_CHMU, s.left.sourceStation)
        assertEquals("18,0 °C", s.secondary[0].text) // T=18 outside WC and HI domains
        assertEquals("14 km/h", s.secondary[1].text)
        assertEquals("0,2 mm (den)", s.secondary[2].text) // unified daily label
        // Both stations still present; Infopocasi is the stale one.
        assertTrue(s.stations[0].isStale)
        assertFalse(s.stations[1].isStale)
    }

    @Test
    fun stationWithoutData_placeholderBlock() {
        val s = build(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 12.0f, 3),
            chmu = null
        )
        assertFalse(s.stations[1].hasData)
        assertEquals("ČHMÚ Holice", s.stations[1].name)
        assertNull(s.stations[1].temperatureC)
        assertNull(s.stations[1].measuredAtMs)
        assertFalse(s.stations[1].isStale)
    }

    @Test
    fun offline_leftWithoutValue_bothStationBlocksPlaceholder() {
        val s = build(null, null)
        assertEquals(WidgetStatus.OFFLINE, s.left.status)
        assertNull(s.left.temperatureC)
        assertTrue(s.secondary.isEmpty())
        assertFalse(s.stations[0].hasData)
        assertFalse(s.stations[1].hasData)
    }

    @Test
    fun secondary_omitsMissingValues_showsOnlyAvailable() {
        val s = build(
            infopocasi = measurement(
                Sources.STATION_INFOPOCASI, null, 5, windMs = 3.0f
            ),
            chmu = measurement(Sources.STATION_CHMU, 14.2f, 120)
        )
        assertEquals("Infopocasi", s.left.badge)
        assertNull(s.left.temperatureC)
        assertEquals(listOf("Vítr"), s.secondary.map { it.label })
        assertEquals("11 km/h", s.secondary[0].text)
    }

    @Test
    fun wideFlag_controlsStationHumidityVisibility() {
        val infopocasi = measurement(Sources.STATION_INFOPOCASI, 21.0f, 5, windMs = 3.0f)
        val chmu = measurement(Sources.STATION_CHMU, 23.0f, 10, humidityPct = 68)
        assertFalse(build(infopocasi, chmu, wide = false).showStationHumidity)
        assertTrue(build(infopocasi, chmu, wide = true).showStationHumidity)
    }

    /**
     * M1.7-trend (Pavel 23. 9.): ONE trend arrow (3 h window) next to the big
     * temperature — carried into the left widget state by the caller (the
     * widget computes it from Room history, the layout stays pure).
     */
    @Test
    fun trend_isCarriedIntoTheLeftWidgetState() {
        val s = build(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 21.0f, 5),
            chmu = measurement(Sources.STATION_CHMU, 23.0f, 10),
            trend = TrendDirection.RISING
        )
        assertEquals(TrendDirection.RISING, s.left.trend)
    }
}