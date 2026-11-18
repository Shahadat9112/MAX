package com.example.memory

import android.content.Context
import com.example.data.ChatMessageEntity
import com.example.data.ChatRepository
import com.example.data.MaxDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Independent manager handling consent-gated conversation memory.
 * Keeps chats strictly in volatile RAM unless the user grants on-device storage consent,
 * and supports instant local cache and database purging.
 */
class MemoryManager(context: Context) {
    private val appContext = context.applicationContext
    private val database = MaxDatabase.getInstance(appContext)
    private val chatRepository = ChatRepository(database.chatMessageDao())

    val messages: Flow<List<ChatMessageEntity>> = chatRepository.messages

    suspend fun setStorageConsent(consented: Boolean) {
        chatRepository.setStorageConsent(consented)
    }

    suspend fun addMessage(
        isUser: Boolean,
        text: String,
        language: String = "en",
        actionBadge: String? = null,
        isOffline: Boolean = false,
        isError: Boolean = false
    ) {
        chatRepository.addMessage(
            isUser = isUser,
            text = text,
            language = language,
            actionBadge = actionBadge,
            isOffline = isOffline,
            isError = isError
        )
    }

    suspend fun getRecentContext(limit: Int = 16): List<ChatMessageEntity> =
        chatRepository.getRecentContext(limit)

    suspend fun clearMemory() {
        chatRepository.clearHistory()
    }

    suspend fun purgeLocalCaches() {
        withContext(Dispatchers.IO) {
            runCatching {
                appContext.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
            }
        }
    }
}
