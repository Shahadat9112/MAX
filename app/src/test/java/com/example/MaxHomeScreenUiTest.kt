package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.ChatMessageEntity
import com.example.data.UserPreferences
import com.example.domain.AssistantStatus
import com.example.domain.VoiceLanguage
import com.example.ui.MaxAssistantScreen
import com.example.ui.MaxUiState
import com.example.ui.theme.MaxAiTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalPermissionsApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class MaxHomeScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeMultiplePermissionsState : MultiplePermissionsState {
        override val allPermissionsGranted: Boolean = true
        override val permissions: List<PermissionState> = emptyList()
        override val revokedPermissions: List<PermissionState> = emptyList()
        override val shouldShowRationale: Boolean = false
        override fun launchMultiplePermissionRequest() {}
    }

    @Test
    fun redesignedHomeScreen_rendersHeaderOrbQuickActionsAndLastTask() {
        val sentCommands = mutableListOf<String>()
        var cameraOpened = false
        var micClicked = false

        val uiState = MaxUiState(
            status = AssistantStatus.IDLE,
            preferences = UserPreferences(preferredLanguage = VoiceLanguage.BENGALI),
            messages = listOf(
                ChatMessageEntity(
                    id = 1L,
                    text = "আলহামদুলিল্লাহ, আমি ভালো আছি সানজু।",
                    isUser = false,
                    actionBadge = "Reminder Set",
                    timestamp = System.currentTimeMillis()
                )
            )
        )

        composeRule.setContent {
            MaxAiTheme(darkTheme = true) {
                MaxAssistantScreen(
                    uiState = uiState,
                    multiplePermissionsState = FakeMultiplePermissionsState(),
                    hasRequestedPermissionsBefore = false,
                    onRequestAllMissingPermissions = {},
                    onToggleMic = { micClicked = true },
                    onSendText = { sentCommands.add(it) },
                    onToggleTts = {},
                    onSelectLanguage = {},
                    onCycleLanguage = {},
                    onToggleAutoListen = {},
                    onToggleStrictPrivacy = {},
                    onToggleSaveHistoryConsent = {},
                    onToggleBackgroundWake = {},
                    onOpenCamera = { cameraOpened = true },
                    onPhotoCaptured = {},
                    onCameraError = {},
                    onCloseCamera = {},
                    onConfirmCall = {},
                    onConfirmSms = {},
                    onReadSmsAloud = { _, _ -> },
                    onCancelPending = {},
                    onRequestPermission = {},
                    onClearChat = {},
                    onEraseAllData = {},
                    onDismissBannerError = {}
                )
            }
        }

        composeRule.onNodeWithTag("max_brand_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("home_greeting_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_orb_button").assertIsDisplayed()
        composeRule.onNodeWithTag("assistant_status_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_action_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_action_camera").assertIsDisplayed()
        composeRule.onNodeWithTag("last_completed_task_card").assertIsDisplayed()

        composeRule.onNodeWithTag("ai_orb_button").performClick()
        assertTrue(micClicked)

        composeRule.onNodeWithTag("quick_action_camera").performClick()
        assertTrue(cameraOpened)

        composeRule.onNodeWithTag("quick_action_gallery").performClick()
        assertEquals(listOf("MAX, গ্যালারি খোলো"), sentCommands)
    }
}
