package com.example.core

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.ChatMessageEntity
import com.example.data.UserPreferences
import com.example.domain.AiDecision
import com.example.domain.AssistantActionType
import com.example.domain.AssistantStatus
import com.example.domain.ContactMatch
import com.example.domain.PendingAction
import com.example.domain.PrivacyGuard
import com.example.domain.VoiceLanguage
import com.example.memory.MemoryManager
import com.example.permissions.PermissionManager
import com.example.repository.AssistantRepository
import com.example.settings.SettingsManager
import com.example.speech.SpeechManager
import com.example.conversation.ConversationManager
import com.example.conversation.ConversationState
import com.example.conversation.FarewellManager
import com.example.conversation.GreetingManager
import com.example.emotion.EmotionManager
import com.example.tts.SpeechStyle
import com.example.tts.TTSManagerWrapper
import com.example.tts.VoiceState
import com.example.ui.MaxUiState
import com.example.utils.PrivacyUtils
import com.example.wakeword.VoiceConversationConnector
import com.example.wakeword.WakeWordConfig
import com.example.wakeword.WakeWordDetectionEvent
import com.example.wakeword.WakeWordListener
import com.example.wakeword.WakeWordManager
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Central application-scoped assistant coordinator that composes all independent
 * MAX managers (WakeWord, Speech, TTS, Memory, Permission, Settings) and the AssistantRepository.
 *
 * Serves as the single source of truth for both the MVVM ViewModel layer and background services.
 */
