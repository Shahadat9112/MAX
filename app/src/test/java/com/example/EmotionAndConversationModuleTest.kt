package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.conversation.ConversationManager
import com.example.conversation.ConversationState
import com.example.conversation.FarewellManager
import com.example.conversation.GreetingManager
import com.example.emotion.EmotionDetector
import com.example.emotion.EmotionManager
import com.example.emotion.EmotionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EmotionAndConversationModuleTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun emotionDetector_detectsCorrectMood() {
        assertEquals(EmotionState.HAPPY, EmotionDetector.detectEmotion("I am so happy and glad"))
        assertEquals(EmotionState.EXCITED, EmotionDetector.detectEmotion("Wow this is fantastic"))
        assertEquals(EmotionState.CARING, EmotionDetector.detectEmotion("I am feeling sad and worried"))
        assertEquals(EmotionState.SUPPORTIVE, EmotionDetector.detectEmotion("Can you please help me"))
        assertEquals(EmotionState.CALM, EmotionDetector.detectEmotion("What time is it"))
    }

    @Test
    fun emotionManager_evaluatesAndSetsResponseStyle() {
        val manager = EmotionManager()
        manager.evaluateAndSetEmotion("I need help")
        assertEquals(EmotionState.SUPPORTIVE, manager.currentEmotion.value)
        assertNotNull(manager.responseStyle.value)
    }

    @Test
    fun conversationManager_managesSanjuGreetingsAndFarewells() {
        val manager = ConversationManager()
        val greeting = manager.startGreeting()
        assertTrue(greeting.contains("সানজু"))
        assertTrue(greeting.contains("আসসালামু আলাইকুম") || greeting.contains("সানজু"))
        assertEquals(ConversationState.GREETING, manager.conversationState.value)

        val prompt = manager.getTaskCompletedPrompt()
        assertTrue(prompt.contains("সানজু"))
        assertEquals(ConversationState.AWAITING_FOLLOW_UP, manager.conversationState.value)

        val farewell = manager.getFarewellMessage()
        assertTrue(farewell.contains("সানজু"))
        assertTrue(farewell.contains("আল্লাহ হাফেজ") || farewell.contains("খোদা হাফেজ") || farewell.contains("শুভ রাত্রি"))
        assertEquals(ConversationState.FAREWELL, manager.conversationState.value)

        val timeout = manager.getIdleTimeoutMessage()
        assertTrue(timeout.contains("সানজু"))
        assertEquals(ConversationState.IDLE_TIMEOUT, manager.conversationState.value)
    }
}
