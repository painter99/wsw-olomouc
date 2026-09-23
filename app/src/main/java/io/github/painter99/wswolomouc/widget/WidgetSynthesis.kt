package io.github.painter99.wswolomouc.widget

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.StationMeasurement
import io.github.painter99.wswolomouc.ui.TrendDirection

/** Widget display state (PRD F2.4): OK / STÁRÉ DATA / OFFLINE. */
enum class WidgetStatus { OK, STALE, OFFLINE }

/**
 * Synthesized widget state (M1.6a, PRD F2.5; revised by Pavel 23. 9. 2026).
 *
 * [badge] names the data source: "Ø 2 stanice" when both fresh stations were
 * averaged, otherwise the short name of the single station the value comes
 * from. The average is a spatial estimate for Olomouc — NEVER described as a
 * "more accurate measurement" (F2.5 wording rule).
 *
 * [trend] is the arrow of the DISPLAYED value (3 h window), set by the
 * caller from Room history — the synthesis itself stays a pure
 * map -> state function.
 */
data class WidgetState(
    val temperatureC: Float?,
    val badge: String,
    /** Measurement time of the DISPLAYED value (epoch ms), null when no data. */
    val measuredAtMs: Long?,
    val status: WidgetStatus,
    /**
     * Station the DISPLAYED value comes from: the primary station for the
     * "Ø 2 stanice" badge (F2.5: non-temperature values come from the
     * primary), the single station's id otherwise, null when OFFLINE.
     */
    val sourceStation: String? = null,
    val trend: TrendDirection? = null
)

/**
 * Pure synthesis rules (PRD F2.5 as revised by Pavel 23. 9. 2026), tested in
 * WidgetSynthesisTest:
 *
 *  1. Both stations usable (fresh <= STALE_THRESHOLD_MIN AND temperature
 *     present) -> average WEIGHTED BY FRESHNESS (round 3, Pavel 23. 9.:
 *     w = 1/(age_min + 15) — the fresher station pulls the result towards
 *     itself) + badge "Ø 2 stanice"; displayed age is the OLDER of the two
 *     measurements (honest staleness). The average is a spatial estimate for
 *     Olomouc — NEVER described as a "more accurate measurement" (F2.5
 *     wording rule). The formula is disclosed in the app (transparency).
 *  2. OTHERWISE the PRIMARY station (Infopocasi) always wins as long as it
 *     has any temperature — even a stale one (Pavel: "jinak jede vlevo
 *     infopocasi"); its staleness is shown honestly (F2.4/G6).
 *  3. Only when the primary has no temperature -> newest available value.
 *  4. No data at all -> OFFLINE, no value (G6).
 */
object WidgetSynthesis {

    private val STALE_MS = Sources.STALE_THRESHOLD_MIN * 60_000L

    /**
     * Freshness weight (round 3, Pavel 23. 9.): w = 1/(age_min + 15).
     * The +15 min offset keeps weights finite and bounded (a "just now"
     * measurement weighs 1/15, a 30-min-old one 1/45 — 3x less, never zero).
     * Disclosed in the app (transparency).
     */
    const val WEIGHT_OFFSET_MIN = 15f

    fun weight(ageMin: Float): Float = 1f / (ageMin + WEIGHT_OFFSET_MIN)

    fun synthesize(measurements: Map<String, StationMeasurement>, nowMs: Long): WidgetState {
        val usable = measurements.values.filter {
            it.temperatureC != null && nowMs - it.measuredAtMs <= STALE_MS
        }

        return when {
            usable.size >= 2 -> {
                val weights = usable.map { weight((nowMs - it.measuredAtMs) / 60_000f) }
                val weighted = usable
                    .map { it.temperatureC!! }
                    .zip(weights) { t, w -> t * w }
                    .sum() / weights.sum()
                WidgetState(
                    temperatureC = weighted,
                    badge = "Ø 2 stanice",
                    measuredAtMs = usable.minOf { it.measuredAtMs },
                    status = WidgetStatus.OK,
                    // Non-temperature values come from the PRIMARY station (F2.5).
                    sourceStation = Sources.STATION_INFOPOCASI
                )
            }
            else -> {
                val primary = measurements[Sources.STATION_INFOPOCASI]
                    ?.takeIf { it.temperatureC != null }
                val chosen = primary
                    ?: measurements.values
                        .filter { it.temperatureC != null }
                        .maxByOrNull { it.measuredAtMs }
                when (chosen) {
                    null -> WidgetState(
                        temperatureC = null,
                        badge = "—",
                        measuredAtMs = null,
                        status = WidgetStatus.OFFLINE
                    )
                    else -> WidgetState(
                        temperatureC = chosen.temperatureC,
                        badge = shortName(chosen.station),
                        measuredAtMs = chosen.measuredAtMs,
                        status = if (nowMs - chosen.measuredAtMs <= STALE_MS) {
                            WidgetStatus.OK
                        } else {
                            WidgetStatus.STALE
                        },
                        sourceStation = chosen.station
                    )
                }
            }
        }
    }

    /**
     * Freshness-weighted average of ALL displayed parameters (round 6, Pavel
     * 23. 9.): wind, gusts, humidity and daily rain are averaged with the
     * same w = 1/(age_min + 15) rule as the temperature. Each field is
     * averaged over the stations that HAVE it; a missing field stays null
     * (never faked as 0). Pressure is never averaged (only INFOPOCASI has
     * it). Returns null when the "Ø 2 stanice" rules do not apply.
     */
    fun weightedMeasurement(
        measurements: Map<String, StationMeasurement>,
        nowMs: Long
    ): StationMeasurement? {
        val usable = measurements.values.filter {
            it.temperatureC != null && nowMs - it.measuredAtMs <= STALE_MS
        }
        if (usable.size < 2) return null
        val weights = usable.map { weight((nowMs - it.measuredAtMs) / 60_000f) }

        fun weightedOf(select: (StationMeasurement) -> Float?): Float? {
            val valid = usable.zip(weights) { m, w -> select(m)?.let { it to w } }
                .filterNotNull()
            if (valid.isEmpty()) return null
            val wSum = valid.sumOf { it.second.toDouble() }
            return (valid.sumOf { (v, w) -> v * w.toDouble() } / wSum).toFloat()
        }

        return StationMeasurement(
            station = Sources.STATION_INFOPOCASI,
            temperatureC = weightedOf { it.temperatureC },
            humidityPct = weightedOf { it.humidityPct?.toFloat() }?.toInt(),
            pressureHpa = null,
            windMs = weightedOf { it.windMs },
            windGustMs = weightedOf { it.windGustMs },
            windDirDeg = null,
            rainMm = null,
            rainDailyMm = weightedOf { it.rainDailyMm },
            measuredAtMs = usable.minOf { it.measuredAtMs },
            fetchedAtMs = nowMs
        )
    }

    /** Short badge names — full names ("Infopocasi Olomouc") do not fit 4×2. */
    fun shortName(station: String): String = when (station) {
        Sources.STATION_INFOPOCASI -> "Infopocasi"
        Sources.STATION_CHMU -> "ČHMÚ Holice"
        else -> station
    }
}