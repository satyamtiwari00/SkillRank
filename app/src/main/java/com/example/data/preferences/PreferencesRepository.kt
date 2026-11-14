package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

data class AppPreferences(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val reduceMotion: Boolean = false
)

private val Context.skillRankDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "skillrank_preferences"
)

class PreferencesRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_preference")
    private val reduceMotionKey = booleanPreferencesKey("reduce_motion")

    val preferencesFlow: Flow<AppPreferences> = context.skillRankDataStore.data.map { prefs ->
        val themeName = prefs[themeKey] ?: ThemePreference.SYSTEM.name
        val theme = ThemePreference.entries.firstOrNull { it.name == themeName }
            ?: ThemePreference.SYSTEM
        val reduceMotion = prefs[reduceMotionKey] ?: false
        AppPreferences(themePreference = theme, reduceMotion = reduceMotion)
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.skillRankDataStore.edit { prefs ->
            prefs[themeKey] = theme.name
        }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.skillRankDataStore.edit { prefs ->
            prefs[reduceMotionKey] = enabled
        }
    }
}
