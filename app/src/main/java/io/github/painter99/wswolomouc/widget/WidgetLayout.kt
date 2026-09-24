package io.github.painter99.wswolomouc.widget

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.StationMeasurement
import io.github.painter99.wswolomouc.ui.FeelsLike
import io.github.painter99.wswolomouc.ui.Format
import io.github.painter99.wswolomouc.ui.TrendDirection

/** One secondary-row entry (PRD F2.6/NF8): label + preformatted value. */
data class SecondaryItem(val label: String, val text: String)

/**
 * Compact per-station block shown on the widget (M1.6b-2 v2). BOTH stations
 * are always present (Pavel 22. 9.: "na widgetu musí být zmíněné obě stanice
 * a u každé čas poslední reálné aktualizace") — a station without data
 * renders as a placeholder (G3), never disappears silently.
 */
data class StationBlock(
    val name: String,
    val temperatureC: Float?,
    val humidityPct: Int?,
    val hasData: Boolean,
    val isStale: Boolean,
    val measuredAtMs: Long?
)

/**
 * Full widget layout state v2 (M1.6b-2, PRD F2.6):
 *  - [left] = synthesis value with F2.5 fallback rules — ALWAYS some value
 *    when any data exists (Pavel 22. 9.: "vždy alespoň nějakou hodnotu"),
 *  - [secondary] = max 3 items (NF8), fixed order Pocitová -> Vítr -> Srážky,
 *    missing values are OMITTED (never faked as 0), temperatures precise,
 *  - [stations] = exactly two blocks, INFOPOCASI first, CHMU second,
 *  - [showStationHumidity] = 5x2 wide mode bonus; compact 4x2 omits it.
 */
data class WidgetLayoutState(
    val left: WidgetState,
    val secondary: List<SecondaryItem>,
    val stations: List<StationBlock>,
    val showStationHumidity: Boolean,
    /**
     * M1.8d (Pavel 24. 9. 2026): hide the single "Měření" time on the left
     * when the value is the average of BOTH stations - one clock time next
     * to an average of two measurements measured minutes apart is misleading.
     * Both per-station times stay on the right (honest per-source age).
     */
    val showLeftAge: Boolean
)

/**
 * Pure layout rules (M1.6b-2 v2), unit-tested in WidgetLayoutTest:
 *
 *  1. Left half is the unchanged F2.5 synthesis; the secondary row takes its
 *     values from the station behind the left badge — with the average badge
 *     ("Ø 2 stanice") the values are the freshness-weighted averages of BOTH
 *     stations (round 6, Pavel 23. 9.; F2.5 wording rules still apply).
 *  2. Both station blocks always appear, each with its own last-measurement
 *     time (honest per-source age).
 *  3. Feels-like uses FeelsLike.calculate(T, windMs * 3.6, RH) — wind is
 *     converted km/h EXACTLY ONCE here (M1.6a.1 contract).
 *  4. Rain is the DAILY total for BOTH sources ("(den)", M1.6b-2
 *     unification — Pavel 22. 9.); the CHMU 10-min value stays in the DB.
 *  5. Temperatures are formatted to one decimal via Format.temperaturePrecise.
 */
object WidgetLayout {

    private val STALE_MS = Sources.STALE_THRESHOLD_MIN * 60_000L

    fun build(
        measurements: Map<String, StationMeasurement>,
        nowMs: Long,
        wide: Boolean,
        trend: TrendDirection? = null
    ): WidgetLayoutState {
        val left = WidgetSynthesis.synthesize(measurements, nowMs).copy(trend = trend)

        // Round 6 (Pavel 23. 9.): with the "Ø 2 stanice" badge the secondary
        // row uses the freshness-weighted average of ALL parameters (wind,
        // gusts, humidity, rain) — not just the primary station's values.
        val source = if (left.badge == "Ø 2 stanice") {
            WidgetSynthesis.weightedMeasurement(measurements, nowMs)
        } else {
            left.sourceStation?.let { measurements[it] }
        }
        val secondary = source?.let { secondaryItems(it) } ?: emptyList()

        val stations = listOf(
            stationBlock(measurements, Sources.STATION_INFOPOCASI, nowMs),
            stationBlock(measurements, Sources.STATION_CHMU, nowMs)
        )

        return WidgetLayoutState(
            left = left,
            secondary = secondary,
            stations = stations,
            showStationHumidity = wide,
            showLeftAge = left.badge != WidgetSynthesis.AVERAGE_BADGE
        )
    }

    private fun stationBlock(
        measurements: Map<String, StationMeasurement>,
        stationId: String,
        nowMs: Long
    ): StationBlock {
        val m = measurements[stationId] ?: return StationBlock(
            name = WidgetSynthesis.shortName(stationId),
            temperatureC = null,
            humidityPct = null,
            hasData = false,
            isStale = false,
            measuredAtMs = null
        )
        return StationBlock(
            name = WidgetSynthesis.shortName(m.station),
            temperatureC = m.temperatureC,
            humidityPct = m.humidityPct,
            hasData = true,
            isStale = nowMs - m.measuredAtMs > STALE_MS,
            measuredAtMs = m.measuredAtMs
        )
    }

    /** Max 3 items (NF8), fixed order: Pocitová, Vítr, Srážky. */
    private fun secondaryItems(m: StationMeasurement): List<SecondaryItem> {
        val items = mutableListOf<SecondaryItem>()
        m.temperatureC?.let { t ->
            val feels = FeelsLike.calculate(t, m.windMs?.times(3.6f), m.humidityPct)
            items += SecondaryItem("Pocitová", Format.temperaturePrecise(feels))
        }
        m.windMs?.let {
            // Gust joined into ONE item (round 2, Pavel 23. 9.) — keeps the
            // row at max 3 items (NF8).
            items += SecondaryItem("Vítr", Format.windRange(m.windMs, m.windGustMs))
        }
        // Rain unified to the DAILY total for both sources (Pavel 22. 9.);
        // the CHMU 10-min value stays in the DB history only.
        m.rainDailyMm?.let { items += SecondaryItem("Srážky", Format.rain(it) + " (den)") }
        return items.take(3)
    }
}