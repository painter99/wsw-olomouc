package io.github.painter99.wswolomouc.data

/**
 * Parsed measurement from infopocasi customclientraw.txt (M1.6b-2 data fix).
 *
 * customclientraw.txt is a FLAT WeatherDisplay JSON with labeled keys and
 * declared units — robust against index shifts that broke clientraw.txt
 * parsing on this station (verified 22. 9. 2026: clientraw[1]=7.3 vs real
 * 13.6 °C; clientraw[12] = indoor temp).
 */
data class CustomClientrawMeasurement(
    val temperatureC: Float?,
    val humidityPct: Int?,
    val pressureHpa: Float?,
    val windSpeedKmh: Float?,
    val windDirDeg: Int?,
    val windGustKmh: Float?,
    val rainTodayMm: Float?,
    val measuredAtEpochMs: Long?
)

/**
 * Parser for infopocasi customclientraw.txt (flat JSON, no external JSON
 * dependency — unit-testable, sandbox/CI friendly).
 *
 * Honesty guards (a unit or sensor mismatch must FAIL the fetch, never
 * render silently wrong values):
 *  - tempunit == "C", windunit == "kmh", pressunit == "hPa", rainunit == "mm"
 *  - SensorContactLost, when present, must be "0"
 *  - "temp" must parse — a file without it is not a current-conditions file
 *
 * Measurement time comes from `timeUTC` ("Y,M,D,H,M,S", UTC); null when
 * unparseable (the mapper then falls back to the fetch time).
 */
object CustomClientrawParser {

    fun parse(raw: String): CustomClientrawMeasurement? {
        if (raw.isBlank()) return null
        if (scalar(raw, "tempunit") != "C") return null
        if (scalar(raw, "windunit") != "kmh") return null
        if (scalar(raw, "pressunit") != "hPa") return null
        if (scalar(raw, "rainunit") != "mm") return null
        val sensorLost = scalar(raw, "SensorContactLost")
        if (sensorLost != null && sensorLost != "0") return null
        val temp = scalar(raw, "temp")?.toFloatOrNull() ?: return null

        return CustomClientrawMeasurement(
            temperatureC = temp,
            humidityPct = scalar(raw, "hum")?.toFloatOrNull()?.toInt(),
            pressureHpa = scalar(raw, "press")?.toFloatOrNull(),
            windSpeedKmh = scalar(raw, "wspeed")?.toFloatOrNull(),
            windDirDeg = scalar(raw, "bearing")?.toFloatOrNull()?.toInt(),
            windGustKmh = scalar(raw, "wgust")?.toFloatOrNull(),
            rainTodayMm = scalar(raw, "rfall")?.toFloatOrNull(),
            measuredAtEpochMs = timeUtcMs(raw)
        )
    }

    /**
     * Extracts a scalar value for [key] from the flat JSON, handling both
     * quoted strings and bare numbers. A closing quote in the key pattern
     * prevents prefix collisions ("temp" does not match "tempTL").
     */
    private fun scalar(raw: String, key: String): String? {
        val m = Regex("\"$key\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}\\]]+))").find(raw)
            ?: return null
        val value = m.groupValues[1].ifEmpty { m.groupValues[2] }
        return value.trim().takeIf { it.isNotEmpty() }
    }

    /** "2026,09,22,11,44,48" (UTC) -> epoch ms, null on malformed input. */
    private fun timeUtcMs(raw: String): Long? {
        val parts = scalar(raw, "timeUTC")?.split(",") ?: return null
        if (parts.size != 6) return null
        val nums = parts.map { it.trim().toIntOrNull() }
        if (nums.any { it == null }) return null
        val (y, mo, d, h, mi, s) = nums.map { it!! }
        return try {
            java.time.LocalDateTime.of(y, mo, d, h, mi, s)
                .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}