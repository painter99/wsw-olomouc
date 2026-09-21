package io.github.painter99.wswolomouc.ui

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Value formatting for the UI (M1.5). Czech decimal comma, units with
 * narrow spacing. Null (missing value) is always "—", never 0.
 */
object Format {

    fun temperature(c: Float?): String = if (c == null) "—" else "${c.roundToInt()} °C"

    fun humidity(pct: Int?): String = if (pct == null) "—" else "$pct %"

    fun pressure(hPa: Float?): String = if (hPa == null) "—" else "${hPa.roundToInt()} hPa"

    /** Wind input in m/s (PRD data model), displayed as km/h (F1.2). */
    fun wind(ms: Float?): String =
        if (ms == null) "—" else "${(ms * 3.6f).roundToInt()} km/h"

    fun rain(mm: Float?): String =
        if (mm == null) "—" else String.format(Locale("cs"), "%.1f", mm) + " mm"
}
