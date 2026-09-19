package io.github.painter99.wswolomouc.data

/**
 * Parsed measurement from a WeatherDisplay clientraw.txt file.
 * All values are nullable — a missing or invalid field should not crash the parser.
 */
data class ClientrawMeasurement(
    val temperatureC: Float?,
    val humidityPct: Int?,
    val pressureHpa: Float?,
    val windSpeedKmh: Float?,
    val windDirDeg: Int?,
    val windGustKmh: Float?,
    val rainTodayMm: Float?,
    val measuredTime: String?,   // "HH:MM:SS" extracted from station name field
    val measuredDate: String?,   // "DD/M/YYYY"
    val stationName: String?     // station identifier (without time suffix)
)
