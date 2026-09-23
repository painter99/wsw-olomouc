package io.github.painter99.wswolomouc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Trend arrow tests (PRD Fáze 2 "trend šipka", Pavel 23. 9. 2026) — pure JVM.
 *
 * Pavel: the widget gets ONE arrow (3 h window), the app shows all three
 * windows (1 h / 3 h / 6 h). A change within ±0.3 °C counts as steady
 * (measurement noise of the Davis station is around ±0.1–0.2 °C).
 */
class TrendTest {

    @Test
    fun compute_rising_aboveThreshold() {
        assertEquals(TrendDirection.RISING, Trend.compute(12.5f, 12.0f))
    }

    @Test
    fun compute_falling_aboveThreshold() {
        assertEquals(TrendDirection.FALLING, Trend.compute(11.5f, 12.0f))
    }

    @Test
    fun compute_steady_withinThreshold() {
        assertEquals(TrendDirection.STEADY, Trend.compute(12.2f, 12.0f))
    }

    @Test
    fun compute_exactlyAtThreshold_isSteady() {
        assertEquals(TrendDirection.STEADY, Trend.compute(12.3f, 12.0f))
        assertEquals(TrendDirection.STEADY, Trend.compute(11.7f, 12.0f))
    }

    @Test
    fun compute_missingValues_yieldNull() {
        assertNull(Trend.compute(null, 12.0f))
        assertNull(Trend.compute(12.0f, null))
        assertNull(Trend.compute(null, null))
    }

    @Test
    fun arrow_mapsDirections() {
        assertEquals("↗", Trend.arrow(TrendDirection.RISING))
        assertEquals("↘", Trend.arrow(TrendDirection.FALLING))
        assertEquals("→", Trend.arrow(TrendDirection.STEADY))
        assertEquals("", Trend.arrow(null))
    }

    @Test
    fun trendItem_text_labelPlusArrow_orDashWhenUnknown() {
        assertEquals("1 h ↗", TrendItem("1 h", TrendDirection.RISING).text)
        assertEquals("3 h –", TrendItem("3 h", null).text)
    }
}