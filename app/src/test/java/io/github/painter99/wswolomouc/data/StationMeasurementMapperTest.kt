package io.github.painter99.wswolomouc.data

import io.github.painter99.wswolomouc.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tests for mapping source-specific measurements into the unified
 * [StationMeasurement] (M1.4).
 *
 * PRD data model: wind is stored in m/s (clientraw provides km/h -> /3.6,
 * CHMU provides m/s directly). Pressure exists only for infopocasi (CHMU 10M
 * feed has no P element — verified M1.3).
 */
class StationMeasurementMapperTest {

    private val fetchedAt = 1_758_000_000_000L

    // --- clientraw mapping -------------------------------------------------

    @Test
    fun clientraw_mapsTemperatureHumidityPressure() {
        val m = ClientrawMeasurement(
            temperatureC = 18.4f, humidityPct = 62, pressureHpa = 1013.2f,
            windSpeedKmh = 10.0f, windDirDeg = 270, windGustKmh = 30.0f,
            rainTodayMm = 1.5f, measuredTime = "10:08:27",
            measuredDate = "19/9/2026", stationName = "Meteo_Olomouc_CZ"
        )
        val r = StationMeasurementMapper.fromClientraw(m, fetchedAtMs = fetchedAt)!!
        assertEquals(Sources.STATION_INFOPOCASI, r.station)
        assertEquals(18.4f, r.temperatureC!!, 0.001f)
        assertEquals(62, r.humidityPct)
        assertEquals(1013.2f, r.pressureHpa!!, 0.001f)
    }

    @Test
    fun clientraw_windKmhConvertedToMs() {
        val m = ClientrawMeasurement(
            temperatureC = 18.4f, humidityPct = 62, pressureHpa = 1013.2f,
            windSpeedKmh = 10.0f, windDirDeg = 270, windGustKmh = 36.0f,
            rainTodayMm = 1.5f, measuredTime = "10:08:27",
            measuredDate = "19/9/2026", stationName = "Meteo_Olomouc_CZ"
        )
        val r = StationMeasurementMapper.fromClientraw(m, fetchedAtMs = fetchedAt)!!
        assertEquals(10.0f / 3.6f, r.windMs!!, 0.001f)
        assertEquals(36.0f / 3.6f, r.windGustMs!!, 0.001f)
    }

    @Test
    fun clientraw_measuredAt_parsedInPragueSummerTime() {
        val m = baseClientraw(measuredTime = "10:08:27", measuredDate = "19/9/2026")
        val r = StationMeasurementMapper.fromClientraw(m, fetchedAtMs = fetchedAt)!!
        // 19. 9. 2026 10:08:27 Europe/Prague (CEST, UTC+2) == 08:08:27 UTC
        val expected = LocalDateTime.of(2026, 9, 19, 10, 8, 27)
            .atZone(ZoneId.of("Europe/Prague")).toInstant().toEpochMilli()
        assertEquals(expected, r.measuredAtMs)
    }

    @Test
    fun clientraw_measuredAt_fallsBackToFetchedAtWhenUnparseable() {
        val m = baseClientraw(measuredTime = null, measuredDate = null)
        val r = StationMeasurementMapper.fromClientraw(m, fetchedAtMs = fetchedAt)!!
        assertEquals(fetchedAt, r.measuredAtMs)
    }

    @Test
    fun clientraw_nullFieldsStayNull() {
        val m = baseClientraw()
            .copy(temperatureC = null, humidityPct = null, pressureHpa = null,
                windSpeedKmh = null, windDirDeg = null, windGustKmh = null, rainTodayMm = null)
        val r = StationMeasurementMapper.fromClientraw(m, fetchedAtMs = fetchedAt)!!
        assertNull(r.temperatureC)
        assertNull(r.humidityPct)
        assertNull(r.pressureHpa)
        assertNull(r.windMs)
        assertNull(r.windGustMs)
        assertNull(r.windDirDeg)
        assertNull(r.rainMm)
    }

    // --- CHMU mapping -------------------------------------------------------

    @Test
    fun chmu_windStaysMs_pressureStaysNull() {
        val m = ChmuMeasurement(
            stationCode = Sources.CHMU_STATION_CODE,
            measuredAtEpochMs = 1_758_001_000_000L,
            temperatureC = 14.5f, humidityPct = 70,
            windSpeedMs = 3.2f, windGustMs = 8.1f, windDirDeg = 225, rain10mMm = 0.3f
        )
        val r = StationMeasurementMapper.fromChmu(m, fetchedAtMs = fetchedAt)!!
        assertEquals(Sources.STATION_CHMU, r.station)
        assertEquals(3.2f, r.windMs!!, 0.001f)      // m/s kept as-is
        assertEquals(8.1f, r.windGustMs!!, 0.001f)
        assertNull(r.pressureHpa)                    // 10M feed has no pressure
        assertEquals(14.5f, r.temperatureC!!, 0.001f)
        assertEquals(0.3f, r.rainMm!!, 0.001f)       // semantics: last 10 min
    }

    @Test
    fun chmu_measuredAt_takenFromParser() {
        val m = ChmuMeasurement(
            stationCode = Sources.CHMU_STATION_CODE,
            measuredAtEpochMs = 1_758_001_000_000L,
            temperatureC = 14.5f, humidityPct = 70, windSpeedMs = null,
            windGustMs = null, windDirDeg = null, rain10mMm = null
        )
        val r = StationMeasurementMapper.fromChmu(m, fetchedAtMs = fetchedAt)!!
        assertEquals(1_758_001_000_000L, r.measuredAtMs)
    }

    // --- helpers ------------------------------------------------------------

    private fun baseClientraw(
        measuredTime: String? = "10:08:27",
        measuredDate: String? = "19/9/2026"
    ) = ClientrawMeasurement(
        temperatureC = 18.4f, humidityPct = 62, pressureHpa = 1013.2f,
        windSpeedKmh = 10.0f, windDirDeg = 270, windGustKmh = 30.0f,
        rainTodayMm = 1.5f, measuredTime = measuredTime,
        measuredDate = measuredDate, stationName = "Meteo_Olomouc_CZ"
    )
}
