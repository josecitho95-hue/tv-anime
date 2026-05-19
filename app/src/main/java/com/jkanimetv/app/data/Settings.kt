package com.jkanimetv.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Single DataStore instance per process — extension property pinned to Context.
private val Context.dataStore by preferencesDataStore(name = "jkanime_settings")

class Settings(private val context: Context) {

    private object Keys {
        val SUBTITLE_LANG = stringPreferencesKey("subtitle_lang")          // "es" | "en" | "off"
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")         // 0.5..2.0
        val MAX_VIDEO_HEIGHT = intPreferencesKey("max_video_height")       // 0 = Auto, 480, 720, 1080
    }

    object Defaults {
        const val SUBTITLE_LANG = "es"
        const val PLAYBACK_SPEED = 1.0f
        const val MAX_VIDEO_HEIGHT = 0  // Auto
    }

    val subtitleLang: Flow<String> = context.dataStore.data
        .map { it[Keys.SUBTITLE_LANG] ?: Defaults.SUBTITLE_LANG }

    val playbackSpeed: Flow<Float> = context.dataStore.data
        .map { it[Keys.PLAYBACK_SPEED] ?: Defaults.PLAYBACK_SPEED }

    val maxVideoHeight: Flow<Int> = context.dataStore.data
        .map { it[Keys.MAX_VIDEO_HEIGHT] ?: Defaults.MAX_VIDEO_HEIGHT }

    suspend fun setSubtitleLang(value: String) = edit { it[Keys.SUBTITLE_LANG] = value }
    suspend fun setPlaybackSpeed(value: Float) = edit { it[Keys.PLAYBACK_SPEED] = value }
    suspend fun setMaxVideoHeight(value: Int) = edit { it[Keys.MAX_VIDEO_HEIGHT] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
