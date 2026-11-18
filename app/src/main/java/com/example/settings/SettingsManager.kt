package com.example.settings

import android.content.Context
import com.example.data.UserPreferences
import com.example.data.UserPreferencesRepository
import com.example.domain.VoiceLanguage
import kotlinx.coroutines.flow.Flow

/**
 * Independent manager encapsulating user preferences via DataStore, including TTS,
 * background wake-word toggle, voice language selection, privacy shield, and history storage consent.
 */
class SettingsManager(context: Context) {
    private val prefsRepository = UserPreferencesRepository(context.applicationContext)

    val preferencesFlow: Flow<UserPreferences> = prefsRepository.preferencesFlow

    suspend fun setTtsEnabled(enabled: Boolean) {
        prefsRepository.setTtsEnabled(enabled)
    }

    suspend fun setBackgroundWakeWordEnabled(enabled: Boolean) {
        prefsRepository.setBackgroundWakeWordEnabled(enabled)
    }

    suspend fun setPreferredLanguage(language: VoiceLanguage) {
        prefsRepository.setPreferredLanguage(language)
    }

    fun nextLanguage(current: VoiceLanguage): VoiceLanguage {
        val order = VoiceLanguage.entries
        return order[(order.indexOf(current) + 1) % order.size]
    }

    suspend fun setAutoListenAfterReply(enabled: Boolean) {
        prefsRepository.setAutoListenAfterReply(enabled)
    }

    suspend fun setStrictPrivacyMode(enabled: Boolean) {
        prefsRepository.setStrictPrivacyMode(enabled)
    }

    suspend fun setSaveHistoryConsent(consented: Boolean) {
        prefsRepository.setSaveHistoryConsent(consented)
    }
}
