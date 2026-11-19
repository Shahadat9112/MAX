package com.example.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Conversation lifecycle states for MAX.
 */
enum class ConversationState {
    IDLE,
    GREETING,
    LISTENING,
    THINKING,
    SPEAKING,
    AWAITING_FOLLOW_UP,
    FAREWELL,
    IDLE_TIMEOUT
}

/**
 * Independent manager coordinating MAX's conversation flow, identity addressing "Sanju" (সানজু),
 * task completion prompts, and idle timeouts.
 */
class ConversationManager {
    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _lastUtterance = MutableStateFlow("")
    val lastUtterance: StateFlow<String> = _lastUtterance.asStateFlow()

    fun updateState(state: ConversationState) {
        _conversationState.value = state
    }

    fun startGreeting(): String {
        _conversationState.value = ConversationState.GREETING
        val greeting = GreetingManager.getInitialGreeting()
        _lastUtterance.value = greeting
        return greeting
    }

    fun getTaskCompletedPrompt(): String {
        _conversationState.value = ConversationState.AWAITING_FOLLOW_UP
        val prompt = FarewellManager.getFollowUpPrompt()
        _lastUtterance.value = prompt
        return prompt
    }

    fun getFarewellMessage(): String {
        _conversationState.value = ConversationState.FAREWELL
        val farewell = FarewellManager.getFarewell()
        _lastUtterance.value = farewell
        return farewell
    }

    fun getIdleTimeoutMessage(): String {
        _conversationState.value = ConversationState.IDLE_TIMEOUT
        val timeout = FarewellManager.getIdleTimeoutMessage()
        _lastUtterance.value = timeout
        return timeout
    }

    fun reset() {
        _conversationState.value = ConversationState.IDLE
    }
}
