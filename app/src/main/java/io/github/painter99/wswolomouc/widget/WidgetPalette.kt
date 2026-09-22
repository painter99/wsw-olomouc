package io.github.painter99.wswolomouc.widget

/**
 * Widget color palette (M1.6b-2) — pure ARGB constants so contrast is
 * unit-testable (WidgetPaletteTest). Background is AMOLED black (F2.6);
 * per Pavel's rule (22. 9.) every text and icon must be clearly visible
 * on black: text colors >= 7:1, status dots >= 4.5:1 (WCAG vs #000000).
 *
 * DOT_OK is lightened (#66BB6A, 8.9:1) compared to the app's dark green
 * (#2E7D32, 4.1:1) — the app uses a light background and keeps the dark
 * variant there.
 */
object WidgetPalette {
    const val TEXT_PRIMARY: Long = 0xFFFFFFFFL   // 21.0:1
    const val TEXT_SECONDARY: Long = 0xFFBDBDBDL // 11.2:1
    const val TEXT_TERTIARY: Long = 0xFF9E9E9EL  // 7.8:1
    const val DOT_OK: Long = 0xFF66BB6AL         // 8.9:1
    const val DOT_STALE: Long = 0xFFEF6C00L      // 6.8:1
    const val DOT_OFFLINE: Long = 0xFF9E9E9EL    // 7.8:1

    /** WCAG 2.x contrast ratio of an ARGB color against pure black. */
    fun contrastRatioVsBlack(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        fun lin(c: Double): Double =
            if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        val l = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
        return (l + 0.05) / 0.05
    }
}