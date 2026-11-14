package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "veloplay_settings")

data class PlayerSettings(
    val darkTheme: Boolean = true,
    val autoPlayNext: Boolean = true,
    val autoPipEnabled: Boolean = true,
    val defaultSpeed: Float = 1.0f
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val AUTO_PIP_ENABLED = booleanPreferencesKey("auto_pip_enabled")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
    }

    val settingsFlow: Flow<PlayerSettings> = context.dataStore.data.map { prefs ->
        PlayerSettings(
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            autoPlayNext = prefs[Keys.AUTO_PLAY_NEXT] ?: true,
            autoPipEnabled = prefs[Keys.AUTO_PIP_ENABLED] ?: true,
            defaultSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setAutoPlayNext(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PLAY_NEXT] = enabled }
    }

    suspend fun setAutoPipEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PIP_ENABLED] = enabled }
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.DEFAULT_SPEED] = speed }
    }
}
