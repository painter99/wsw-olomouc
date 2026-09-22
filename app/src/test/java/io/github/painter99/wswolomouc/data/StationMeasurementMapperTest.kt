package io.github.painter99.wswolomouc.data

import io.github.painter99.wswolomouc.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for mapping source-specific measurements into the unified
 * [StationMeasurement] (M1.4, customclientraw switch M1.6b-2).
 *
 * PRD data model: wind is stored in m/s (infopocasi customclientraw provides
 * km/h -> /3.6, CHMU provides m/s directly). Pressure exists only for
 * infopocasi (CHMU 10M feed has no P element — verified M1.3).
 */
class StationMeasurementMapperTest {

    private val fetchedAt = 1_758_000_000_000L

    // --- customclientraw mapping -------------------------------------------

    @Test
    fun customclientraw_mapsTemperatureHumidityPressure() {
        val m = baseCustom(temperatureC = 13.6f, humidityPct = 77, pressureHpa = 1023.7f)
        val r = StationMeasurementMapper.fromCustomClientraw(m, fetchedAtMs = fetchedAt)!!
        assertEquals(Sources.STATION_INFOPOCASI, r.station)
        assertEquals(13.6f, r.temperatureC!!, 0.001f)
        assertEquals(77, r.humidityPct)
        assertEquals(1023.7f, r.pressureHpa!!, 0.001f)
        assertEquals(1.0f, r.rainMm!!, 0.001f)
    }

    @Test
    fun customclientraw_windKmhConvertedToMs() {
        val m = baseCustom(windSpeedKmh = 14.5f, windGustKmh = 27.3f)
        val r = StationMeasurementMapper.fromCustomClientraw(m, fetchedAtMs = fetchedAt)!!
        assertEquals(14.5f / 3.6f, r.windMs!!, 0.001f)
        assertEquals(27.3f / 3.6f, r.windGustMs!!, 0.001f)
    }

    @Test
    fun customclientraw_measuredAt_takenFromParser() {
        val m = baseCustom(measuredAtEpochMs = 1_758_001_000_000L)
        val r = StationMeasurementMapper.fromCustomClientraw(m, fetchedAtMs = fetchedAt)!!
        assertEquals(1_758_001_000_000L, r.measuredAtMs)
    }

    @Test
    fun customclientraw_measuredAt_fallsBackToFetchedAtWhenUnparseable() {
        val m = baseCustom(measuredAtEpochMs = null)
        val r = StationMeasurementMapper.fromCustomClientraw(m, fetchedAtMs = fetchedAt)!!
        assertEquals(fetchedAt, r.measuredAtMs)
    }

    @Test
    fun customclientraw_nullFieldsStayNull() {
        val m = baseCustom(
            temperatureC = null, humidityPct = null, pressureHpa = null,
            windSpeedKmh = null, windDirDeg = null, windGustKmh = null, rainTodayMm = null
        )
        val r = StationMeasurementMapper.fromCustomClientraw(m, fetchedAtMs = fetchedAt)!!
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

    private fun baseCustom(
        temperatureC: Float? = 13.6f,
        humidityPct: Int? = 77,
        pressureHpa: Float? = 1023.7f,
        windSpeedKmh: Float? = 14.5f,
        windDirDeg: Int? = 309,
        windGustKmh: Float? = 27.3f,
        rainTodayMm: Float? = 1.0f,
        measuredAtEpochMs: Long? = 1_758_001_000_000L
    ) = CustomClientrawMeasurement(
        temperatureC = temperatureC,
        humidityPct = humidityPct,
        pressureHpa = pressureHpa,
        windSpeedKmh = windSpeedKmh,
        windDirDeg = windDirDeg,
        windGustKmh = windGustKmh,
        rainTodayMm = rainTodayMm,
        measuredAtEpochMs = measuredAtEpochMs
    )
}