package io.github.painter99.wswolomouc.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Theme preference (round 2, Pavel 23. 9.): system default, persisted. */
class ThemeModeTest {

    @Test
    fun fromKey_parsesKnownKeys_unknownFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("dark"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("nonsense"))
    }
}