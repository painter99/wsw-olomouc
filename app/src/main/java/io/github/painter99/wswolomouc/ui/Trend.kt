package io.github.painter99.wswolomouc.ui

/** Direction of the temperature trend (PRD Fáze 2 "trend šipka"). */
enum class TrendDirection { RISING, FALLING, STEADY }

/**
 * Pure trend rules (Pavel 23. 9. 2026): the widget shows ONE arrow for the
 * 3 h window next to the big temperature; the app shows all three windows
 * (1 h / 3 h / 6 h) at once. A change within ±0.3 °C counts as STEADY
 * (Davis station noise is around ±0.1–0.2 °C). Missing data -> null.
 */
object Trend {
    const val THRESHOLD_C = 0.3f

    /** Widget window: one arrow, 3 h (Pavel 23. 9.). */
    const val WIDGET_WINDOW_MS = 3L * 60 * 60 * 1000

    /** App windows: all three at once (Pavel 23. 9.). */
    val APP_WINDOWS_MS: List<Pair<Long, String>> = listOf(
        1L * 60 * 60 * 1000 to "1 h",
        3L * 60 * 60 * 1000 to "3 h",
        6L * 60 * 60 * 1000 to "6 h"
    )

    fun compute(currentC: Float?, pastC: Float?): TrendDirection? {
        if (currentC == null || pastC == null) return null
        // Round to 0.1 °C (the display precision) so float noise does not
        // push an exactly-at-threshold change over the limit (run #72).
        val diff = Math.round((currentC - pastC) * 10f) / 10.0
        return when {
            diff > THRESHOLD_C -> TrendDirection.RISING
            diff < -THRESHOLD_C -> TrendDirection.FALLING
            else -> TrendDirection.STEADY
        }
    }

    fun arrow(direction: TrendDirection?): String = when (direction) {
        TrendDirection.RISING -> "↗"
        TrendDirection.FALLING -> "↘"
        TrendDirection.STEADY -> "→"
        null -> ""
    }
}

/** One app trend row entry: "1 h ↗"; "3 h –" when the history is missing. */
data class TrendItem(val label: String, val direction: TrendDirection?) {
    val text: String
        get() = "$label ${direction?.let { Trend.arrow(it) } ?: "–"}"
}