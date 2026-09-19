package io.github.painter99.wswolomouc.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClientrawParserTest {

    private val fixture: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("clientraw_fixture.txt")!!
            .readBytes().decodeToString()
    }

    @Test
    fun parse_fixture_returnsNonNull() {
        val result = ClientrawParser.parse(fixture)
        assertNotNull(result, "Parser should return non-null for valid fixture")
    }

    @Test
    fun parse_temperatureC_matchesFixture() {
        val result = ClientrawParser.parse(fixture)!!
        assertEquals(2.7f, result.temperatureC!!, 0.01f)
    }

    @Test
    fun parse_humidity_matchesFixture() {
        val result = ClientrawParser.parse(fixture)!!
        assertEquals(85, result.humidityPct)
    }

    @Test
    fun parse_pressure_matchesFixture() {
        val result = ClientrawParser.parse(fixture)!!
        assertEquals(1022.8f, result.pressureHpa!!, 0.01f)
    }

    @Test
    fun parse_windSpeed_matchesFixture() {
        val result = ClientrawParser.parse(fixture)!!
        assertEquals(21.9f, result.windSpeedKmh!!, 0.01f)
    }

    @Test
    fun parse_windDirection_matchesFixture() {
        val result = ClientrawParser.parse(fixture)!!
        assertEquals(54, result.windDirDeg)
    }

    @Test
    fun parse_rainToday_matchesFixture() {
        val result = ClientrawParser.parse(fixture)!!
        assertEquals(0.0f, result.rainTodayMm!!, 0.001f)
    }

    @Test
    fun parse_measuredTime_extractedFromStationName() {
        val result = ClientrawParser.parse(fixture)!!
        assertEquals("10:08:27", result.measuredTime)
    }

    @Test
    fun parse_stationName_withoutTimeSuffix() {
        val result = ClientrawParser.parse(fixture)!!
        assertEquals("Meteo_Olomouc_CZ", result.stationName)
    }

    @Test
    fun parse_date_matchesFixture() {
        val result = ClientrawParser.parse(fixture)!!
        assertEquals("19/9/2026", result.measuredDate)
    }

    @Test
    fun parse_invalidHeader_returnsNull() {
        val invalid = fixture.replace("12345", "99999")
        assertNull(ClientrawParser.parse(invalid))
    }

    @Test
    fun parse_tooFewFields_returnsNull() {
        assertNull(ClientrawParser.parse("12345 2.7 5.2"))
    }

    @Test
    fun parse_emptyString_returnsNull() {
        assertNull(ClientrawParser.parse(""))
    }
}
