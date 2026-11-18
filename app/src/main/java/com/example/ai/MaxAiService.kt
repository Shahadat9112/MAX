package com.example.ai

import android.util.Log
import com.example.data.ChatMessageEntity
import com.example.domain.AiDecision
import com.example.domain.AssistantActionType
import com.example.domain.VoiceLanguage
import com.example.domain.WakeWordParser
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class MaxAiService {
    private val decisionSchema = Schema.obj(
        properties = mapOf(
            "transcript" to Schema.string(
                description = "Exact transcription of what the user spoke in the audio (only when audio is provided).",
                nullable = true
            ),
            "reply" to Schema.string(
                description = "Warm, natural, human-like spoken response in English or Bengali matching the user's language."
            ),
            "language" to Schema.enumeration(
                values = listOf("en", "bn"),
                description = "Primary language of reply: 'bn' for Bengali, 'en' for English."
            ),
            "action" to Schema.enumeration(
                values = AssistantActionType.entries.map { it.name },
                description = "Device or assistant action to trigger, or NONE for general conversation."
            ),
            "targetName" to Schema.string(
                description = "Contact name (for CALL_CONTACT or SEND_SMS), app name (for OPEN_APP), or settings section like wifi/bluetooth/sound/display/general (for OPEN_SETTINGS).",
                nullable = true
            ),
            "messageBody" to Schema.string(
                description = "Written SMS message body when action is SEND_SMS and user already included what to say.",
                nullable = true
            ),
            "volumePercent" to Schema.integer(
                description = "Optional target volume percentage 0..100 when action is VOLUME_SET.",
                nullable = true
            )
        ),
        optionalProperties = listOf("transcript", "targetName", "messageBody", "volumePercent")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = decisionSchema
                temperature = 0.65f
            },
            systemInstruction = content {
                text(
                    """
                    You are MAX, an intelligent, warm, human-like personal AI voice assistant built into an Android phone.
                    Your wake word is "MAX" or "Hey MAX".
                    
                    Language rules:
                    - Understand and reply fluently in both Bengali (বাংলা) and English (from text or spoken audio).
                    - If the user writes or speaks in Bengali (or Banglish clearly asking in Bengali), reply in natural conversational Bengali script with language = "bn".
                    - If the user communicates in English, reply in natural conversational English with language = "en".
                    - Keep replies concise, natural, and pleasant to hear spoken aloud (avoid markdown formatting, asterisks, or bullet symbols since replies are spoken via Text-to-Speech).
                    
                    Action routing rules:
                    - OPEN_APP: When user wants to open/launch an installed app (set targetName to app name, e.g., "YouTube", "WhatsApp", "Chrome").
                    - VOLUME_UP / VOLUME_DOWN / VOLUME_MUTE / VOLUME_UNMUTE / VOLUME_SET: For volume adjustments.
                    - OPEN_SETTINGS: When user wants to open device settings (targetName can be "wifi", "bluetooth", "sound", "display", "battery", "apps", or "general").
                    - CALL_CONTACT: When user asks to call someone (set targetName to the person or relationship like "Father", "Mom", "Rahim"). Tell the user you're looking up the contact to confirm before calling.
                    - SEND_SMS: When user asks to send an SMS/text message (set targetName to recipient; set messageBody if they provided the message text, or null if you need to ask them what to write).
                    - OPEN_CAMERA: When user asks to open the camera.
                    - TAKE_PHOTO: When user asks to take a picture / photo / selfie / snap.
                    - GET_TIME / GET_BATTERY / TOGGLE_FLASHLIGHT / CLEAR_CHAT: When requesting current time, battery status, flashlight/torch, or clearing chat history.
                    - NONE: For general questions, knowledge, coding help, advice, translation, jokes, and conversation.
                    
                    Always remember the ongoing conversation context provided in history.
                    """.trimIndent()
                )
            }
        )
    }

    suspend fun sendMessage(
        userInput: String,
        recentHistory: List<ChatMessageEntity>,
        preferredLanguage: VoiceLanguage
    ): AiDecision = withContext(Dispatchers.IO) {
        val historyContent = buildHistory(recentHistory)
        val languageHint = buildLanguageHint(preferredLanguage)

        val accumulated = StringBuilder()
        withTimeout(45_000L) {
            val chat = model.startChat(history = historyContent)
            chat.sendMessageStream(userInput + languageHint).collect { chunk ->
                chunk.text?.let { accumulated.append(it) }
            }
        }
        val responseText = accumulated.toString()
        if (responseText.isBlank()) {
            throw IllegalStateException("Empty response from MAX AI.")
        }

        parseDecision(responseText, userInput).second
    }

    suspend fun sendAudioMessage(
        wavBytes: ByteArray,
        recentHistory: List<ChatMessageEntity>,
        preferredLanguage: VoiceLanguage
    ): Pair<String, AiDecision> = withContext(Dispatchers.IO) {
        val historyContent = buildHistory(recentHistory)
        val languageHint = buildLanguageHint(preferredLanguage)

        val accumulated = StringBuilder()
        withTimeout(45_000L) {
            val chat = model.startChat(history = historyContent)
            chat.sendMessageStream(
                content {
                    inlineData(wavBytes, "audio/wav")
                    text(
                        "Listen to the user's spoken audio (Bengali or English). Put what they said in 'transcript', and respond appropriately.$languageHint"
                    )
                }
            ).collect { chunk ->
                chunk.text?.let { accumulated.append(it) }
            }
        }
        val responseText = accumulated.toString()
        if (responseText.isBlank()) {
            throw IllegalStateException("Empty response from MAX AI.")
        }

        parseDecision(responseText, "[Voice message]")
    }

    private fun buildHistory(recentHistory: List<ChatMessageEntity>) =
        recentHistory
            .filter { !it.isError && it.text.isNotBlank() }
            .takeLast(12)
            .map { msg ->
                val role = if (msg.isUser) "user" else "model"
                content(role) {
                    text(
                        if (msg.isUser) {
                            msg.text
                        } else {
                            JSONObject().apply {
                                put("reply", msg.text)
                                put("language", msg.language)
                                put("action", AssistantActionType.NONE.name)
                            }.toString()
                        }
                    )
                }
            }

    private fun buildLanguageHint(preferredLanguage: VoiceLanguage): String = when (preferredLanguage) {
        VoiceLanguage.BENGALI -> " [User preference: respond in Bengali unless asked otherwise]"
        VoiceLanguage.ENGLISH -> " [User preference: respond in English unless asked otherwise]"
        VoiceLanguage.AUTO -> ""
    }

    private fun parseDecision(jsonString: String, fallbackInput: String): Pair<String, AiDecision> {
        return try {
            val json = JSONObject(jsonString)
            val transcript = json.optString("transcript")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: fallbackInput
            val reply = json.optString("reply").ifBlank {
                "I'm here and ready to help."
            }
            val lang = json.optString("language").ifBlank {
                if (WakeWordParser.isBengaliText(reply) || WakeWordParser.isBengaliText(transcript)) "bn" else "en"
            }
            val actionName = json.optString("action", AssistantActionType.NONE.name)
            val action = runCatching { AssistantActionType.valueOf(actionName) }
                .getOrDefault(AssistantActionType.NONE)
            val targetName = json.optString("targetName").takeIf { it.isNotBlank() && it != "null" }
            val messageBody = json.optString("messageBody").takeIf { it.isNotBlank() && it != "null" }
            val volumePercent = if (json.has("volumePercent") && !json.isNull("volumePercent")) {
                json.optInt("volumePercent").coerceIn(0, 100)
            } else {
                null
            }

            transcript to AiDecision(
                reply = reply,
                language = lang,
                action = action,
                targetName = targetName,
                messageBody = messageBody,
                volumePercent = volumePercent
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON response: $jsonString", e)
            fallbackInput to AiDecision(
                reply = jsonString,
                language = if (WakeWordParser.isBengaliText(jsonString)) "bn" else "en",
                action = AssistantActionType.NONE
            )
        }
    }

    companion object {
        private const val TAG = "MaxAiService"
    }
}
