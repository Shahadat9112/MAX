package com.example.data

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatMessageDao) {
    val messages: Flow<List<ChatMessageEntity>> = dao.observeAllMessages()

    suspend fun addMessage(
        isUser: Boolean,
        text: String,
        language: String = "en",
        actionBadge: String? = null,
        isOffline: Boolean = false,
        isError: Boolean = false
    ) {
        dao.insertMessage(
            ChatMessageEntity(
                isUser = isUser,
                text = text,
                language = language,
                actionBadge = actionBadge,
                isOffline = isOffline,
                isError = isError
            )
        )
    }

    suspend fun getRecentContext(limit: Int = 16): List<ChatMessageEntity> {
        return dao.getRecentMessages(limit).reversed()
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }
}
