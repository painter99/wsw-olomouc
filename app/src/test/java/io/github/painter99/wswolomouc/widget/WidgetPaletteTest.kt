package io.github.painter99.wswolomouc.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget palette contrast tests (M1.6b-2, Pavel 22. 9.: "nechci šedé písmo
 * ve widgetu, nejde vidět například na slunci").
 *
 * Rules:
 *  - ALL widget text is pure white (21:1 vs the AMOLED black background,
 *    F2.6) — gray text colors are BANNED on the widget.
 *  - Status dots keep their colors and must reach >= 4.5:1 vs black.
 */
class WidgetPaletteTest {

    @Test
    fun widgetText_isPureWhite() {
        assertEquals(0xFFFFFFFFL, WidgetPalette.TEXT_PRIMARY)
    }

    @Test
    fun whiteText_hasContrastAtLeast7VsBlack() {
        assertTrue(
            "text contrast ${WidgetPalette.contrastRatioVsBlack(WidgetPalette.TEXT_PRIMARY)}",
            WidgetPalette.contrastRatioVsBlack(WidgetPalette.TEXT_PRIMARY) >= 7.0
        )
    }

    @Test
    fun dotColors_haveContrastAtLeast4_5VsBlack() {
        val dotColors = listOf(
            WidgetPalette.DOT_OK,
            WidgetPalette.DOT_STALE,
            WidgetPalette.DOT_OFFLINE
        )
        for (c in dotColors) {
            assertTrue(
                "dot color ${c.toString(16)} has contrast ${WidgetPalette.contrastRatioVsBlack(c)}",
                WidgetPalette.contrastRatioVsBlack(c) >= 4.5
            )
        }
    }
}