package com.example.conversation

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
