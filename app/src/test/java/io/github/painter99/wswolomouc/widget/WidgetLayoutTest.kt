package io.github.painter99.wswolomouc.widget

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.StationMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget layout tests (M1.6b-2, PRD F2.6/NF8) — pure JVM, tabulated.
 *
 * Layout contract:
 *  - LEFT half = existing synthesis (F2.5, unchanged rules) + secondary row
 *    (feels-like / wind / rain, max 3 items, NF8) + data age.
 *  - RIGHT half = the OTHER station, compact (Pavel's proposal d, 21. 9.).
 *  - Secondary row source = the station behind the left badge; with the
 *    average badge ("Ø 2 stanice") the non-temperature values come from the
 *    PRIMARY station (F2.5: synthesis applies to temperature only).
 *  - Rain label is shortened but honest (M1.4 semantics): "(den)" for
 *    INFOPOCASI, "(10 min)" for CHMU.
 *  - Missing values are OMITTED from the secondary row (never "0" or fake).
 *  - Wide mode (5x2) additionally shows the right station's humidity
 *    (showRightHumidity); compact 4x2 does not.
 *  - Contrast: every widget text color >= 7:1 vs black, status dots >= 4.5:1
 *    (WidgetPaletteTest).
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
        rainMm: Float? = null
    ): StationMeasurement = StationMeasurement(
        station = station,
        temperatureC = tempC,
        humidityPct = humidityPct,
        pressureHpa = null,
        windMs = windMs,
        windGustMs = null,
        windDirDeg = null,
        rainMm = rainMm,
        measuredAtMs = now - ageMin * minute,
        fetchedAtMs = now
    )

    private fun build(
        infopocasi: StationMeasurement?,
        chmu: StationMeasurement?,
        wide: Boolean = false
    ): WidgetLayoutState {
        val map = buildMap {
            infopocasi?.let { put(Sources.STATION_INFOPOCASI, it) }
            chmu?.let { put(Sources.STATION_CHMU, it) }
        }
        return WidgetLayout.build(map, now, wide)
    }

    @Test
    fun bothFresh_leftAverage_secondaryFromPrimary_rightIsOtherStation() {
        val s = build(
            infopocasi = measurement(
                Sources.STATION_INFOPOCASI, 21.0f, 5, windMs = 3.0f, rainMm = 0.5f
            ),
            chmu = measurement(
                Sources.STATION_CHMU, 23.0f, 10, humidityPct = 68, windMs = 4.0f, rainMm = 0.2f
            )
        )
        // Left half: existing synthesis rules (F2.5), unchanged.
        assertEquals(22.0f, s.left.temperatureC!!, 0.001f)
        assertEquals("Ø 2 stanice", s.left.badge)
        assertEquals(WidgetStatus.OK, s.left.status)
        assertEquals(Sources.STATION_INFOPOCASI, s.left.sourceStation)
        assertEquals(now - 10 * minute, s.left.measuredAtMs) // honest age = older
        // Secondary row from the PRIMARY station (F2.5: synthesis is temp only).
        assertEquals(listOf("Pocitová", "Vítr", "Srážky"), s.secondary.map { it.label })
        assertEquals("21 °C", s.secondary[0].text)   // T=21 outside WC and HI domains
        assertEquals("11 km/h", s.secondary[1].text) // 3.0 m/s -> 10.8 -> 11 km/h, ONCE
        assertEquals("0,5 mm (den)", s.secondary[2].text)
        // Right half = the OTHER station, compact.
        assertEquals("ČHMÚ Holice", s.right.name)
        assertEquals(23.0f, s.right.temperatureC!!, 0.001f)
        assertTrue(s.right.hasData)
        assertFalse(s.right.isStale)
        assertEquals(68, s.right.humidityPct)
        assertEquals(now - 10 * minute, s.right.measuredAtMs)
    }

    @Test
    fun feelsLike_usesSingleWindConversion() {
        // 5 °C + 4.17 m/s = 15.012 km/h -> WC = 1.75 °C -> "2 °C".
        // Double conversion (54 km/h as m/s) would give WC = -1.5 -> "-2 °C";
        // no conversion (4.17 km/h <= 4.8) would leave "5 °C".
        val s = build(
            infopocasi = measurement(
                Sources.STATION_INFOPOCASI, 5.0f, 5, windMs = 4.17f
            ),
            chmu = measurement(Sources.STATION_CHMU, 12.0f, 120)
        )
        assertEquals("Infopocasi", s.left.badge)
        assertEquals(Sources.STATION_INFOPOCASI, s.left.sourceStation)
        assertEquals("2 °C", s.secondary[0].text)
        assertEquals("15 km/h", s.secondary[1].text) // 4.17 m/s -> 15.012 -> 15
        assertEquals(2, s.secondary.size)            // rain null -> omitted
        // Right half = the stale other station.
        assertEquals("ČHMÚ Holice", s.right.name)
        assertTrue(s.right.isStale)
    }

    @Test
    fun feelsLike_missingWind_equalsTemperature() {
        val s = build(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 5.0f, 5, humidityPct = null),
            chmu = null
        )
        assertEquals(listOf("Pocitová"), s.secondary.map { it.label })
        assertEquals("5 °C", s.secondary[0].text)
    }

    @Test
    fun onlyChmuFresh_secondaryFromChmu_rainLabelTenMin_rightInfopocasiStale() {
        val s = build(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 12.0f, 120),
            chmu = measurement(
                Sources.STATION_CHMU, 18.0f, 12, humidityPct = 68, windMs = 4.0f, rainMm = 0.2f
            )
        )
        assertEquals("ČHMÚ Holice", s.left.badge)
        assertEquals(Sources.STATION_CHMU, s.left.sourceStation)
        assertEquals(listOf("Pocitová", "Vítr", "Srážky"), s.secondary.map { it.label })
        assertEquals("18 °C", s.secondary[0].text)   // T=18 outside WC and HI domains
        assertEquals("14 km/h", s.secondary[1].text)
        assertEquals("0,2 mm (10 min)", s.secondary[2].text)
        // Right half = the OTHER station (Infopocasi), stale.
        assertEquals("Infopocasi", s.right.name)
        assertTrue(s.right.hasData)
        assertTrue(s.right.isStale)
        assertEquals(now - 120 * minute, s.right.measuredAtMs)
    }

    @Test
    fun rightStationWithoutData_placeholder() {
        val s = build(
            infopocasi = measurement(Sources.STATION_INFOPOCASI, 12.0f, 3),
            chmu = null
        )
        assertEquals("ČHMÚ Holice", s.right.name)
        assertFalse(s.right.hasData)
        assertNull(s.right.temperatureC)
        assertNull(s.right.measuredAtMs)
        assertFalse(s.right.isStale)
    }

    @Test
    fun wideFlag_controlsRightHumidityVisibility() {
        val infopocasi = measurement(Sources.STATION_INFOPOCASI, 21.0f, 5, windMs = 3.0f)
        val chmu = measurement(Sources.STATION_CHMU, 23.0f, 10, humidityPct = 68)
        assertFalse(build(infopocasi, chmu, wide = false).showRightHumidity)
        assertTrue(build(infopocasi, chmu, wide = true).showRightHumidity)
    }

    @Test
    fun offline_emptySecondary_rightPlaceholder() {
        val s = build(null, null)
        assertEquals(WidgetStatus.OFFLINE, s.left.status)
        assertNull(s.left.temperatureC)
        assertTrue(s.secondary.isEmpty())
        assertEquals("ČHMÚ Holice", s.right.name)
        assertFalse(s.right.hasData)
    }

    @Test
    fun secondary_omitsMissingValues_showsOnlyAvailable() {
        // Left station has no temperature (fresh) -> no Pocitová item;
        // wind present, rain null -> exactly one item.
        val s = build(
            infopocasi = measurement(
                Sources.STATION_INFOPOCASI, null, 5, windMs = 3.0f
            ),
            chmu = measurement(Sources.STATION_CHMU, 14.0f, 120)
        )
        assertEquals("Infopocasi", s.left.badge)
        assertNull(s.left.temperatureC)
        assertEquals(listOf("Vítr"), s.secondary.map { it.label })
        assertEquals("11 km/h", s.secondary[0].text)
        // Right half = the stale other station with its value.
        assertEquals("ČHMÚ Holice", s.right.name)
        assertEquals(14.0f, s.right.temperatureC!!, 0.001f)
        assertTrue(s.right.isStale)
    }
}