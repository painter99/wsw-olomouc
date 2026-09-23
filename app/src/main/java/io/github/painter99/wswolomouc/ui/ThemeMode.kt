package io.github.painter99.wswolomouc.ui

/**
 * App theme preference (round 2, Pavel 23. 9. 2026): system default,
 * switchable in the app, persisted in DataStore.
 */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}