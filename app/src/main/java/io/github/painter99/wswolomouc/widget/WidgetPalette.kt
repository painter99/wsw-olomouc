package io.github.painter99.wswolomouc.widget

/**
 * Widget color palette v2 (M1.6b-2 redesign, Pavel 22. 9.: "nechci šedé
 * písmo ve widgetu, nejde vidět například na slunci").
 *
 * Rules:
 *  - ALL widget text is pure white (21:1 vs the AMOLED black background,
 *    F2.6); gray text colors are banned. Emphasis comes from size/weight
 *    only, never from darker color.
 *  - Status dots keep their colors and reach >= 4.5:1 vs black (WCAG).
 *    DOT_OK is lightened (#66BB6A, 8.9:1) — the app's dark green (#2E7D32,
 *    4.1:1) stays on the app's light background only.
 */
object WidgetPalette {
    const val TEXT_PRIMARY: Long = 0xFFFFFFFFL   // 21.0:1
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