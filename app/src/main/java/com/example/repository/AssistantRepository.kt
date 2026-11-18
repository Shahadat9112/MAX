package com.example.repository

import android.content.Context
import com.example.ai.MaxAiService
import com.example.data.ChatMessageEntity
import com.example.device.ContactsHelper
import com.example.device.DeviceControlManager
import com.example.device.NetworkMonitor
import com.example.device.PhoneCallManager
import com.example.device.SmsHelper
import com.example.domain.AiDecision
import com.example.domain.ContactMatch
import com.example.domain.OfflineCommandEngine
import com.example.domain.VoiceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * MVVM data/action repository bridging local device capabilities (contacts, phone calls,
 * SMS, hardware controls, offline parser, connectivity) and cloud AI inference.
 */
class AssistantRepository(context: Context) {
    private val appContext = context.applicationContext

    private val aiService = MaxAiService()
    private val networkMonitor = NetworkMonitor(appContext)
    private val deviceControl = DeviceControlManager(appContext)
    private val contactsHelper = ContactsHelper(appContext)
    private val phoneCallManager = PhoneCallManager(appContext)
    private val smsHelper = SmsHelper(appContext)

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    fun isNetworkConnected(): Boolean = networkMonitor.checkCurrentConnectivity()

    fun parseOfflineCommand(input: String): AiDecision? =
        OfflineCommandEngine.tryParse(input)

    suspend fun requestAiDecision(
        userInput: String,
        recentHistory: List<ChatMessageEntity>,
        preferredLanguage: VoiceLanguage
    ): AiDecision = aiService.sendMessage(
        userInput = userInput,
        recentHistory = recentHistory,
        preferredLanguage = preferredLanguage
    )

    suspend fun requestAudioAiDecision(
        wavBytes: ByteArray,
        recentHistory: List<ChatMessageEntity>,
        preferredLanguage: VoiceLanguage
    ): Pair<String, AiDecision> = aiService.sendAudioMessage(
        wavBytes = wavBytes,
        recentHistory = recentHistory,
        preferredLanguage = preferredLanguage
    )

    suspend fun findContacts(query: String): List<ContactMatch> =
        withContext(Dispatchers.IO) { contactsHelper.findContacts(query) }

    fun makeCall(phoneNumber: String): Result<Boolean> =
        phoneCallManager.makeCall(phoneNumber)

    fun hasSendSmsPermission(): Boolean = smsHelper.hasSendSmsPermission()

    suspend fun sendSms(phoneNumber: String, messageBody: String): Result<Unit> =
        withContext(Dispatchers.IO) { smsHelper.sendSms(phoneNumber, messageBody) }

    fun getCurrentTimeMessage(isBengali: Boolean): String =
        deviceControl.getCurrentTimeMessage(isBengali)

    fun getBatteryStatusMessage(isBengali: Boolean): String =
        deviceControl.getBatteryStatusMessage(isBengali)

    fun toggleFlashlight(): Result<Boolean> = deviceControl.toggleFlashlight()

    fun adjustVolume(direction: Int): Int = deviceControl.adjustVolume(direction)

    fun setVolumePercent(percent: Int): Int = deviceControl.setVolumePercent(percent)

    fun openSettings(section: String?): Boolean = deviceControl.openSettings(section)

    fun openInstalledApp(appName: String): Result<String> = deviceControl.openInstalledApp(appName)
}
