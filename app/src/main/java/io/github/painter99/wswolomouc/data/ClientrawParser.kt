package io.github.painter99.wswolomouc.data

/**
 * Parser for WeatherDisplay clientraw.txt format.
 *
 * Format: space-separated values, header "12345" at position 0.
 * ~150+ fields; we extract only the ones we need.
 *
 * Key positions (0-indexed):
 *  [0]  header "12345"
 *  [1]  temperature °C
 *  [5]  humidity %
 *  [6]  pressure hPa
 *  [10] rain today mm
 *  [12] wind speed (km/h)
 *  [13] wind direction °
 *  [14] wind gust (km/h)
 *  [32] station name with embedded time "Name-HH:MM:SS"
 *  [74] date "DD/M/YYYY"
 */
object ClientrawParser {

    private const val EXPECTED_HEADER = "12345"
    private const val MIN_FIELDS = 75

    // Field indices (0-based)
    private const val IDX_HEADER = 0
    private const val IDX_TEMP_C = 1
    private const val IDX_HUMIDITY = 5
    private const val IDX_PRESSURE = 6
    private const val IDX_RAIN_TODAY = 10
    private const val IDX_WIND_SPEED = 12
    private const val IDX_WIND_DIR = 13
    private const val IDX_WIND_GUST = 14
    private const val IDX_STATION_TIME = 32
    private const val IDX_DATE = 74

    /**
     * Parses a raw clientraw.txt line into a [ClientrawMeasurement].
     * Returns null if the data is invalid (wrong header, too few fields).
     */
    fun parse(raw: String): ClientrawMeasurement? {
        val fields = raw.trim().split(" ")

        if (fields.size < MIN_FIELDS) return null
        if (fields[IDX_HEADER] != EXPECTED_HEADER) return null

        return ClientrawMeasurement(
            temperatureC = parseFloat(fields, IDX_TEMP_C),
            humidityPct = parseInt(fields, IDX_HUMIDITY),
            pressureHpa = parseFloat(fields, IDX_PRESSURE),
            windSpeedKmh = parseFloat(fields, IDX_WIND_SPEED),
            windDirDeg = parseInt(fields, IDX_WIND_DIR),
            windGustKmh = parseFloat(fields, IDX_WIND_GUST),
            rainTodayMm = parseFloat(fields, IDX_RAIN_TODAY),
            measuredTime = extractTime(fields, IDX_STATION_TIME),
            measuredDate = fields.getOrNull(IDX_DATE)?.takeIf { it.contains("/") },
            stationName = extractStationName(fields, IDX_STATION_TIME)
        )
    }

    private fun parseFloat(fields: List<String>, index: Int): Float? =
        fields.getOrNull(index)?.toFloatOrNull()

    private fun parseInt(fields: List<String>, index: Int): Int? =
        fields.getOrNull(index)?.toIntOrNull()

    /**
     * Extracts time "HH:MM:SS" from station name field like "Meteo_Olomouc_CZ-10:08:27".
     */
    private fun extractTime(fields: List<String>, index: Int): String? {
        val value = fields.getOrNull(index) ?: return null
        // Pattern: anything followed by -HH:MM:SS
        val match = Regex("-(\\d{2}:\\d{2}:\\d{2})$").find(value)
        return match?.groupValues?.get(1)
    }

    /**
     * Extracts station name without the time suffix.
     * "Meteo_Olomouc_CZ-10:08:27" → "Meteo_Olomouc_CZ"
     */
    private fun extractStationName(fields: List<String>, index: Int): String? {
        val value = fields.getOrNull(index) ?: return null
        return value.substringBeforeLast("-").takeIf { it.isNotEmpty() }
    }
}
