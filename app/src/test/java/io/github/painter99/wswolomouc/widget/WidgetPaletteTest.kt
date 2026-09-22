package io.github.painter99.wswolomouc.widget

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget palette contrast tests (M1.6b-2, Pavel 22. 9.: "všechny texty a
 * ikony musejí být kontrastně viditelné na černém podkladu").
 *
 * WCAG 2.x contrast ratio against pure black (#000000):
 *  - every TEXT color must reach >= 7:1 (AAA body text),
 *  - every STATUS DOT must reach >= 4.5:1 (AA large/graphical).
 * The widget background is AMOLED black per F2.6, so black is the only
 * background this palette must satisfy.
 */
class WidgetPaletteTest {

    private val textColors = listOf(
        WidgetPalette.TEXT_PRIMARY,
        WidgetPalette.TEXT_SECONDARY,
        WidgetPalette.TEXT_TERTIARY
    )

    private val dotColors = listOf(
        WidgetPalette.DOT_OK,
        WidgetPalette.DOT_STALE,
        WidgetPalette.DOT_OFFLINE
    )

    @Test
    fun textColors_haveContrastAtLeast7VsBlack() {
        for (c in textColors) {
            assertTrue(
                "text color ${c.toString(16)} has contrast ${WidgetPalette.contrastRatioVsBlack(c)}",
                WidgetPalette.contrastRatioVsBlack(c) >= 7.0
            )
        }
    }

    @Test
    fun dotColors_haveContrastAtLeast4_5VsBlack() {
        for (c in dotColors) {
            assertTrue(
                "dot color ${c.toString(16)} has contrast ${WidgetPalette.contrastRatioVsBlack(c)}",
                WidgetPalette.contrastRatioVsBlack(c) >= 4.5
            )
        }
    }
}