package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.configureAppCheck
import com.example.service.VoiceActivationService
import com.example.ui.MaxAssistantScreen
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.components.maxCorePermissionsList
import com.example.ui.theme.MaxAiTheme
import com.example.viewmodel.MaxAssistantViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Single-activity Jetpack Compose entry point hosting the MAX AI interface,
 * runtime permission orchestration, and wake-word launch intent handling.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MaxAssistantViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        handleWakeIntent(intent)

        setContent {
            MaxAiTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                var hasRequestedPermissionsBefore by remember { mutableStateOf(false) }
                var rationalePermission by remember { mutableStateOf<PermissionState?>(null) }

                val multiplePermissionsState = rememberMultiplePermissionsState(
                    permissions = maxCorePermissionsList,
                    onPermissionsResult = { results ->
                        hasRequestedPermissionsBefore = true
                        viewModel.onPermissionsUpdated(results)
                    }
                )

                // Launch Accompanist multi-permission prompt on initial launch if any permissions
                // are not yet granted, or ensure background MAX wake service starts immediately.
                LaunchedEffect(Unit) {
                    if (!multiplePermissionsState.allPermissionsGranted &&
                        !multiplePermissionsState.shouldShowRationale
                    ) {
                        hasRequestedPermissionsBefore = true
                        multiplePermissionsState.launchMultiplePermissionRequest()
                    } else {
                        viewModel.ensureBackgroundWakeService()
                    }
                }

                // Route controller permission requests through Accompanist PermissionState
                LaunchedEffect(Unit) {
                    viewModel.permissionRequests.collect { requested ->
                        val target = requested.firstOrNull()
                        val matchingState = multiplePermissionsState.permissions.firstOrNull {
                            it.permission == target
                        }
                        if (matchingState != null) {
                            if (matchingState.status.shouldShowRationale ||
                                (hasRequestedPermissionsBefore && !matchingState.status.isGranted)
                            ) {
                                rationalePermission = matchingState
                            } else if (!matchingState.status.isGranted) {
                                hasRequestedPermissionsBefore = true
                                matchingState.launchPermissionRequest()
                            }
                        } else {
                            hasRequestedPermissionsBefore = true
                            multiplePermissionsState.launchMultiplePermissionRequest()
                        }
                    }
                }

                MaxAssistantScreen(
                    uiState = uiState,
                    multiplePermissionsState = multiplePermissionsState,
                    hasRequestedPermissionsBefore = hasRequestedPermissionsBefore,
                    onRequestAllMissingPermissions = {
                        hasRequestedPermissionsBefore = true
                        multiplePermissionsState.launchMultiplePermissionRequest()
                    },
                    onToggleMic = viewModel::toggleMicrophone,
                    onSendText = { text -> viewModel.submitUserInput(text, fromVoice = false) },
                    onToggleTts = viewModel::toggleTts,
                    onSelectLanguage = viewModel::setLanguage,
                    onCycleLanguage = viewModel::cycleLanguage,
                    onToggleAutoListen = viewModel::setAutoListen,
                    onToggleStrictPrivacy = viewModel::setStrictPrivacyMode,
                    onToggleSaveHistoryConsent = viewModel::setSaveHistoryConsent,
                    onToggleBackgroundWake = viewModel::toggleBackgroundWakeWord,
                    onOpenCamera = { viewModel.openCameraScreen(autoCapture = false) },
                    onPhotoCaptured = viewModel::onPhotoSaved,
                    onCameraError = viewModel::onCameraError,
                    onCloseCamera = viewModel::closeCamera,
                    onConfirmCall = viewModel::confirmPendingCall,
                    onConfirmSms = viewModel::confirmPendingSms,
                    onReadSmsAloud = viewModel::readSmsAloud,
                    onCancelPending = viewModel::cancelPendingAction,
                    onRequestPermission = { perm -> viewModel.requestPermissions(arrayOf(perm)) },
                    onClearChat = viewModel::clearConversation,
                    onEraseAllData = viewModel::eraseAllLocalData,
                    onDismissBannerError = viewModel::dismissBannerError
                )

                rationalePermission?.let { permState ->
                    PermissionRationaleDialog(
                        permissionState = permState,
                        onDismiss = { rationalePermission = null }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionHealth()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeIntent(intent)
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.action == VoiceActivationService.ACTION_WAKE_TRIGGERED) {
            val command = intent.getStringExtra(VoiceActivationService.EXTRA_WAKE_COMMAND).orEmpty()
            intent.action = null
            viewModel.handleWakeWordTriggered(command)
        }
    }
}
