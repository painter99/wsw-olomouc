package io.github.painter99.wswolomouc.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Parser for CHMU OpenData 10M daily JSON (PRD F1.2).
 *
 * Structure (live-verified 2026-09-19):
 *   root.data.data.values = [["0-203-0-11742","T","2026-09-19T20:00:00Z",14.5,"",5.0], ...]
 *   value row = [STATION, ELEMENT, DT(ISO-8601 UTC), VAL, FLAG, QUALITY]
 *
 * Elements used (units from meta2-*):
 *   T (°C), H (%), F (m/s), Fmax (m/s), D (deg), SRA10M (mm)
 * Note: the 10M feed contains NO pressure element.
 */
object ChmuJsonParser {

    private const val EL_TEMP = "T"
    private const val EL_HUMIDITY = "H"
    private const val EL_WIND = "F"
    private const val EL_WIND_GUST = "Fmax"
    private const val EL_WIND_DIR = "D"
    private const val EL_RAIN = "SRA10M"

    /**
     * Parses the daily JSON and returns the measurement with the latest
     * timestamp for [stationCode], or null if nothing usable is found.
     */
    fun parse(raw: String, stationCode: String): ChmuMeasurement? {
        val values = try {
            JSONObject(raw)
                .getJSONObject("data")
                .getJSONObject("data")
                .getJSONArray("values")
        } catch (e: Exception) {
            return null
        }

        // Latest value per element + the newest DT seen for the station.
        val latestByElement = HashMap<String, Pair<Instant, Float>>()
        var newestDt: Instant? = null
        val rainRows = ArrayList<Pair<Instant, Float>>()

        for (i in 0 until values.length()) {
            val row = values.optJSONArray(i) ?: continue
            if (row.length() < 4) continue
            if (row.optString(0) != stationCode) continue

            val dt = parseDt(row.optString(2)) ?: continue
            if (newestDt == null || dt.isAfter(newestDt)) newestDt = dt

            val element = row.optString(1)
            if (element in SUPPORTED) {
                val value = row.optDouble(3, Double.NaN)
                if (!value.isNaN()) {
                    if (element == EL_RAIN) {
                        // The daily file holds every 10-minute SRA10M row
                        // of the UTC day — the local-day total is filtered
                        // below (M1.7b, Pavel 24. 9.).
                        rainRows += dt to value.toFloat()
                    }
                    val existing = latestByElement[element]
                    if (existing == null || dt.isAfter(existing.first)) {
                        latestByElement[element] = dt to value.toFloat()
                    }
                }
            }
        }

        newestDt ?: return null

        // "Uhrn za den" = sum of the SRA10M rows belonging to the LOCAL
        // (Europe/Prague) calendar day of the newest row — od pulnoci, same
        // definition as the Infopocasi station's own daily total (M1.7b,
        // Pavel 24. 9. 2026). The UTC file spans 02:00-01:50 local, so a
        // plain sum of the whole file would mix two local days.
        val localDay = newestDt.atZone(PRAGUE).toLocalDate()
        val rainDaily =
            if (rainRows.isEmpty()) null
            else rainRows
                .filter { it.first.atZone(PRAGUE).toLocalDate() == localDay }
                .sumOf { it.second.toDouble() }.toFloat()

        return ChmuMeasurement(
            stationCode = stationCode,
            measuredAtEpochMs = newestDt.toEpochMilli(),
            temperatureC = latestByElement[EL_TEMP]?.second,
            humidityPct = latestByElement[EL_HUMIDITY]?.second?.toInt(),
            windSpeedMs = latestByElement[EL_WIND]?.second,
            windGustMs = latestByElement[EL_WIND_GUST]?.second,
            windDirDeg = latestByElement[EL_WIND_DIR]?.second?.toInt(),
            rain10mMm = latestByElement[EL_RAIN]?.second,
            rainDailyMm = rainDaily
        )
    }

    private val SUPPORTED = setOf(EL_TEMP, EL_HUMIDITY, EL_WIND, EL_WIND_GUST, EL_WIND_DIR, EL_RAIN)

    /** Local day zone for the daily rain total (same day the station resets). */
    private val PRAGUE = ZoneId.of("Europe/Prague")

    private fun parseDt(raw: String): Instant? = try {
        Instant.parse(raw)
    } catch (e: Exception) {
        null
    }
}
