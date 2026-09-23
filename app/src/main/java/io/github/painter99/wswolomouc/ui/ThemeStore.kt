package io.github.painter99.wswolomouc.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persisted theme preference (round 2, Pavel 23. 9. 2026). The interface
 * keeps the ViewModel testable — the DataStore implementation is Android-only.
 */
interface ThemeStore {
    val mode: Flow<ThemeMode>
    suspend fun set(mode: ThemeMode)
}

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class DataStoreThemeStore(private val context: Context) : ThemeStore {

    override val mode: Flow<ThemeMode> =
        context.settingsDataStore.data.map { prefs ->
            ThemeMode.fromKey(prefs[KEY_THEME])
        }

    override suspend fun set(mode: ThemeMode) {
        context.settingsDataStore.edit { it[KEY_THEME] = mode.key }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")
    }
}