class MAXCore private constructor(
    val wakeWordManager: WakeWordManager,
    val speechManager: SpeechManager,
    val ttsManager: TTSManagerWrapper,
    val memoryManager: MemoryManager,
    val permissionManager: PermissionManager,
    val settingsManager: SettingsManager,
    val repository: AssistantRepository,
    val emotionManager: EmotionManager,
    val conversationManager: ConversationManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isThinking = MutableStateFlow(false)
    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    private val _isCameraOpen = MutableStateFlow(false)
    private val _autoCapture = MutableStateFlow(false)
    private val _lastPhotoUri = MutableStateFlow<Uri?>(null)
    private val _permissionHealth = MutableStateFlow(permissionManager.computePermissionHealth())
    private val _bannerError = MutableStateFlow<String?>(null)

    private val _permissionRequests = MutableSharedFlow<Array<String>>(extraBufferCapacity = 4)
    val permissionRequests: SharedFlow<Array<String>> = _permissionRequests.asSharedFlow()

    private val _wakeDetectionEvents = MutableSharedFlow<WakeWordDetectionEvent>(extraBufferCapacity = 4)
    val wakeDetectionEvents: SharedFlow<WakeWordDetectionEvent> = _wakeDetectionEvents.asSharedFlow()

    private var lastCommandToRetryAfterPermission: String? = null
    private var pendingSmsToSendAfterPermission: PendingAction.ConfirmSms? = null
    private var pendingVoiceFollowUpAfterTts = false

    private val wakeListener = object : WakeWordListener {
        override fun onWakeWordDetected(event: WakeWordDetectionEvent) {
            _wakeDetectionEvents.tryEmit(event)
            prepareAssistantForWakeResponse(event)
        }
    }

    private val conversationListener = object : com.example.wakeword.VoiceConversationConnector {
        override fun onReadyForConversation(event: WakeWordDetectionEvent) {
            val greeting = conversationManager.startGreeting()
            scope.launch {
                respondAsMax(greeting, "bn", actionBadge = "MAX Greeting")
            }
        }

        override fun onSpeechRecognitionRequested(triggerSource: String) {
            startVoiceListening()
        }

        override fun onConversationEnded() {
            val farewell = conversationManager.getFarewellMessage()
            scope.launch {
                respondAsMax(farewell, "bn", actionBadge = "MAX Farewell")
            }
            conversationManager.reset()
        }
    }

    val uiState: StateFlow<MaxUiState> = combine(
        memoryManager.messages,
        settingsManager.preferencesFlow,
        repository.isOnline,
        speechManager.isListening,
        speechManager.partialText,
        speechManager.audioLevel,
        speechManager.activeLocaleTag,
        ttsManager.isSpeaking,
        _isThinking,
        _pendingAction,
        _isCameraOpen,
        _autoCapture,
        _lastPhotoUri,
        wakeWordManager.isServiceRunning,
        wakeWordManager.lastHeardStatus,
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

        wakeWordManager.setForegroundMicBusy(listening || speaking)

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
        initialValue = MaxUiState(permissionHealth = permissionManager.computePermissionHealth())
    )

    init {
        wakeWordManager.addListener(wakeListener)
        wakeWordManager.attachConversationConnector(conversationListener)

        scope.launch {
            settingsManager.preferencesFlow
                .map { it.saveHistoryConsent }
                .distinctUntilChanged()
                .collect { consented ->
                    memoryManager.setStorageConsent(consented)
                }
        }

        ttsManager.onSpeechCompleted = {
            scope.launch {
                val shouldListen = pendingVoiceFollowUpAfterTts ||
                    uiState.value.preferences.autoListenAfterReply
                pendingVoiceFollowUpAfterTts = false
                if (shouldListen && !_isCameraOpen.value) {
                    startVoiceListening()
                } else {
                    if (conversationManager.conversationState.value == ConversationState.AWAITING_FOLLOW_UP) {
                        // Wait for user response; if idle timeout occurs, farewell plays
                        mainHandler.postDelayed({
                            scope.launch {
                                if (conversationManager.conversationState.value == ConversationState.AWAITING_FOLLOW_UP) {
                                    val timeoutMsg = conversationManager.getIdleTimeoutMessage()
                                    respondAsMax(timeoutMsg, "bn", actionBadge = "Idle Timeout")
                                    wakeWordManager.notifyConversationEnded()
                                }
                            }
                        }, 12_000L)
                    } else {
                        wakeWordManager.notifyConversationEnded()
                    }
                }
            }
        }
    }

    /**
     * Attaches a future real-time [VoiceConversationConnector] to wake-word triggers.
     */
    fun attachVoiceConversationConnector(connector: VoiceConversationConnector) {
        wakeWordManager.attachConversationConnector(connector)
    }

    fun ensureBackgroundWakeServiceIfEnabled() {
        val prefs = uiState.value.preferences
        wakeWordManager.ensureRunningIfPermitted(
            enabled = prefs.backgroundWakeWordEnabled,
            hasRecordAudioPermission = permissionManager.hasMicrophonePermission()
        )
    }

    fun handleWakeWordTriggered(command: String) {
        if (command.isBlank()) {
            val greeting = conversationManager.startGreeting()
            scope.launch {
                respondAsMax(greeting, "bn", actionBadge = "MAX Greeting")
            }
        } else {
            wakeWordManager.dispatchWakeDetected(
                WakeWordDetectionEvent(
                    wakeWord = WakeWordConfig.DEFAULT_WAKE_WORD,
                    trailingCommand = command
                )
            )
        }
    }

    private fun prepareAssistantForWakeResponse(event: WakeWordDetectionEvent) {
        scope.launch {
            _bannerError.value = null
            wakeWordManager.setForegroundMicBusy(true)
            val command = event.trailingCommand
            if (command.isBlank()) {
                val greeting = conversationManager.startGreeting()
                respondAsMax(greeting, "bn", actionBadge = "MAX Greeting")
            } else {
                submitUserInput(command, fromVoice = true)
            }
        }
    }

    fun toggleMicrophone() {
        if (uiState.value.status == AssistantStatus.LISTENING) {
            speechManager.stopListening()
        } else {
            ttsManager.stop()
            startVoiceListening()
        }
    }

    fun startVoiceListening() {
        if (!permissionManager.hasMicrophonePermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        _bannerError.value = null
        ttsManager.stop()
        wakeWordManager.setForegroundMicBusy(true)

        val prefs = uiState.value.preferences
        val preferOffline = !repository.isNetworkConnected()

        mainHandler.postDelayed({
            speechManager.startListening(
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
                if (!repository.isNetworkConnected()) {
                    _bannerError.value = "Speech recognition unavailable offline without an offline speech pack."
                    return@launch
                }
                val history = memoryManager.getRecentContext(limit = 16)
                val (transcript, decision) = repository.requestAudioAiDecision(
                    wavBytes = wavBytes,
                    recentHistory = history,
                    preferredLanguage = uiState.value.preferences.preferredLanguage
                )

                val cleanTranscript = wakeWordManager.stripOptionalWakeWord(transcript)
                val currentPending = _pendingAction.value
                if (currentPending != null && handlePendingFlowInput(currentPending, cleanTranscript, fromVoice = true)) {
                    return@launch
                }

                val userLang = if (PrivacyUtils.isBengaliText(cleanTranscript)) "bn" else "en"
                memoryManager.addMessage(isUser = true, text = transcript, language = userLang)
                executeDecision(decision, isOffline = false, fromVoice = true)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Audio AI request timed out")
                memoryManager.addMessage(
                    isUser = false,
                    text = "Voice processing timed out. Please try again.",
                    isError = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio AI request failed (${e.javaClass.simpleName})")
                memoryManager.addMessage(
                    isUser = false,
                    text = "Voice AI error: ${e.localizedMessage ?: "Unable to process voice."}",
                    isError = true
                )
            } finally {
                PrivacyGuard.wipeBuffer(wavBytes)
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

        val cleanInput = wakeWordManager.stripOptionalWakeWord(trimmed)

        val currentPending = _pendingAction.value
        if (currentPending != null) {
            if (handlePendingFlowInput(currentPending, cleanInput, fromVoice)) {
                return
            }
        }

        scope.launch {
            emotionManager.evaluateAndSetEmotion(trimmed)
            val userLang = when {
                PrivacyUtils.isBengaliText(cleanInput) -> "bn"
                spokenLanguageHint == "bn" -> "bn"
                else -> "en"
            }
            memoryManager.addMessage(isUser = true, text = trimmed, language = userLang)

            _isThinking.value = true
            try {
                val localAction = repository.parseOfflineCommand(cleanInput)
                    ?.takeIf { it.action != AssistantActionType.NONE }
                if (localAction != null) {
                    executeDecision(
                        decision = localAction,
                        isOffline = !repository.isNetworkConnected(),
                        fromVoice = fromVoice
                    )
                    return@launch
                }

                val online = repository.isNetworkConnected()
                if (!online) {
                    val offlineDecision = repository.parseOfflineCommand(cleanInput)
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

                val history = memoryManager.getRecentContext(limit = 16)
                val decision = repository.requestAiDecision(
                    userInput = cleanInput,
                    recentHistory = history,
                    preferredLanguage = uiState.value.preferences.preferredLanguage
                )
                executeDecision(decision, isOffline = false, fromVoice = fromVoice)
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "AI request timed out after 45s", e)
                val offlineFallback = repository.parseOfflineCommand(cleanInput)
                if (offlineFallback != null) {
                    executeDecision(offlineFallback, isOffline = true, fromVoice = fromVoice)
                } else {
                    val timeoutMsg = if (userLang == "bn") {
                        "ইন্টারনেট বা এআই সার্ভার থেকে উত্তর আসতে দেরি হচ্ছে। অনুগ্রহ করে আবার চেষ্টা করুন।"
                    } else {
                        "MAX AI timed out waiting for a response. Please check your internet connection and try again."
                    }
                    memoryManager.addMessage(
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
                val offlineFallback = repository.parseOfflineCommand(cleanInput)
                if (offlineFallback != null) {
                    executeDecision(offlineFallback, isOffline = true, fromVoice = fromVoice)
                } else {
                    memoryManager.addMessage(
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
                            memoryManager.addMessage(isUser = true, text = input)
                            confirmPendingCall()
                        }
                        true
                    }
                    isNo -> {
                        scope.launch {
                            memoryManager.addMessage(isUser = true, text = input)
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
                        memoryManager.addMessage(isUser = true, text = input)
                        cancelPendingAction()
                    }
                    return true
                }
                scope.launch {
                    val lang = if (PrivacyUtils.isBengaliText(input)) "bn" else pending.language
                    memoryManager.addMessage(isUser = true, text = input, language = lang)
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
                            memoryManager.addMessage(isUser = true, text = input)
                            confirmPendingSms(pending.messageBody)
                        }
                        true
                    }
                    isNo -> {
                        scope.launch {
                            memoryManager.addMessage(isUser = true, text = input)
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
                val msg = repository.getCurrentTimeMessage(isBn)
                respondAsMax(msg, lang, actionBadge = "Clock", isOffline = isOffline)
            }

            AssistantActionType.GET_BATTERY -> {
                val msg = repository.getBatteryStatusMessage(isBn)
                respondAsMax(msg, lang, actionBadge = "Battery", isOffline = isOffline)
            }

            AssistantActionType.TOGGLE_FLASHLIGHT -> {
                val res = repository.toggleFlashlight()
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
                val pct = repository.adjustVolume(AudioManager.ADJUST_RAISE)
                val msg = if (isBn) "ভলিউম বাড়িয়ে $pct% করা হয়েছে।" else "Volume increased to $pct%."
                respondAsMax(msg, lang, actionBadge = "Volume $pct%", isOffline = isOffline)
            }

            AssistantActionType.VOLUME_DOWN -> {
                val pct = repository.adjustVolume(AudioManager.ADJUST_LOWER)
                val msg = if (isBn) "ভলিউম কমিয়ে $pct% করা হয়েছে।" else "Volume decreased to $pct%."
                respondAsMax(msg, lang, actionBadge = "Volume $pct%", isOffline = isOffline)
            }

            AssistantActionType.VOLUME_MUTE -> {
                repository.adjustVolume(AudioManager.ADJUST_MUTE)
                val msg = if (isBn) "মিডিয়া ভলিউম মিউট করা হয়েছে।" else "Media volume muted."
                respondAsMax(msg, lang, actionBadge = "Muted", isOffline = isOffline)
            }

            AssistantActionType.VOLUME_UNMUTE -> {
                val pct = repository.adjustVolume(AudioManager.ADJUST_UNMUTE)
                val msg = if (isBn) "মিডিয়া ভলিউম আনমিউট করা হয়েছে ($pct%)।" else "Media volume unmuted ($pct%)."
                respondAsMax(msg, lang, actionBadge = "Volume $pct%", isOffline = isOffline)
            }

            AssistantActionType.VOLUME_SET -> {
                val pct = repository.setVolumePercent(decision.volumePercent ?: 50)
                val msg = if (isBn) "ভলিউম $pct%-এ সেট করা হয়েছে।" else "Volume set to $pct%."
                respondAsMax(msg, lang, actionBadge = "Volume $pct%", isOffline = isOffline)
            }

            AssistantActionType.OPEN_SETTINGS -> {
                val opened = repository.openSettings(decision.targetName)
                val msg = if (opened) {
                    decision.reply.ifBlank { if (isBn) "সেটিংস খোলা হচ্ছে।" else "Opening phone settings." }
                } else {
                    if (isBn) "সেটিংস খোলা যায়নি।" else "Unable to open device settings."
                }
                respondAsMax(msg, lang, actionBadge = "Settings", isOffline = isOffline)
            }

            AssistantActionType.OPEN_APP -> {
                val target = decision.targetName.orEmpty()
                val res = repository.openInstalledApp(target)
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
                val isDirectNumber = PrivacyUtils.isPhoneNumberLiteral(target)

                if (!isDirectNumber && !permissionManager.hasContactsPermission()) {
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
                    repository.findContacts(target)
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
                val isDirectNumber = PrivacyUtils.isPhoneNumberLiteral(target)

                if (!isDirectNumber && !permissionManager.hasContactsPermission()) {
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
                    repository.findContacts(target)
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
                memoryManager.clearMemory()
                _pendingAction.value = null
            }
        }
    }

    fun openCameraScreen(autoCapture: Boolean, language: String = "en", isOffline: Boolean = false) {
        scope.launch {
            if (!permissionManager.hasCameraPermission()) {
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
            val result = repository.makeCall(pending.contact.phoneNumber)
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
        ttsManager.speak(text, language)
    }

    fun confirmPendingSms(editedBody: String) {
        val pending = _pendingAction.value as? PendingAction.ConfirmSms ?: return
        val finalBody = editedBody.trim()
        if (finalBody.isEmpty()) return

        if (!repository.hasSendSmsPermission()) {
            pendingSmsToSendAfterPermission = pending.copy(messageBody = finalBody)
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS))
            _bannerError.value = "Please grant SMS permission to send messages."
            return
        }

        _pendingAction.value = null
        scope.launch {
            val isBn = pending.language == "bn"
            val result = repository.sendSms(pending.contact.phoneNumber, finalBody)
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
        memoryManager.addMessage(
            isUser = false,
            text = text,
            language = language,
            actionBadge = actionBadge,
            isOffline = isOffline
        )
        if (uiState.value.preferences.ttsEnabled) {
            val style = emotionManager.responseStyle.value.speechStyle
            ttsManager.setSpeechStyle(style)
            ttsManager.speak(text, language)
        } else {
            pendingVoiceFollowUpAfterTts = false
        }

        // If this was a task response, queue follow-up asking Sanju if anything else is needed
        if (actionBadge != null && actionBadge != "MAX Greeting" && actionBadge != "MAX Farewell" && actionBadge != "Idle Timeout") {
            val followUp = conversationManager.getTaskCompletedPrompt()
            mainHandler.postDelayed({
                scope.launch {
                    memoryManager.addMessage(isUser = false, text = followUp, language = "bn", actionBadge = "Follow-up")
                    if (uiState.value.preferences.ttsEnabled) {
                        ttsManager.speak(followUp, "bn")
                    }
                }
            }, 2500L)
        }
    }

    fun toggleTts() {
        val current = uiState.value.preferences.ttsEnabled
        scope.launch {
            settingsManager.setTtsEnabled(!current)
            if (current) ttsManager.stop()
        }
    }

    fun setLanguage(language: VoiceLanguage) {
        scope.launch {
            settingsManager.setPreferredLanguage(language)
        }
    }

    fun cycleLanguage() {
        val current = uiState.value.preferences.preferredLanguage
        setLanguage(settingsManager.nextLanguage(current))
    }

    fun setAutoListen(enabled: Boolean) {
        scope.launch {
            settingsManager.setAutoListenAfterReply(enabled)
        }
    }

    fun setStrictPrivacyMode(enabled: Boolean) {
        scope.launch {
            settingsManager.setStrictPrivacyMode(enabled)
        }
    }

    fun setSaveHistoryConsent(consented: Boolean) {
        scope.launch {
            settingsManager.setSaveHistoryConsent(consented)
            memoryManager.setStorageConsent(consented)
        }
    }

    fun eraseAllLocalData() {
        scope.launch {
            memoryManager.clearMemory()
            _pendingAction.value = null
            _bannerError.value = null
            _lastPhotoUri.value = null
            pendingSmsToSendAfterPermission = null
            lastCommandToRetryAfterPermission = null
            memoryManager.purgeLocalCaches()
        }
    }

    fun toggleBackgroundWakeWord(enabled: Boolean) {
        scope.launch {
            if (enabled) {
                val needed = permissionManager.missingPermissionsForBackgroundWake()
                if (needed.isNotEmpty()) {
                    requestPermissions(needed.toTypedArray())
                    return@launch
                }
                settingsManager.setBackgroundWakeWordEnabled(true)
                wakeWordManager.startService()
            } else {
                settingsManager.setBackgroundWakeWordEnabled(false)
                wakeWordManager.stopService()
            }
        }
    }

    fun clearConversation() {
        scope.launch {
            memoryManager.clearMemory()
            _pendingAction.value = null
        }
    }

    fun requestPermissions(permissions: Array<String>) {
        _permissionRequests.tryEmit(permissions)
    }

    fun onPermissionsUpdated(results: Map<String, Boolean>) {
        _permissionHealth.value = permissionManager.computePermissionHealth()

        val pendingSms = pendingSmsToSendAfterPermission
        if (pendingSms != null && permissionManager.hasSmsPermission()) {
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
        _permissionHealth.value = permissionManager.computePermissionHealth()
        ensureBackgroundWakeServiceIfEnabled()
    }

    fun hasPermission(permission: String): Boolean = permissionManager.hasPermission(permission)

    companion object {
        private const val TAG = "MAXCore"

        @Volatile
        private var INSTANCE: MAXCore? = null

        fun getInstance(context: Context): MAXCore {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                INSTANCE ?: MAXCore(
                    wakeWordManager = WakeWordManager(appContext),
                    speechManager = SpeechManager(appContext),
                    ttsManager = TTSManagerWrapper(appContext),
                    memoryManager = MemoryManager(appContext),
                    permissionManager = PermissionManager(appContext),
                    settingsManager = SettingsManager(appContext),
                    repository = AssistantRepository(appContext),
                    emotionManager = EmotionManager(),
                    conversationManager = ConversationManager()
                ).also { INSTANCE = it }
            }
        }
    }
}
