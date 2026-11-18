package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.atomic.AtomicLong

/**
 * Consent-gated repository:
 * - When [storageConsent] is false (default), messages exist ONLY in volatile RAM for the active session
 *   and zero data is persisted to SQLite/disk.
 * - When the user explicitly grants consent, messages are persisted locally on-device (never published).
 * - Revoking consent immediately wipes all previously persisted rows from SQLite.
 */
class ChatRepository(private val dao: ChatMessageDao) {
    private val storageConsent = MutableStateFlow(false)
    private val volatileSessionMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private val ramIdCounter = AtomicLong(1L)

    val messages: Flow<List<ChatMessageEntity>> = combine(
        storageConsent,
        dao.observeAllMessages(),
        volatileSessionMessages
    ) { consented, dbMessages, ramMessages ->
        if (consented) dbMessages else ramMessages
    }

    suspend fun setStorageConsent(consented: Boolean) {
        val previous = storageConsent.value
        storageConsent.value = consented
        if (!consented) {
            // Purge any persisted database entries immediately when consent is absent or revoked
            dao.clearAll()
        } else if (!previous) {
            val pendingRam = volatileSessionMessages.value
            if (pendingRam.isNotEmpty()) {
                pendingRam.forEach { entity ->
                    dao.insertMessage(entity.copy(id = 0))
                }
                volatileSessionMessages.value = emptyList()
            }
        }
    }

    suspend fun addMessage(
        isUser: Boolean,
        text: String,
        language: String = "en",
        actionBadge: String? = null,
        isOffline: Boolean = false,
        isError: Boolean = false
    ) {
        val entity = ChatMessageEntity(
            id = if (storageConsent.value) 0 else ramIdCounter.getAndIncrement(),
            isUser = isUser,
            text = text,
            language = language,
            actionBadge = actionBadge,
            isOffline = isOffline,
            isError = isError
        )
        if (storageConsent.value) {
            dao.insertMessage(entity)
        } else {
            volatileSessionMessages.value = volatileSessionMessages.value + entity
        }
    }

    suspend fun getRecentContext(limit: Int = 16): List<ChatMessageEntity> {
        return if (storageConsent.value) {
            dao.getRecentMessages(limit).reversed()
        } else {
            volatileSessionMessages.value.takeLast(limit)
        }
    }

    suspend fun clearHistory() {
        volatileSessionMessages.value = emptyList()
        dao.clearAll()
    }
}
