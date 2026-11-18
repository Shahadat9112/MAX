package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.VoiceLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "max_ai_prefs")

data class UserPreferences(
    val ttsEnabled: Boolean = true,
    val backgroundWakeWordEnabled: Boolean = true,
    val preferredLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    val autoListenAfterReply: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val WAKE_WORD_BG = booleanPreferencesKey("wake_word_background_enabled")
        val PREFERRED_LANG = stringPreferencesKey("preferred_language")
        val AUTO_LISTEN = booleanPreferencesKey("auto_listen_after_reply")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val langName = prefs[Keys.PREFERRED_LANG] ?: VoiceLanguage.AUTO.name
        val language = runCatching { VoiceLanguage.valueOf(langName) }
            .getOrDefault(VoiceLanguage.AUTO)
        UserPreferences(
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            backgroundWakeWordEnabled = prefs[Keys.WAKE_WORD_BG] ?: true,
            preferredLanguage = language,
            autoListenAfterReply = prefs[Keys.AUTO_LISTEN] ?: false
        )
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TTS_ENABLED] = enabled }
    }

    suspend fun setBackgroundWakeWordEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WAKE_WORD_BG] = enabled }
    }

    suspend fun setPreferredLanguage(language: VoiceLanguage) {
        context.dataStore.edit { it[Keys.PREFERRED_LANG] = language.name }
    }

    suspend fun setAutoListenAfterReply(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_LISTEN] = enabled }
    }
}
