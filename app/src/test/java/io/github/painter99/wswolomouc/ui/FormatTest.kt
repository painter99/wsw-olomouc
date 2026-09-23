package io.github.painter99.wswolomouc.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Value formatting tests (M1.5) — Czech decimal comma, "—" for null.
 */
class FormatTest {

    @Test
    fun temperature() {
        assertEquals("21 °C", Format.temperature(21.4f))
        assertEquals("—", Format.temperature(null))
    }

    @Test
    fun temperaturePrecise_oneDecimalCzechComma() {
        // Pavel 22. 9.: "aby se teplota ukazovala detailněji, tedy na desetiny"
        assertEquals("13,6 °C", Format.temperaturePrecise(13.6f))
        assertEquals("5,0 °C", Format.temperaturePrecise(5.0f))
        assertEquals("-2,3 °C", Format.temperaturePrecise(-2.26f))
        assertEquals("—", Format.temperaturePrecise(null))
    }

    @Test
    fun humidity() {
        assertEquals("55 %", Format.humidity(55))
        assertEquals("—", Format.humidity(null))
    }

    @Test
    fun pressure() {
        assertEquals("1013 hPa", Format.pressure(1013.4f))
        assertEquals("—", Format.pressure(null))
    }

    @Test
    fun wind_msToKmh() {
        assertEquals("9 km/h", Format.wind(2.5f))
        assertEquals("—", Format.wind(null))
    }

    @Test
    fun rain_czechDecimalComma() {
        assertEquals("1,3 mm", Format.rain(1.25f))
        assertEquals("0,0 mm", Format.rain(0f))
        assertEquals("—", Format.rain(null))
    }

    @Test
    fun windRange_windAndGust_singleUnit() {
        // Pavel 23. 9. (round 2): gusts joined into the wind item on the
        // widget — "Vítr 11–18 km/h" keeps the secondary row at 3 items (NF8).
        assertEquals("11–18 km/h", Format.windRange(3.0f, 5.0f))
        assertEquals("11 km/h", Format.windRange(3.0f, null))
        assertEquals("—", Format.windRange(null, 5.0f))
    }
}
