package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ai.MaxAiService
import com.example.data.ChatMessageEntity
import com.example.data.ChatRepository
import com.example.data.MaxDatabase
import com.example.data.UserPreferences
import com.example.data.UserPreferencesRepository
import com.example.device.ContactsHelper
import com.example.device.DeviceControlManager
import com.example.device.NetworkMonitor
import com.example.device.PhoneCallManager
import com.example.device.SmsHelper
import com.example.device.VoiceRecognitionManager
import com.example.device.VoiceTtsManager
import com.example.domain.AiDecision
import com.example.domain.AssistantActionType
import com.example.domain.AssistantStatus
import com.example.domain.ContactMatch
import com.example.domain.OfflineCommandEngine
import com.example.domain.PendingAction
import com.example.domain.VoiceLanguage
import com.example.domain.WakeWordParser
import com.example.domain.WakeWordResult
import com.example.ui.MaxUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application-scoped assistant brain shared by both MaxViewModel (foreground UI)
 * and MaxWakeWordService (background / screen-off wake-word execution).
 */
class MaxAssistantController private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val database = MaxDatabase.getInstance(appContext)
    private val chatRepository = ChatRepository(database.chatMessageDao())
    private val prefsRepository = UserPreferencesRepository(appContext)
    private val aiService = MaxAiService()
    private val networkMonitor = NetworkMonitor(appContext)
    private val deviceControl = DeviceControlManager(appContext)
    private val contactsHelper = ContactsHelper(appContext)
    private val phoneCallManager = PhoneCallManager(appContext)
    private val smsHelper = SmsHelper(appContext)
    private val voiceRecognizer = VoiceRecognitionManager(appContext)
    private val voiceTts = VoiceTtsManager(appContext)

    private val _isThinking = MutableStateFlow(false)
    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    private val _isCameraOpen = MutableStateFlow(false)
    private val _autoCapture = MutableStateFlow(false)
    private val _lastPhotoUri = MutableStateFlow<Uri?>(null)
    private val _permissionHealth = MutableStateFlow(computePermissionHealth())
    private val _bannerError = MutableStateFlow<String?>(null)

    private val _permissionRequests = MutableSharedFlow<Array<String>>(extraBufferCapacity = 4)
    val permissionRequests: SharedFlow<Array<String>> = _permissionRequests.asSharedFlow()

    private var lastCommandToRetryAfterPermission: String? = null
    private var pendingSmsToSendAfterPermission: PendingAction.ConfirmSms? = null
    private var pendingVoiceFollowUpAfterTts = false

    val uiState: StateFlow<MaxUiState> = combine(
        chatRepository.messages,
        prefsRepository.preferencesFlow,
        networkMonitor.isOnline,
        voiceRecognizer.isListening,
        voiceRecognizer.partialText,
        voiceRecognizer.audioLevel,
        voiceRecognizer.activeLocaleTag,
        voiceTts.isSpeaking,
        _isThinking,
        _pendingAction,
        _isCameraOpen,
        _autoCapture,
        _lastPhotoUri,
        WakeWordCoordinator.isServiceRunning,
        WakeWordCoordinator.lastHeardStatus,
        _permissionHealth,
        _bannerError
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val messages = values[0] as List<ChatMessageEntity>
        val prefs = values[1] as UserPreferences
        val online = values[2] as Boolean
        val listening = values[3] as Boolean
        val partial = values[4] as String
        val audioLevel = values[5] as Float
        val activeLocale = values[6] as String
        val speaking = values[7] as Boolean
        val thinking = values[8] as Boolean
        val pending = values[9] as PendingAction?
        val cameraOpen = values[10] as Boolean
        val autoCap = values[11] as Boolean
        val lastPhoto = values[12] as Uri?
        val wakeRunning = values[13] as Boolean
        val wakeText = values[14] as String
        @Suppress("UNCHECKED_CAST")
        val perms = values[15] as Map<String, Boolean>
        val error = values[16] as String?

        val status = when {
            listening -> AssistantStatus.LISTENING
            thinking -> AssistantStatus.THINKING
            speaking -> AssistantStatus.SPEAKING
            else -> AssistantStatus.IDLE
        }

        WakeWordCoordinator.setForegroundMicBusy(listening || speaking)

        MaxUiState(
            status = status,
            messages = messages,
            partialTranscript = partial,
            audioLevel = audioLevel,
            activeSpeechLocale = activeLocale,
            isOnline = online,
            preferences = prefs,
            pendingAction = pending,
            isCameraOpen = cameraOpen,
            autoCaptureOnCameraOpen = autoCap,
            lastCapturedPhotoUri = lastPhoto,
            isWakeServiceRunning = wakeRunning,
            wakeServiceStatusText = wakeText,
            permissionHealth = perms,
            bannerError = error
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = MaxUiState(permissionHealth = computePermissionHealth())
    )

    init {
        voiceTts.onSpeechCompleted = {
            scope.launch {
                val shouldListen = pendingVoiceFollowUpAfterTts ||
                    uiState.value.preferences.autoListenAfterReply
                pendingVoiceFollowUpAfterTts = false
                if (shouldListen && !_isCameraOpen.value) {
                    startVoiceListening()
                }
            }
        }
    }

    fun ensureBackgroundWakeServiceIfEnabled() {
        val prefs = uiState.value.preferences
        if (prefs.backgroundWakeWordEnabled &&
            hasPermission(Manifest.permission.RECORD_AUDIO) &&
            !WakeWordCoordinator.isServiceRunning.value
        ) {
            MaxWakeWordService.start(appContext)
        }
    }

    fun handleWakeWordTriggered(command: String) {
        scope.launch {
            if (command.isBlank()) {
                val isBn = uiState.value.preferences.preferredLanguage == VoiceLanguage.BENGALI
                val prompt = if (isBn) "হ্যাঁ, বলুন।" else "Yes, I'm listening."
                pendingVoiceFollowUpAfterTts = true
                if (uiState.value.preferences.ttsEnabled) {
                    voiceTts.speak(prompt, if (isBn) "bn" else "en")
                } else {
                    pendingVoiceFollowUpAfterTts = false
                    startVoiceListening()
                }
            } else {
                submitUserInput(command, fromVoice = true)
            }
        }
    }

    fun toggleMicrophone() {
        if (uiState.value.status == AssistantStatus.LISTENING) {
            voiceRecognizer.stopListening()
        } else {
            voiceTts.stop()
            startVoiceListening()
        }
    }

    fun startVoiceListening() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        _bannerError.value = null
        voiceTts.stop()
        // Pause background service recognizer immediately before starting foreground mic
        WakeWordCoordinator.setForegroundMicBusy(true)

        val prefs = uiState.value.preferences
        val preferOffline = !networkMonitor.checkCurrentConnectivity()

        mainHandler.postDelayed({
            voiceRecognizer.startListening(
                language = prefs.preferredLanguage,
                preferOffline = preferOffline,
                onResult = { recognitionResult ->
                    submitUserInput(
                        rawText = recognitionResult.text,
                        fromVoice = true,
                        spokenLanguageHint = recognitionResult.detectedLanguage
                    )
                },
                onAudioFallback = { wavBytes ->
                    submitAudioFallback(wavBytes)
                },
                onError = { errMsg ->
                    _bannerError.value = errMsg
                }
            )
        }, 120L)
    }

    private fun submitAudioFallback(wavBytes: ByteArray) {
        scope.launch {
            _isThinking.value = true
            try {
                if (!networkMonitor.checkCurrentConnectivity()) {
                    _bannerError.value = "Speech recognition unavailable offline without an offline speech pack."
                    return@launch
                }
                val history = chatRepository.getRecentContext(limit = 16)
                val (transcript, decision) = aiService.sendAudioMessage(
                    wavBytes = wavBytes,
                    recentHistory = history,
                    preferredLanguage = uiState.value.preferences.preferredLanguage
                )

                val cleanTranscript = WakeWordParser.stripOptionalWakeWord(transcript)
                val currentPending = _pendingAction.value
                if (currentPending != null && handlePendingFlowInput(currentPending, cleanTranscript, fromVoice = true)) {
                    return@launch
                }

                val userLang = if (WakeWordParser.isBengaliText(cleanTranscript)) "bn" else "en"
                chatRepository.addMessage(isUser = true, text = transcript, language = userLang)
                executeDecision(decision, isOffline = false, fromVoice = true)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Audio AI request timed out", e)
                chatRepository.addMessage(
                    isUser = false,
                    text = "Voice processing timed out. Please try again.",
                    isError = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio AI request failed", e)
                chatRepository.addMessage(
                    isUser = false,
                    text = "Voice AI error: ${e.localizedMessage ?: "Unable to process voice."}",
                    isError = true
                )
            } finally {
                _isThinking.value = false
            }
        }
    }

    fun dismissBannerError() {
        _bannerError.value = null
    }

    fun submitUserInput(
        rawText: String,
        fromVoice: Boolean = false,
        spokenLanguageHint: String? = null
    ) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return
        _bannerError.value = null

        val cleanInput = WakeWordParser.stripOptionalWakeWord(trimmed)

        val currentPending = _pendingAction.value
        if (currentPending != null) {
            if (handlePendingFlowInput(currentPending, cleanInput, fromVoice)) {
                return
            }
        }

        scope.launch {
            val userLang = when {
                WakeWordParser.isBengaliText(cleanInput) -> "bn"
                spokenLanguageHint == "bn" -> "bn"
                else -> "en"
            }
            chatRepository.addMessage(isUser = true, text = trimmed, language = userLang)

            _isThinking.value = true
            try {
                // Execute deterministic device/phone commands immediately without waiting on cloud AI
                val localAction = OfflineCommandEngine.tryParse(cleanInput)
                    ?.takeIf { it.action != AssistantActionType.NONE }
                if (localAction != null) {
                    executeDecision(
                        decision = localAction,
                        isOffline = !networkMonitor.checkCurrentConnectivity(),
                        fromVoice = fromVoice
                    )
                    return@launch
                }

                val online = networkMonitor.checkCurrentConnectivity()
                if (!online) {
                    val offlineDecision = OfflineCommandEngine.tryParse(cleanInput)
                    if (offlineDecision != null) {
                        executeDecision(offlineDecision, isOffline = true, fromVoice = fromVoice)
                    } else {
                        val reply = if (userLang == "bn") {
                            "আপনি এখন অফলাইনে আছেন। ইন্টারনেট ছাড়াই আমি সময়, ব্যাটারি, ভলিউম, টর্চলাইট, অ্যাপ খোলা, কল, এসএমএস এবং ক্যামেরা চালাতে পারি।"
                        } else {
                            "You're currently offline. Without internet, I can still tell the time, check battery, adjust volume, toggle the flashlight, open apps, make calls, send SMS, and take photos."
                        }
                        respondAsMax(reply, userLang, actionBadge = "Offline Mode", isOffline = true)
                    }
                    return@launch
                }

                val history = chatRepository.getRecentContext(limit = 16)
                val decision = aiService.sendMessage(
                    userInput = cleanInput,
                    recentHistory = history,
                    preferredLanguage = uiState.value.preferences.preferredLanguage
                )
                executeDecision(decision, isOffline = false, fromVoice = fromVoice)
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "AI request timed out after 45s", e)
                val offlineFallback = OfflineCommandEngine.tryParse(cleanInput)
                if (offlineFallback != null) {
                    executeDecision(offlineFallback, isOffline = true, fromVoice = fromVoice)
                } else {
                    val timeoutMsg = if (userLang == "bn") {
                        "ইন্টারনেট বা এআই সার্ভার থেকে উত্তর আসতে দেরি হচ্ছে। অনুগ্রহ করে আবার চেষ্টা করুন।"
                    } else {
                        "MAX AI timed out waiting for a response. Please check your internet connection and try again."
                    }
                    chatRepository.addMessage(
                        isUser = false,
                        text = timeoutMsg,
                        language = userLang,
                        isError = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Online AI call failed", e)
                val offlineFallback = OfflineCommandEngine.tryParse(cleanInput)
                if (offlineFallback != null) {
                    executeDecision(offlineFallback, isOffline = true, fromVoice = fromVoice)
                } else {
                    chatRepository.addMessage(
                        isUser = false,
                        text = "AI connection error: ${e.localizedMessage ?: "Unable to reach MAX AI service."}",
                        isError = true
                    )
                }
            } finally {
                _isThinking.value = false
            }
        }
    }

    private fun isConfirmReply(input: String): Boolean {
        val lower = input.lowercase().trim()
        val confirmPhrases = listOf(
            "yes", "yeah", "yep", "sure", "ok", "okay", "confirm", "go ahead", "do it",
            "send it", "send", "call him", "call her", "place the call", "please call",
            "হ্যাঁ", "হ্যা", "জি", "জ্বী", "ঠিক আছে", "আচ্ছা", "পাঠাও", "পাঠিয়ে দাও", "পাঠান",
            "কল করো", "কল দাও", "করো", "দাও"
        )
        return confirmPhrases.any { phrase ->
            lower == phrase || lower.startsWith("$phrase ") || lower.endsWith(" $phrase") || lower.contains(" $phrase ")
        }
    }

    private fun isCancelReply(input: String): Boolean {
        val lower = input.lowercase().trim()
        val cancelPhrases = listOf(
            "no", "nope", "cancel", "stop", "abort", "don't", "do not", "never mind",
            "না", "বাতিল", "থামো", "পাঠিও না", "কল করো না", "দরকার নেই"
        )
        return cancelPhrases.any { phrase ->
            lower == phrase || lower.startsWith("$phrase ") || lower.endsWith(" $phrase") || lower.contains(" $phrase ")
        }
    }

    private fun handlePendingFlowInput(
        pending: PendingAction,
        input: String,
        fromVoice: Boolean
    ): Boolean {
        val isYes = isConfirmReply(input)
        val isNo = isCancelReply(input)

        return when (pending) {
            is PendingAction.ConfirmCall -> {
                when {
                    isYes -> {
                        scope.launch {
                            chatRepository.addMessage(isUser = true, text = input)
                            confirmPendingCall()
                        }
                        true
                    }
                    isNo -> {
                        scope.launch {
                            chatRepository.addMessage(isUser = true, text = input)
                            cancelPendingAction()
                        }
                        true
                    }
                    else -> {
                        _pendingAction.value = null
                        false
                    }
                }
            }

            is PendingAction.AwaitSmsBody -> {
                if (isNo) {
                    scope.launch {
                        chatRepository.addMessage(isUser = true, text = input)
                        cancelPendingAction()
                    }
                    return true
                }
                scope.launch {
                    val lang = if (WakeWordParser.isBengaliText(input)) "bn" else pending.language
                    chatRepository.addMessage(isUser = true, text = input, language = lang)
                    _pendingAction.value = PendingAction.ConfirmSms(
                        contact = pending.contact,
                        messageBody = input,
                        language = lang
                    )
                    val readAloud = if (lang == "bn") {
                        "${pending.contact.name}-কে পাঠানো মেসেজটি হলো: \"$input\"। আমি কি এটি পাঠিয়ে দেবো?"
                    } else {
                        "Your message to ${pending.contact.name} reads: \"$input\". Should I send it?"
                    }
                    if (fromVoice) pendingVoiceFollowUpAfterTts = true
                    respondAsMax(readAloud, lang, actionBadge = "Review SMS")
                }
                true
            }

            is PendingAction.ConfirmSms -> {
                when {
                    isYes -> {
                        scope.launch {
                            chatRepository.addMessage(isUser = true, text = input)
                            confirmPendingSms(pending.messageBody)
                        }
                        true
                    }
                    isNo -> {
                        scope.launch {
                            chatRepository.addMessage(isUser = true, text = input)
                            cancelPendingAction()
                        }
                        true
                    }
                    else -> {
                        _pendingAction.value = null
                        false
                    }
                }
            }

            is PendingAction.RequestPermission -> {
                _pendingAction.value = null
                false
            }
        }
    }

    private suspend fun executeDecision(
        decision: AiDecision,
        isOffline: Boolean,
        fromVoice: Boolean
    ) {
        val lang = decision.language
        val isBn = lang == "bn"

        when (decision.action) {
            AssistantActionType.NONE -> {
                respondAsMax(decision.reply, lang, isOffline = isOffline)
            }

            AssistantActionType.GET_TIME -> {
                val msg = deviceControl.getCurrentTimeMessage(isBn)
                respondAsMax(msg, lang, actionBadge = "Clock", isOffline = isOffline)
            }

            AssistantActionType.GET_BATTERY -> {
                val msg = deviceControl.getBatteryStatusMessage(isBn)
                respondAsMax(msg, lang, actionBadge = "Battery", isOffline = isOffline)
            }

            AssistantActionType.TOGGLE_FLASHLIGHT -> {
                val res = deviceControl.toggleFlashlight()
                val msg = res.fold(
                    onSuccess = { on ->
                        if (isBn) {
                            if (on) "টর্চলাইট চালু করা হয়েছে।" else "টর্চলাইট বন্ধ করা হয়েছে।"
                        } else {
                            if (on) "Flashlight turned on." else "Flashlight turned off."
                        }
                    },
                    onFailure = { err -> err.localizedMessage ?: "Flashlight unavailable." }
                )
                respondAsMax(msg, lang, actionBadge = "Flashlight", isOffline = isOffline)
            }

            AssistantActionType.VOLUME_UP -> {
                val pct = deviceControl.adjustVolume(AudioManager.ADJUST_RAISE)
                val msg = if (isBn) "ভলিউম বাড়িয়ে $pct% করা হয়েছে।" else "Volume increased to $pct%."
                respondAsMax(msg, lang, actionBadge = "Volume $pct%", isOffline = isOffline)
            }

            AssistantActionType.VOLUME_DOWN -> {
                val pct = deviceControl.adjustVolume(AudioManager.ADJUST_LOWER)
                val msg = if (isBn) "ভলিউম কমিয়ে $pct% করা হয়েছে।" else "Volume decreased to $pct%."
                respondAsMax(msg, lang, actionBadge = "Volume $pct%", isOffline = isOffline)
            }

            AssistantActionType.VOLUME_MUTE -> {
                deviceControl.adjustVolume(AudioManager.ADJUST_MUTE)
                val msg = if (isBn) "মিডিয়া ভলিউম মিউট করা হয়েছে।" else "Media volume muted."
                respondAsMax(msg, lang, actionBadge = "Muted", isOffline = isOffline)
            }

            AssistantActionType.VOLUME_UNMUTE -> {
                val pct = deviceControl.adjustVolume(AudioManager.ADJUST_UNMUTE)
                val msg = if (isBn) "মিডিয়া ভলিউম আনমিউট করা হয়েছে ($pct%)।" else "Media volume unmuted ($pct%)."
                respondAsMax(msg, lang, actionBadge = "Volume $pct%", isOffline = isOffline)
            }

            AssistantActionType.VOLUME_SET -> {
                val pct = deviceControl.setVolumePercent(decision.volumePercent ?: 50)
                val msg = if (isBn) "ভলিউম $pct%-এ সেট করা হয়েছে।" else "Volume set to $pct%."
                respondAsMax(msg, lang, actionBadge = "Volume $pct%", isOffline = isOffline)
            }

            AssistantActionType.OPEN_SETTINGS -> {
                val opened = deviceControl.openSettings(decision.targetName)
                val msg = if (opened) {
                    decision.reply.ifBlank { if (isBn) "সেটিংস খোলা হচ্ছে।" else "Opening phone settings." }
                } else {
                    if (isBn) "সেটিংস খোলা যায়নি।" else "Unable to open device settings."
                }
                respondAsMax(msg, lang, actionBadge = "Settings", isOffline = isOffline)
            }

            AssistantActionType.OPEN_APP -> {
                val target = decision.targetName.orEmpty()
                val res = deviceControl.openInstalledApp(target)
                val msg = res.fold(
                    onSuccess = { appLabel ->
                        if (isBn) "$appLabel খোলা হচ্ছে।" else "Opening $appLabel."
                    },
                    onFailure = {
                        if (isBn) "দুঃখিত, আপনার ফোনে '$target' অ্যাপটি খুঁজে পাইনি।"
                        else "I couldn't find an installed app named '$target' on your phone."
                    }
                )
                respondAsMax(msg, lang, actionBadge = "App Launcher", isOffline = isOffline)
            }

            AssistantActionType.OPEN_CAMERA -> {
                openCameraScreen(autoCapture = false, language = lang, isOffline = isOffline)
            }

            AssistantActionType.TAKE_PHOTO -> {
                openCameraScreen(autoCapture = true, language = lang, isOffline = isOffline)
            }

            AssistantActionType.CALL_CONTACT -> {
                val target = decision.targetName.orEmpty()
                val isDirectNumber = isPhoneNumberLiteral(target)

                if (!isDirectNumber && !hasPermission(Manifest.permission.READ_CONTACTS)) {
                    lastCommandToRetryAfterPermission = "call $target"
                    _pendingAction.value = PendingAction.RequestPermission(
                        permission = Manifest.permission.READ_CONTACTS,
                        rationale = if (isBn) "কন্টাক্ট খুঁজে পেতে কন্টাক্টস অনুমতি প্রয়োজন।"
                        else "Contacts permission is required to find \"$target\" before calling.",
                        pendingRetryCommand = "call $target"
                    )
                    requestPermissions(
                        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
                    )
                    respondAsMax(
                        if (isBn) "কল করার আগে কন্টাক্ট খুঁজতে অনুমতি দিন।"
                        else "Please grant Contacts and Phone permissions so I can look up $target.",
                        lang,
                        actionBadge = "Permission"
                    )
                    return
                }

                val matches = if (isDirectNumber) {
                    listOf(ContactMatch(name = target, phoneNumber = target))
                } else {
                    withContext(Dispatchers.IO) { contactsHelper.findContacts(target) }
                }

                if (matches.isEmpty()) {
                    val msg = if (isBn) {
                        "আপনার কন্টাক্ট লিস্টে \"$target\" নামে কাউকে পাওয়া যায়নি।"
                    } else {
                        "I couldn't find \"$target\" in your contacts."
                    }
                    respondAsMax(msg, lang, actionBadge = "Contacts", isOffline = isOffline)
                } else {
                    val top = matches.first()
                    _pendingAction.value = PendingAction.ConfirmCall(contact = top, language = lang)
                    val confirmPrompt = if (isBn) {
                        "${top.name} (${top.phoneNumber})-কে কি কল করবো? নিশ্চিত করতে হ্যাঁ বলুন বা কল বোতামে ট্যাপ করুন।"
                    } else {
                        "Found ${top.name} at ${top.phoneNumber}. Would you like me to place this call?"
                    }
                    if (fromVoice) pendingVoiceFollowUpAfterTts = true
                    respondAsMax(confirmPrompt, lang, actionBadge = "Confirm Call", isOffline = isOffline)
                }
            }

            AssistantActionType.SEND_SMS -> {
                val target = decision.targetName.orEmpty()
                val isDirectNumber = isPhoneNumberLiteral(target)

                if (!isDirectNumber && !hasPermission(Manifest.permission.READ_CONTACTS)) {
                    val retry = if (decision.messageBody.isNullOrBlank()) {
                        "send sms to $target"
                    } else {
                        "send sms to $target saying ${decision.messageBody}"
                    }
                    lastCommandToRetryAfterPermission = retry
                    _pendingAction.value = PendingAction.RequestPermission(
                        permission = Manifest.permission.READ_CONTACTS,
                        rationale = if (isBn) "এসএমএস পাঠাতে কন্টাক্টস ও এসএমএস অনুমতি প্রয়োজন।"
                        else "Contacts and SMS permissions are required to message \"$target\".",
                        pendingRetryCommand = retry
                    )
                    requestPermissions(
                        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS)
                    )
                    respondAsMax(
                        if (isBn) "মেসেজ পাঠানোর আগে কন্টাক্টস ও এসএমএস অনুমতি দিন।"
                        else "Please grant Contacts and SMS permissions so I can prepare your message.",
                        lang,
                        actionBadge = "Permission"
                    )
                    return
                }

                val matches = if (isDirectNumber) {
                    listOf(ContactMatch(name = target, phoneNumber = target))
                } else {
                    withContext(Dispatchers.IO) { contactsHelper.findContacts(target) }
                }

                if (matches.isEmpty()) {
                    val msg = if (isBn) {
                        "আপনার কন্টাক্ট লিস্টে \"$target\" নামে কাউকে পাওয়া যায়নি।"
                    } else {
                        "I couldn't find \"$target\" in your contacts."
                    }
                    respondAsMax(msg, lang, actionBadge = "Contacts", isOffline = isOffline)
                } else {
                    val contact = matches.first()
                    val body = decision.messageBody
                    if (body.isNullOrBlank()) {
                        _pendingAction.value = PendingAction.AwaitSmsBody(contact, lang)
                        val askMsg = if (isBn) {
                            "${contact.name}-কে কী মেসেজ লিখতে চান? বলুন বা টাইপ করুন।"
                        } else {
                            "What message would you like to send to ${contact.name}?"
                        }
                        if (fromVoice) pendingVoiceFollowUpAfterTts = true
                        respondAsMax(askMsg, lang, actionBadge = "Compose SMS", isOffline = isOffline)
                    } else {
                        _pendingAction.value = PendingAction.ConfirmSms(contact, body, lang)
                        val confirmMsg = if (isBn) {
                            "${contact.name}-কে পাঠানো মেসেজটি হলো: \"$body\"। আমি কি এটি পাঠিয়ে দেবো?"
                        } else {
                            "Your message to ${contact.name} says: \"$body\". Should I send it?"
                        }
                        if (fromVoice) pendingVoiceFollowUpAfterTts = true
                        respondAsMax(confirmMsg, lang, actionBadge = "Review SMS", isOffline = isOffline)
                    }
                }
            }

            AssistantActionType.CLEAR_CHAT -> {
                chatRepository.clearHistory()
                _pendingAction.value = null
            }
        }
    }

    private fun isPhoneNumberLiteral(target: String): Boolean {
        val cleaned = target.trim()
        return cleaned.count { it.isDigit() } >= 3 && cleaned.none { it.isLetter() }
    }

    fun openCameraScreen(autoCapture: Boolean, language: String = "en", isOffline: Boolean = false) {
        scope.launch {
            if (!hasPermission(Manifest.permission.CAMERA)) {
                lastCommandToRetryAfterPermission = if (autoCapture) "take a picture" else "open camera"
                requestPermissions(arrayOf(Manifest.permission.CAMERA))
                respondAsMax(
                    if (language == "bn") "ক্যামেরা ব্যবহার করতে ক্যামেরা অনুমতি দিন।"
                    else "Please grant Camera permission so MAX can open the camera.",
                    language,
                    actionBadge = "Permission"
                )
                return@launch
            }
            _autoCapture.value = autoCapture
            _isCameraOpen.value = true
            val msg = if (autoCapture) {
                if (language == "bn") "ক্যামেরা খুলে ছবি তুলছি।" else "Taking a picture now."
            } else {
                if (language == "bn") "ক্যামেরা চালু করা হয়েছে।" else "Camera is open."
            }
            respondAsMax(msg, language, actionBadge = "Camera", isOffline = isOffline)
        }
    }

    fun onPhotoSaved(uri: Uri) {
        _lastPhotoUri.value = uri
        _autoCapture.value = false
        scope.launch {
            val isBn = uiState.value.preferences.preferredLanguage == VoiceLanguage.BENGALI
            val msg = if (isBn) "ছবিটি আপনার ফোনের গ্যালারিতে সেভ করা হয়েছে।" else "Photo saved to your phone's gallery."
            respondAsMax(msg, if (isBn) "bn" else "en", actionBadge = "Photo Saved")
        }
    }

    fun onCameraError(errorMsg: String) {
        _autoCapture.value = false
        _bannerError.value = errorMsg
    }

    fun closeCamera() {
        _isCameraOpen.value = false
        _autoCapture.value = false
    }

    fun confirmPendingCall() {
        val pending = _pendingAction.value as? PendingAction.ConfirmCall ?: return
        _pendingAction.value = null
        scope.launch {
            val isBn = pending.language == "bn"
            val result = phoneCallManager.makeCall(pending.contact.phoneNumber)
            val msg = result.fold(
                onSuccess = { directCall ->
                    if (isBn) {
                        if (directCall) "${pending.contact.name}-কে কল করা হচ্ছে..."
                        else "${pending.contact.name}-এর নম্বর ডায়ালারে খোলা হয়েছে।"
                    } else {
                        if (directCall) "Calling ${pending.contact.name}..."
                        else "Opening dialer for ${pending.contact.name} (grant Call Phone permission for direct calling)."
                    }
                },
                onFailure = { err ->
                    err.localizedMessage ?: "Failed to initiate call."
                }
            )
            respondAsMax(msg, pending.language, actionBadge = "Calling")
        }
    }

    fun readSmsAloud(messageBody: String, language: String) {
        val text = if (language == "bn") "মেসেজটি হলো: $messageBody" else "The message reads: $messageBody"
        voiceTts.speak(text, language)
    }

    fun confirmPendingSms(editedBody: String) {
        val pending = _pendingAction.value as? PendingAction.ConfirmSms ?: return
        val finalBody = editedBody.trim()
        if (finalBody.isEmpty()) return

        if (!smsHelper.hasSendSmsPermission()) {
            pendingSmsToSendAfterPermission = pending.copy(messageBody = finalBody)
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS))
            _bannerError.value = "Please grant SMS permission to send messages."
            return
        }

        _pendingAction.value = null
        scope.launch {
            val isBn = pending.language == "bn"
            val result = withContext(Dispatchers.IO) {
                smsHelper.sendSms(pending.contact.phoneNumber, finalBody)
            }
            val msg = result.fold(
                onSuccess = {
                    if (isBn) "${pending.contact.name}-কে এসএমএস সফলভাবে পাঠানো হয়েছে।"
                    else "SMS sent to ${pending.contact.name}."
                },
                onFailure = { err ->
                    if (isBn) "এসএমএস পাঠানো যায়নি: ${err.localizedMessage}"
                    else "Failed to send SMS: ${err.localizedMessage}"
                }
            )
            respondAsMax(msg, pending.language, actionBadge = "SMS Sent")
        }
    }

    fun cancelPendingAction() {
        val pending = _pendingAction.value ?: return
        val lang = when (pending) {
            is PendingAction.ConfirmCall -> pending.language
            is PendingAction.AwaitSmsBody -> pending.language
            is PendingAction.ConfirmSms -> pending.language
            is PendingAction.RequestPermission -> "en"
        }
        _pendingAction.value = null
        scope.launch {
            val msg = if (lang == "bn") "বাতিল করা হয়েছে।" else "Action cancelled."
            respondAsMax(msg, lang)
        }
    }

    private suspend fun respondAsMax(
        text: String,
        language: String,
        actionBadge: String? = null,
        isOffline: Boolean = false
    ) {
        chatRepository.addMessage(
            isUser = false,
            text = text,
            language = language,
            actionBadge = actionBadge,
            isOffline = isOffline
        )
        if (uiState.value.preferences.ttsEnabled) {
            voiceTts.speak(text, language)
        } else {
            pendingVoiceFollowUpAfterTts = false
        }
    }

    fun toggleTts() {
        val current = uiState.value.preferences.ttsEnabled
        scope.launch {
            prefsRepository.setTtsEnabled(!current)
            if (current) voiceTts.stop()
        }
    }

    fun setLanguage(language: VoiceLanguage) {
        scope.launch {
            prefsRepository.setPreferredLanguage(language)
        }
    }

    fun cycleLanguage() {
        val order = VoiceLanguage.entries
        val current = uiState.value.preferences.preferredLanguage
        val next = order[(order.indexOf(current) + 1) % order.size]
        setLanguage(next)
    }

    fun setAutoListen(enabled: Boolean) {
        scope.launch {
            prefsRepository.setAutoListenAfterReply(enabled)
        }
    }

    fun toggleBackgroundWakeWord(enabled: Boolean) {
        scope.launch {
            if (enabled) {
                val needed = mutableListOf<String>()
                if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    needed.add(Manifest.permission.RECORD_AUDIO)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    needed.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (needed.isNotEmpty()) {
                    requestPermissions(needed.toTypedArray())
                    return@launch
                }
                prefsRepository.setBackgroundWakeWordEnabled(true)
                MaxWakeWordService.start(appContext)
            } else {
                prefsRepository.setBackgroundWakeWordEnabled(false)
                MaxWakeWordService.stop(appContext)
            }
        }
    }

    fun clearConversation() {
        scope.launch {
            chatRepository.clearHistory()
            _pendingAction.value = null
        }
    }

    fun requestPermissions(permissions: Array<String>) {
        _permissionRequests.tryEmit(permissions)
    }

    fun onPermissionsUpdated(results: Map<String, Boolean>) {
        _permissionHealth.value = computePermissionHealth()

        val pendingSms = pendingSmsToSendAfterPermission
        if (pendingSms != null && hasPermission(Manifest.permission.SEND_SMS)) {
            pendingSmsToSendAfterPermission = null
            _pendingAction.value = pendingSms
            confirmPendingSms(pendingSms.messageBody)
        }

        val retryCmd = lastCommandToRetryAfterPermission
        if (retryCmd != null && results.values.any { it }) {
            lastCommandToRetryAfterPermission = null
            submitUserInput(retryCmd, fromVoice = false)
        }

        ensureBackgroundWakeServiceIfEnabled()
    }

    fun refreshPermissionHealth() {
        _permissionHealth.value = computePermissionHealth()
        ensureBackgroundWakeServiceIfEnabled()
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun computePermissionHealth(): Map<String, Boolean> {
        val map = linkedMapOf(
            Manifest.permission.RECORD_AUDIO to hasPermission(Manifest.permission.RECORD_AUDIO),
            Manifest.permission.READ_CONTACTS to hasPermission(Manifest.permission.READ_CONTACTS),
            Manifest.permission.CALL_PHONE to hasPermission(Manifest.permission.CALL_PHONE),
            Manifest.permission.SEND_SMS to hasPermission(Manifest.permission.SEND_SMS),
            Manifest.permission.CAMERA to hasPermission(Manifest.permission.CAMERA)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            map[Manifest.permission.POST_NOTIFICATIONS] =
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        }
        return map
    }

    companion object {
        private const val TAG = "MaxAssistantCtrl"

        @Volatile
        private var INSTANCE: MaxAssistantController? = null

        fun getInstance(context: Context): MaxAssistantController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MaxAssistantController(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
