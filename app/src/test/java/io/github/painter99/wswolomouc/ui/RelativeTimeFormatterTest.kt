package io.github.painter99.wswolomouc.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Relative time formatter tests (M1.5, PRD G2) — tabulated, pure JVM.
 */
class RelativeTimeFormatterTest {

    private val now = 1_758_000_000_000L
    private val minute = 60_000L

    private fun format(ageMs: Long): String =
        RelativeTimeFormatter.format(now - ageMs, now)

    @Test
    fun underOneMinute() {
        assertEquals("právě teď", format(0))
        assertEquals("právě teď", format(59_000))
    }

    @Test
    fun minutes() {
        assertEquals("před 1 min", format(1 * minute))
        assertEquals("před 5 min", format(5 * minute))
        assertEquals("před 59 min", format(59 * minute))
    }

    @Test
    fun hours() {
        assertEquals("před 1 h", format(60 * minute))
        assertEquals("před 1 h 5 min", format(65 * minute))
        assertEquals("před 2 h", format(120 * minute))
        assertEquals("před 23 h 59 min", format(24 * minute * 60 - minute))
    }

    @Test
    fun olderThanDay_showsAbsoluteDateAndTime() {
        // Epoch 0 in Europe/Prague = 1. 1. 1970 01:00
        assertEquals("1. 1. 1970 01:00", RelativeTimeFormatter.format(0, 25 * 60 * minute))
    }

    @Test
    fun futureMeasurement_clampedToNow() {
        assertEquals("právě teď", RelativeTimeFormatter.format(now + 5 * minute, now))
    }
}
