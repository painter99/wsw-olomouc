package io.github.painter99.wswolomouc.data

import io.github.painter99.wswolomouc.Sources
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Unified measurement from one station, independent of the wire format (M1.4).
 *
 * Units follow the PRD data model: wind in m/s, pressure in hPa, rain in mm.
 * Rain semantics differ per source and MUST be surfaced in the UI:
 *  - INFOPOCASI: daily total (clientraw field [10])
 *  - CHMU_HOLICE: accumulation of the last 10 minutes (element SRA10M)
 *
 * Pressure is present only for INFOPOCASI — the CHMU 10M feed has no P
 * element (verified M1.3, 2026-09-19).
 */
data class StationMeasurement(
    val station: String,          // Sources.STATION_INFOPOCASI | STATION_CHMU
    val temperatureC: Float?,
    val humidityPct: Int?,
    val pressureHpa: Float?,
    val windMs: Float?,
    val windGustMs: Float?,
    val windDirDeg: Int?,
    val rainMm: Float?,
    val measuredAtMs: Long,       // measurement time from the source (epoch ms UTC)
    val fetchedAtMs: Long         // fetch time (epoch ms UTC)
)

/**
 * Mapping from source-specific parser results to [StationMeasurement].
 */
object StationMeasurementMapper {

    /** Station local timezone — both Olomouc sources report local wall time. */
    private val PRAGUE: ZoneId = ZoneId.of("Europe/Prague")

    private const val KMH_PER_MS = 3.6f

    /**
     * Maps a parsed clientraw.txt measurement. Wind arrives in km/h
     * (clientraw [12]/[14], unit not yet cross-checked against the station
     * website) and is converted to m/s. The measurement time is parsed from
     * station-local date [74] + time [32]; if unparseable, the fetch time is
     * used as a conservative fallback.
     */
    fun fromClientraw(
        m: ClientrawMeasurement,
        stationId: String = Sources.STATION_INFOPOCASI,
        fetchedAtMs: Long
    ): StationMeasurement = StationMeasurement(
        station = stationId,
        temperatureC = m.temperatureC,
        humidityPct = m.humidityPct,
        pressureHpa = m.pressureHpa,
        windMs = m.windSpeedKmh?.div(KMH_PER_MS),
        windGustMs = m.windGustKmh?.div(KMH_PER_MS),
        windDirDeg = m.windDirDeg,
        rainMm = m.rainTodayMm,
        measuredAtMs = clientrawMeasuredAtMs(m.measuredDate, m.measuredTime) ?: fetchedAtMs,
        fetchedAtMs = fetchedAtMs
    )

    /**
     * Maps a parsed CHMU 10M measurement. Wind is already m/s; pressure stays
     * null (no P element in the 10M feed).
     */
    fun fromChmu(m: ChmuMeasurement, fetchedAtMs: Long): StationMeasurement = StationMeasurement(
        station = Sources.STATION_CHMU,
        temperatureC = m.temperatureC,
        humidityPct = m.humidityPct,
        pressureHpa = null,
        windMs = m.windSpeedMs,
        windGustMs = m.windGustMs,
        windDirDeg = m.windDirDeg,
        rainMm = m.rain10mMm,
        measuredAtMs = m.measuredAtEpochMs,
        fetchedAtMs = fetchedAtMs
    )

    /**
     * Combines clientraw date ("19/9/2026") and time ("10:08:27") into an
     * epoch-ms instant in Europe/Prague. Returns null on malformed input.
     */
    fun clientrawMeasuredAtMs(date: String?, time: String?): Long? {
        if (date.isNullOrBlank() || time.isNullOrBlank()) return null
        return try {
            val d = LocalDate.parse(date, DateTimeFormatter.ofPattern("d/M/u"))
            val t = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss"))
            LocalDateTime.of(d, t).atZone(PRAGUE).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
