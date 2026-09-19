package io.github.painter99.wswolomouc.data

/**
 * Parsed latest measurement from a CHMU OpenData 10M daily JSON file.
 * All values are nullable — a missing element must not crash the parser.
 *
 * Element units verified against meta2-* metadata (2026-09-19):
 *   T = °C, H = %, F = m/s, Fmax = m/s, D = degrees, SRA10M = mm
 */
data class ChmuMeasurement(
    val stationCode: String,
    val measuredAtEpochMs: Long,   // DT of the latest record (UTC)
    val temperatureC: Float?,
    val humidityPct: Int?,
    val windSpeedMs: Float?,
    val windGustMs: Float?,
    val windDirDeg: Int?,
    val rain10mMm: Float?
)
