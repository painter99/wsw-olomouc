package io.github.painter99.wswolomouc.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "před X min" formatter (M1.5, PRD G2). Pure function of (measuredAt, now)
 * so it is fully testable; the UI supplies both values.
 *
 * Format rules:
 *  - < 1 min         -> "právě teď"
 *  - < 60 min        -> "před N min"
 *  - < 24 h          -> "před H h" / "před H h M min"
 *  - 24 h and older  -> absolute date+time in Europe/Prague
 * A measurement "from the future" (clock skew) is clamped to "právě teď".
 */
object RelativeTimeFormatter {

    private val PRAGUE = ZoneId.of("Europe/Prague")
    private val DATE_TIME = DateTimeFormatter.ofPattern("d. M. yyyy HH:mm", Locale("cs"))

    fun format(measuredAtMs: Long, nowMs: Long): String {
        val diffMin = (nowMs - measuredAtMs).coerceAtLeast(0) / 60_000L
        return when {
            diffMin < 1 -> "právě teď"
            diffMin < 60 -> "před $diffMin min"
            diffMin < 24 * 60 -> {
                val h = diffMin / 60
                val m = diffMin % 60
                if (m == 0L) "před $h h" else "před $h h $m min"
            }
            else -> Instant.ofEpochMilli(measuredAtMs).atZone(PRAGUE).format(DATE_TIME)
        }
    }
}
