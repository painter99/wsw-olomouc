package io.github.painter99.wswolomouc.widget

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.StationMeasurement

/** Widget display state (PRD F2.4): OK / STÁRÉ DATA / OFFLINE. */
enum class WidgetStatus { OK, STALE, OFFLINE }

/**
 * Synthesized widget state (M1.6a, PRD F2.5).
 *
 * [badge] names the data source: "Ø 2 stanice" when both fresh stations were
 * averaged, otherwise the short name of the single station the value comes
 * from. The average is a spatial estimate for Olomouc — NEVER described as a
 * "more accurate measurement" (F2.5 wording rule).
 */
data class WidgetState(
    val temperatureC: Float?,
    val badge: String,
    /** Measurement time of the DISPLAYED value (epoch ms), null when no data. */
    val measuredAtMs: Long?,
    val status: WidgetStatus,
    /**
     * Station the DISPLAYED value comes from (M1.6b-2): the averaged primary
     * station for the "Ø 2 stanice" badge (F2.5: non-temperature values come
     * from the primary), the single station's id otherwise, null when OFFLINE.
     */
    val sourceStation: String? = null
)

/**
 * Pure synthesis rules (PRD F2.5), unit-tested in WidgetSynthesisTest:
 *
 *  1. Both stations usable (fresh <= STALE_THRESHOLD_MIN AND temperature
 *     present) -> arithmetic mean + badge "Ø 2 stanice"; displayed age is the
 *     OLDER of the two measurements (honest staleness).
 *  2. Exactly one usable -> its value + its badge.
 *  3. No usable station but some data exists -> newest available value,
 *     status STALE (F2.4 "STÁRÉ DATA"; the PRD's "both old -> OFFLINE" is
 *     realized as not-OK: the widget keeps showing the last known value with
 *     its age per G6, and the gray/orange dot makes the state explicit).
 *  4. No data at all -> OFFLINE, no value (G6).
 */
object WidgetSynthesis {

    private val STALE_MS = Sources.STALE_THRESHOLD_MIN * 60_000L

    fun synthesize(measurements: Map<String, StationMeasurement>, nowMs: Long): WidgetState {
        val usable = measurements.values.filter {
            it.temperatureC != null && nowMs - it.measuredAtMs <= STALE_MS
        }

        return when {
            usable.size >= 2 -> {
                val avg = usable.map { it.temperatureC!! }.average().toFloat()
                WidgetState(
                    temperatureC = avg,
                    badge = "Ø 2 stanice",
                    measuredAtMs = usable.minOf { it.measuredAtMs },
                    status = WidgetStatus.OK,
                    // Non-temperature values come from the PRIMARY station (F2.5).
                    sourceStation = Sources.STATION_INFOPOCASI
                )
            }
            usable.size == 1 -> {
                val m = usable.single()
                WidgetState(
                    temperatureC = m.temperatureC,
                    badge = shortName(m.station),
                    measuredAtMs = m.measuredAtMs,
                    status = WidgetStatus.OK,
                    sourceStation = m.station
                )
            }
            measurements.isNotEmpty() -> {
                val newest = measurements.values.maxBy { it.measuredAtMs }
                val fresh = nowMs - newest.measuredAtMs <= STALE_MS
                WidgetState(
                    temperatureC = newest.temperatureC,
                    badge = shortName(newest.station),
                    measuredAtMs = newest.measuredAtMs,
                    status = if (fresh) WidgetStatus.OK else WidgetStatus.STALE,
                    sourceStation = newest.station
                )
            }
            else -> WidgetState(
                temperatureC = null,
                badge = "—",
                measuredAtMs = null,
                status = WidgetStatus.OFFLINE
            )
        }
    }

    /** Short badge names — full names ("Infopocasi Olomouc") do not fit 4×2. */
    fun shortName(station: String): String = when (station) {
        Sources.STATION_INFOPOCASI -> "Infopocasi"
        Sources.STATION_CHMU -> "ČHMÚ Holice"
        else -> station
    }
}
