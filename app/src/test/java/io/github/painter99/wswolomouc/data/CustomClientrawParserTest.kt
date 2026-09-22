package io.github.painter99.wswolomouc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Parser tests for infopocasi customclientraw.txt (M1.6b-2 data fix).
 *
 * WHY: clientraw.txt field indices are NOT standard on this station
 * (verified 22. 9. 2026: clientraw[1]=7.3 while the real temperature was
 * 13.6 °C, cross-checked against CHMU Holice and the station's own labeled
 * customclientraw.txt; clientraw[12] turned out to be the INDOOR temp).
 * customclientraw.txt carries LABELED keys and declared units, so the app
 * reads it instead of guessing indices.
 *
 * Fixture = real station data captured 2026-09-22 13:44 local time.
 */
class CustomClientrawParserTest {

    private fun fixture(): String =
        javaClass.getResourceAsStream("/customclientraw_fixture.json")!!
            .bufferedReader().use { it.readText() }

    @Test
    fun parse_liveFixture_labeledValues() {
        val m = CustomClientrawParser.parse(fixture())

        assertNotNull(m)
        assertEquals(13.6f, m!!.temperatureC!!, 0.01f)
        assertEquals(77, m.humidityPct)
        assertEquals(1023.7f, m.pressureHpa!!, 0.01f)
        assertEquals(14.5f, m.windSpeedKmh!!, 0.01f)   // wspeed, NOT intemp
        assertEquals(27.3f, m.windGustKmh!!, 0.01f)
        assertEquals(309, m.windDirDeg)
        assertEquals(1.0f, m.rainTodayMm!!, 0.01f)     // rfall (it rained 04:11)
    }

    @Test
    fun parse_measuredAt_fromTimeUTC() {
        val m = CustomClientrawParser.parse(fixture())!!
        // timeUTC "2026,09,22,11,44,48" is UTC
        assertEquals(
            Instant.parse("2026-09-22T11:44:48Z").toEpochMilli(),
            m.measuredAtEpochMs!!
        )
    }

    @Test
    fun parse_wrongTemperatureUnit_returnsNull() {
        val raw = fixture().replace("\"tempunit\":\"C\"", "\"tempunit\":\"F\"")
        assertNull(CustomClientrawParser.parse(raw))
    }

    @Test
    fun parse_wrongWindUnit_returnsNull() {
        val raw = fixture().replace("\"windunit\":\"kmh\"", "\"windunit\":\"mph\"")
        assertNull(CustomClientrawParser.parse(raw))
    }

    @Test
    fun parse_sensorContactLost_returnsNull() {
        val raw = fixture().replace("\"SensorContactLost\":\"0\"", "\"SensorContactLost\":\"1\"")
        assertNull(CustomClientrawParser.parse(raw))
    }

    @Test
    fun parse_garbage_returnsNull() {
        assertNull(CustomClientrawParser.parse("not a customclientraw file"))
        assertNull(CustomClientrawParser.parse(""))
        assertNull(CustomClientrawParser.parse("{\"date\":\"13:44\"}"))
    }

    @Test
    fun parse_minimalFile_missingOptionalKeys_stayNull() {
        val raw = "{\"temp\":\"10.0\",\"tempunit\":\"C\",\"windunit\":\"kmh\"," +
            "\"pressunit\":\"hPa\",\"rainunit\":\"mm\"}"
        val m = CustomClientrawParser.parse(raw)
        assertNotNull(m)
        assertEquals(10.0f, m!!.temperatureC!!, 0.01f)
        assertNull(m.humidityPct)
        assertNull(m.pressureHpa)
        assertNull(m.windSpeedKmh)
        assertNull(m.windGustKmh)
        assertNull(m.windDirDeg)
        assertNull(m.rainTodayMm)
        assertNull(m.measuredAtEpochMs)
    }
}