package io.github.painter99.wswolomouc.widget

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.StationMeasurement
import io.github.painter99.wswolomouc.ui.FeelsLike
import io.github.painter99.wswolomouc.ui.Format

/** One secondary-row entry (PRD F2.6/NF8): label + preformatted value. */
data class SecondaryItem(val label: String, val text: String)

/** Compact state of the OTHER station (right half of the widget, M1.6b-2). */
data class SideStation(
    val name: String,
    val temperatureC: Float?,
    val humidityPct: Int?,
    /** false = placeholder ("bez dat", gray dot) — G3, never silently hidden. */
    val hasData: Boolean,
    val isStale: Boolean,
    val measuredAtMs: Long?
)

/**
 * Full widget layout state (M1.6b-2, PRD F2.6):
 *  - [left] = existing synthesis (F2.5, rules unchanged),
 *  - [secondary] = max 3 items (NF8), fixed order Pocitová -> Vítr -> Srážky,
 *    missing values are OMITTED (never faked as 0),
 *  - [right] = the OTHER station, compact,
 *  - [showRightHumidity] = 5x2 wide mode bonus; compact 4x2 does not show it.
 */
data class WidgetLayoutState(
    val left: WidgetState,
    val secondary: List<SecondaryItem>,
    val right: SideStation,
    val showRightHumidity: Boolean
)

/**
 * Pure layout rules (M1.6b-2), unit-tested in WidgetLayoutTest:
 *
 *  1. Left half is the unchanged F2.5 synthesis; the secondary row takes its
 *     values from the station behind the left badge — with the average badge
 *     ("Ø 2 stanice") non-temperature values come from the PRIMARY station
 *     (F2.5: synthesis applies to temperature only).
 *  2. The right half shows the OTHER station relative to that source; with
 *     no source (OFFLINE) the right half is the secondary station CHMU.
 *  3. Feels-like uses FeelsLike.calculate(T, windMs * 3.6, RH) — wind is
 *     converted km/h EXACTLY ONCE here (M1.6a.1 contract: Format.wind takes
 *     m/s, FeelsLike takes km/h).
 *  4. Rain label is shortened but honest (M1.4 semantics): "(den)" for
 *     INFOPOCASI, "(10 min)" for CHMU.
 *  5. A station without data renders as a placeholder (G3, US-002).
 */
object WidgetLayout {

    private val STALE_MS = Sources.STALE_THRESHOLD_MIN * 60_000L

    fun build(
        measurements: Map<String, StationMeasurement>,
        nowMs: Long,
        wide: Boolean
    ): WidgetLayoutState {
        val left = WidgetSynthesis.synthesize(measurements, nowMs)

        val source = left.sourceStation?.let { measurements[it] }
        val secondary = source?.let { secondaryItems(it) } ?: emptyList()

        val otherId = if (left.sourceStation == Sources.STATION_CHMU) {
            Sources.STATION_INFOPOCASI
        } else {
            Sources.STATION_CHMU
        }
        val right = measurements[otherId]?.let { m ->
            SideStation(
                name = WidgetSynthesis.shortName(m.station),
                temperatureC = m.temperatureC,
                humidityPct = m.humidityPct,
                hasData = true,
                isStale = nowMs - m.measuredAtMs > STALE_MS,
                measuredAtMs = m.measuredAtMs
            )
        } ?: SideStation(
            name = WidgetSynthesis.shortName(otherId),
            temperatureC = null,
            humidityPct = null,
            hasData = false,
            isStale = false,
            measuredAtMs = null
        )

        return WidgetLayoutState(
            left = left,
            secondary = secondary,
            right = right,
            showRightHumidity = wide
        )
    }

    /** Max 3 items (NF8), fixed order: Pocitová, Vítr, Srážky. */
    private fun secondaryItems(m: StationMeasurement): List<SecondaryItem> {
        val items = mutableListOf<SecondaryItem>()
        m.temperatureC?.let { t ->
            val feels = FeelsLike.calculate(t, m.windMs?.times(3.6f), m.humidityPct)
            items += SecondaryItem("Pocitová", Format.temperature(feels))
        }
        m.windMs?.let { items += SecondaryItem("Vítr", Format.wind(it)) }
        m.rainMm?.let { items += SecondaryItem("Srážky", Format.rain(it) + " (" + rainLabel(m.station) + ")") }
        return items.take(3)
    }

    private fun rainLabel(station: String): String = when (station) {
        Sources.STATION_INFOPOCASI -> "den"
        Sources.STATION_CHMU -> "10 min"
        else -> "—"
    }
}