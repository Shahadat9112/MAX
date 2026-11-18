package com.example.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AssistantStatus
import com.example.domain.VoiceLanguage
import com.example.ui.components.AccompanistPermissionBanner
import com.example.ui.components.CameraCaptureOverlay
import com.example.ui.components.ChatArea
import com.example.ui.components.ConfirmationCard
import com.example.ui.components.MaxAiOrb
import com.example.ui.components.SettingsSheet
import com.example.ui.theme.AlertRed
import com.example.ui.theme.DarkBlueCard
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SlateBlueSecondary
import com.example.ui.theme.SubtleBlueBorder
import com.example.ui.theme.WarningAmber
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MaxAssistantScreen(
    uiState: MaxUiState,
    multiplePermissionsState: MultiplePermissionsState,
    hasRequestedPermissionsBefore: Boolean,
    onRequestAllMissingPermissions: () -> Unit,
    onToggleMic: () -> Unit,
    onSendText: (String) -> Unit,
    onToggleTts: () -> Unit,
    onSelectLanguage: (VoiceLanguage) -> Unit,
    onCycleLanguage: () -> Unit,
    onToggleAutoListen: (Boolean) -> Unit,
    onToggleStrictPrivacy: (Boolean) -> Unit,
    onToggleSaveHistoryConsent: (Boolean) -> Unit,
    onToggleBackgroundWake: (Boolean) -> Unit,
    onOpenCamera: () -> Unit,
    onPhotoCaptured: (Uri) -> Unit,
    onCameraError: (String) -> Unit,
    onCloseCamera: () -> Unit,
    onConfirmCall: () -> Unit,
    onConfirmSms: (String) -> Unit,
    onReadSmsAloud: (String, String) -> Unit,
    onCancelPending: () -> Unit,
    onRequestPermission: (String) -> Unit,
    onClearChat: () -> Unit,
    onEraseAllData: () -> Unit,
    onDismissBannerError: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianBlack,
                        DeepNavySurface,
                        ObsidianBlack
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                MaxTopBar(
                    uiState = uiState,
                    onToggleWake = { onToggleBackgroundWake(!uiState.isWakeServiceRunning) },
                    onToggleTts = onToggleTts,
                    onOpenCamera = onOpenCamera,
                    onOpenSettings = { showSettings = true }
                )
            },
            bottomBar = {
                MaxBottomDock(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    status = uiState.status,
                    onSendClick = {
                        if (inputText.isNotBlank()) {
                            onSendText(inputText)
                            inputText = ""
                        }
                    },
                    onMicClick = onToggleMic
                )
            }
        ) { innerPadding ->
            BoxWithConstraints(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val isWide = maxWidth >= 720.dp

                if (isWide) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 1100.dp)
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(0.44f)
                                .fillMaxHeight()
                                .padding(12.dp)
                        ) {
                            MaxAiOrb(
                                status = uiState.status,
                                audioLevel = uiState.audioLevel,
                                preferredLanguage = uiState.preferences.preferredLanguage,
                                activeSpeechLocale = uiState.activeSpeechLocale,
                                onCycleLanguage = onCycleLanguage,
                                orbSize = 210.dp,
                                onOrbClick = onToggleMic
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            AccompanistPermissionBanner(
                                multiplePermissionsState = multiplePermissionsState,
                                hasRequestedPermissionsBefore = hasRequestedPermissionsBefore,
                                onRequestPermissions = onRequestAllMissingPermissions
                            )
                            uiState.pendingAction?.let { pending ->
                                ConfirmationCard(
                                    pendingAction = pending,
                                    onConfirmCall = onConfirmCall,
                                    onConfirmSms = onConfirmSms,
                                    onReadSmsAloud = onReadSmsAloud,
                                    onCancel = onCancelPending,
                                    onGrantPermission = onRequestPermission
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(0.56f)
                                .fillMaxHeight()
                        ) {
                            ErrorBanner(uiState.bannerError, onDismissBannerError)
                            ChatArea(
                                messages = uiState.messages,
                                partialTranscript = uiState.partialTranscript,
                                onQuickPromptClick = onSendText,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxSize()
                    ) {
                        ErrorBanner(uiState.bannerError, onDismissBannerError)

                        AccompanistPermissionBanner(
                            multiplePermissionsState = multiplePermissionsState,
                            hasRequestedPermissionsBefore = hasRequestedPermissionsBefore,
                            onRequestPermissions = onRequestAllMissingPermissions
                        )

                        MaxAiOrb(
                            status = uiState.status,
                            audioLevel = uiState.audioLevel,
                            preferredLanguage = uiState.preferences.preferredLanguage,
                            activeSpeechLocale = uiState.activeSpeechLocale,
                            onCycleLanguage = onCycleLanguage,
                            orbSize = if (uiState.pendingAction != null) 140.dp else 168.dp,
                            onOrbClick = onToggleMic,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )

                        AnimatedVisibility(
                            visible = uiState.pendingAction != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            uiState.pendingAction?.let { pending ->
                                ConfirmationCard(
                                    pendingAction = pending,
                                    onConfirmCall = onConfirmCall,
                                    onConfirmSms = onConfirmSms,
                                    onReadSmsAloud = onReadSmsAloud,
                                    onCancel = onCancelPending,
                                    onGrantPermission = onRequestPermission
                                )
                            }
                        }

                        ChatArea(
                            messages = uiState.messages,
                            partialTranscript = uiState.partialTranscript,
                            onQuickPromptClick = onSendText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (uiState.isCameraOpen) {
            CameraCaptureOverlay(
                autoCapture = uiState.autoCaptureOnCameraOpen,
                lastCapturedUri = uiState.lastCapturedPhotoUri,
                onPhotoCaptured = onPhotoCaptured,
                onError = onCameraError,
                onClose = onCloseCamera
            )
        }

        if (showSettings) {
            SettingsSheet(
                preferences = uiState.preferences,
                isWakeServiceRunning = uiState.isWakeServiceRunning,
                wakeServiceStatusText = uiState.wakeServiceStatusText,
                multiplePermissionsState = multiplePermissionsState,
                onToggleBackgroundWake = onToggleBackgroundWake,
                onSelectLanguage = onSelectLanguage,
                onToggleTts = onToggleTts,
                onToggleAutoListen = onToggleAutoListen,
                onToggleStrictPrivacy = onToggleStrictPrivacy,
                onToggleSaveHistoryConsent = onToggleSaveHistoryConsent,
                onRequestPermission = onRequestPermission,
                onClearChat = onClearChat,
                onEraseAllData = onEraseAllData,
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
private fun MaxTopBar(
    uiState: MaxUiState,
    onToggleWake: () -> Unit,
    onToggleTts: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "MAX AI",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = IceBlueText
            )

            Surface(
                shape = RoundedCornerShape(50),
                color = if (uiState.isWakeServiceRunning) ElectricBlue.copy(alpha = 0.2f) else DarkBlueCard,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (uiState.isWakeServiceRunning) NeonCyan else SubtleBlueBorder
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggleWake)
                    .testTag("wake_badge_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Hearing,
                        contentDescription = "Wake word toggle",
                        tint = if (uiState.isWakeServiceRunning) NeonCyan else SlateBlueSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (uiState.isWakeServiceRunning) "MAX • ON" else "Wake: \"MAX\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isWakeServiceRunning) NeonCyan else SlateBlueSecondary
                    )
                }
            }

            if (!uiState.isOnline) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = WarningAmber.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.6f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudOff,
                            contentDescription = "Offline mode",
                            tint = WarningAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleTts,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("tts_toggle_button")
            ) {
                Icon(
                    imageVector = if (uiState.preferences.ttsEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    contentDescription = stringResource(R.string.toggle_voice),
                    tint = if (uiState.preferences.ttsEnabled) NeonCyan else SlateBlueSecondary
                )
            }
            IconButton(
                onClick = onOpenCamera,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("camera_quick_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = stringResource(R.string.open_camera),
                    tint = IceBlueText
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.open_settings),
                    tint = IceBlueText
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(error: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = !error.isNullOrBlank()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AlertRed.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IceBlueText,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = NeonCyan)
                }
            }
        }
    }
}

@Composable
private fun MaxBottomDock(
    inputText: String,
    onInputChange: (String) -> Unit,
    status: AssistantStatus,
    onSendClick: () -> Unit,
    onMicClick: () -> Unit
) {
    val isListening = status == AssistantStatus.LISTENING

    Surface(
        color = DeepNavySurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, SubtleBlueBorder.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.input_placeholder),
                        color = SlateBlueSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = SubtleBlueBorder,
                    focusedTextColor = IceBlueText,
                    unfocusedTextColor = IceBlueText,
                    focusedContainerColor = DarkBlueCard,
                    unfocusedContainerColor = DarkBlueCard,
                    cursorColor = NeonCyan
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                trailingIcon = {
                    if (inputText.isNotBlank()) {
                        IconButton(
                            onClick = onSendClick,
                            modifier = Modifier.testTag("send_text_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.send_message),
                                tint = NeonCyan
                            )
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_text_input")
            )

            FloatingActionButton(
                onClick = onMicClick,
                shape = CircleShape,
                containerColor = if (isListening) AlertRed else ElectricBlue,
                contentColor = if (isListening) IceBlueText else ObsidianBlack,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .size(56.dp)
                    .testTag("mic_fab_button")
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (isListening) {
                        stringResource(R.string.stop_listening)
                    } else {
                        stringResource(R.string.voice_command)
                    },
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
