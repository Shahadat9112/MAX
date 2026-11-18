package com.example

import com.example.data.ChatMessageDao
import com.example.data.ChatMessageEntity
import com.example.data.ChatRepository
import com.example.domain.PrivacyGuard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyGuardTest {

    @Test
    fun sanitizeForExternalAi_redactsPhonesEmailsOtpsAndCards() {
        val raw = "Call +880 1712-345678, email me at rahim.user@example.com, " +
            "my OTP is 482910 and password: SecretPass99, card 4532 1234 5678 9012"
        val sanitized = PrivacyGuard.sanitizeForExternalAi(raw)

        assertFalse(sanitized.contains("1712"))
        assertFalse(sanitized.contains("rahim.user@example.com"))
        assertFalse(sanitized.contains("482910"))
        assertFalse(sanitized.contains("SecretPass99"))
        assertFalse(sanitized.contains("4532"))

        assertTrue(sanitized.contains("[PHONE_REDACTED]"))
        assertTrue(sanitized.contains("[EMAIL_REDACTED]"))
        assertTrue(sanitized.contains("[SECRET_REDACTED]"))
        assertTrue(sanitized.contains("[ACCOUNT_REDACTED]"))
    }

    @Test
    fun shouldExcludeFromCloudHistory_isolatesPrivateDeviceActionsAndOfflineMessages() {
        assertTrue(PrivacyGuard.shouldExcludeFromCloudHistory("Confirm Call", isOffline = false, isError = false))
        assertTrue(PrivacyGuard.shouldExcludeFromCloudHistory("Review SMS", isOffline = false, isError = false))
        assertTrue(PrivacyGuard.shouldExcludeFromCloudHistory("Contacts", isOffline = false, isError = false))
        assertTrue(PrivacyGuard.shouldExcludeFromCloudHistory("Photo Saved", isOffline = false, isError = false))
        assertTrue(PrivacyGuard.shouldExcludeFromCloudHistory("Volume 70%", isOffline = false, isError = false))
        assertTrue(PrivacyGuard.shouldExcludeFromCloudHistory(null, isOffline = true, isError = false))
        assertTrue(PrivacyGuard.shouldExcludeFromCloudHistory(null, isOffline = false, isError = true))

        assertFalse(PrivacyGuard.shouldExcludeFromCloudHistory(null, isOffline = false, isError = false))
    }

    @Test
    fun wipeBuffer_zeroesAudioArraysInRam() {
        val bytes = byteArrayOf(12, 34, -56, 78)
        val shorts = shortArrayOf(1200, -3400, 560)

        PrivacyGuard.wipeBuffer(bytes)
        PrivacyGuard.wipeBuffer(shorts)

        assertArrayEquals(byteArrayOf(0, 0, 0, 0), bytes)
        assertArrayEquals(shortArrayOf(0, 0, 0), shorts)
        assertEquals(4, bytes.size)
    }

    @Test
    fun chatRepository_doesNotPersistToDiskWithoutConsent_andPurgesOnRevoke() = runBlocking {
        val fakeDao = FakeChatMessageDao()
        val repo = ChatRepository(fakeDao)

        // Default: storageConsent = false -> zero DAO writes
        repo.addMessage(isUser = true, text = "Hello MAX")
        assertEquals(0, fakeDao.stored.value.size)
        assertEquals(1, repo.messages.first().size)

        // Grant consent -> persists to DAO
        repo.setStorageConsent(true)
        assertEquals(1, fakeDao.stored.value.size)
        assertEquals("Hello MAX", fakeDao.stored.value.first().text)

        // Revoke consent -> immediately purges persisted DAO rows
        repo.setStorageConsent(false)
        assertEquals(0, fakeDao.stored.value.size)
        assertEquals(0, repo.messages.first().size)
    }

    private class FakeChatMessageDao : ChatMessageDao {
        val stored = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

        override fun observeAllMessages(): Flow<List<ChatMessageEntity>> = stored

        override suspend fun getRecentMessages(limit: Int): List<ChatMessageEntity> =
            stored.value.takeLast(limit).reversed()

        override suspend fun insertMessage(message: ChatMessageEntity): Long {
            val id = (stored.value.maxOfOrNull { it.id } ?: 0L) + 1L
            stored.value = stored.value + message.copy(id = id)
            return id
        }

        override suspend fun clearAll() {
            stored.value = emptyList()
        }
    }
}
