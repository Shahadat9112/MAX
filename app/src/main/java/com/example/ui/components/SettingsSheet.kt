package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.UserPreferences
import com.example.domain.VoiceLanguage
import com.example.ui.theme.AlertRed
import com.example.ui.theme.DarkBlueCard
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedBlueSurface
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SlateBlueSecondary
import com.example.ui.theme.SubtleBlueBorder
import com.example.ui.theme.SuccessMint
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsSheet(
    preferences: UserPreferences,
    isWakeServiceRunning: Boolean,
    wakeServiceStatusText: String,
    multiplePermissionsState: MultiplePermissionsState,
    onToggleBackgroundWake: (Boolean) -> Unit,
    onSelectLanguage: (VoiceLanguage) -> Unit,
    onToggleTts: () -> Unit,
    onToggleAutoListen: (Boolean) -> Unit,
    onToggleStrictPrivacy: (Boolean) -> Unit,
    onToggleSaveHistoryConsent: (Boolean) -> Unit,
    onRequestPermission: (String) -> Unit,
    onClearChat: () -> Unit,
    onEraseAllData: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DeepNavySurface,
        contentColor = IceBlueText
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "MAX AI Controls & Permissions",
                style = MaterialTheme.typography.headlineSmall,
                color = IceBlueText
            )
            Text(
                text = "Wake word is \"MAX\" or \"Hey MAX\".",
                style = MaterialTheme.typography.bodyMedium,
                color = NeonCyan
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Background & Screen-off Wake Word Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = DarkBlueCard,
                border = BorderStroke(1.dp, if (isWakeServiceRunning) ElectricBlue else SubtleBlueBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Hearing,
                            contentDescription = null,
                            tint = if (isWakeServiceRunning) NeonCyan else SlateBlueSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Background \"MAX\" Wake Word",
                                style = MaterialTheme.typography.titleMedium,
                                color = IceBlueText
                            )
                            Text(
                                text = if (isWakeServiceRunning) wakeServiceStatusText
                                else "Listen for \"MAX\" in background & with screen off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateBlueSecondary
                            )
                        }
                        Switch(
                            checked = isWakeServiceRunning || preferences.backgroundWakeWordEnabled,
                            onCheckedChange = onToggleBackgroundWake,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBlack,
                                checkedTrackColor = NeonCyan
                            ),
                            modifier = Modifier.testTag("wake_word_background_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    ) {
                        Text(
                            text = "Allow unrestricted battery for screen-off listening →",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Language Mode
            Text(
                text = "Voice & Response Language",
                style = MaterialTheme.typography.titleMedium,
                color = IceBlueText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                VoiceLanguage.entries.forEach { lang ->
                    val selected = preferences.preferredLanguage == lang
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectLanguage(lang) },
                        label = { Text(lang.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = ObsidianBlack,
                            containerColor = DarkBlueCard,
                            labelColor = IceBlueText
                        ),
                        modifier = Modifier.testTag("lang_chip_${lang.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Voice Output & Auto-Listen
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Natural Voice Output", style = MaterialTheme.typography.titleMedium, color = IceBlueText)
                    Text("Speak replies aloud in Bengali & English", style = MaterialTheme.typography.bodyMedium, color = SlateBlueSecondary)
                }
                Switch(
                    checked = preferences.ttsEnabled,
                    onCheckedChange = { onToggleTts() },
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBlack, checkedTrackColor = ElectricBlue)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hands-free Follow-up", style = MaterialTheme.typography.titleMedium, color = IceBlueText)
                    Text("Automatically listen again after MAX speaks", style = MaterialTheme.typography.bodyMedium, color = SlateBlueSecondary)
                }
                Switch(
                    checked = preferences.autoListenAfterReply,
                    onCheckedChange = onToggleAutoListen,
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBlack, checkedTrackColor = ElectricBlue)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Zero-Leak Privacy Shield Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = DarkBlueCard,
                border = BorderStroke(
                    1.dp,
                    if (preferences.strictPrivacyMode) SuccessMint.copy(alpha = 0.65f) else SubtleBlueBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (preferences.strictPrivacyMode) SuccessMint else SlateBlueSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Zero-Leak Privacy Shield",
                                style = MaterialTheme.typography.titleMedium,
                                color = IceBlueText
                            )
                            Text(
                                text = "Block background audio upload; auto-redact phone numbers, emails, OTPs & PINs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateBlueSecondary
                            )
                        }
                        Switch(
                            checked = preferences.strictPrivacyMode,
                            onCheckedChange = onToggleStrictPrivacy,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBlack,
                                checkedTrackColor = SuccessMint
                            ),
                            modifier = Modifier.testTag("strict_privacy_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Save Chat History on Device",
                                style = MaterialTheme.typography.titleSmall,
                                color = IceBlueText
                            )
                            Text(
                                text = "Off by default — nothing is saved to disk or published without your permission",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateBlueSecondary
                            )
                        }
                        Switch(
                            checked = preferences.saveHistoryConsent,
                            onCheckedChange = onToggleSaveHistoryConsent,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBlack,
                                checkedTrackColor = ElectricBlue
                            ),
                            modifier = Modifier.testTag("save_history_consent_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Zero silent storage: chats stay in volatile RAM unless you allow saving\n• Never published, shared, or backed up to cloud (allowBackup=false)\n• Contacts, Calls, SMS & Photos never leave your device\n• Raw audio buffers wiped from RAM immediately",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateBlueSecondary
                    )
                }
            }

            HorizontalDivider(color = SubtleBlueBorder, modifier = Modifier.padding(vertical = 12.dp))

            // Accompanist Permissions Checklist
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Device Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    color = IceBlueText
                )
                if (!multiplePermissionsState.allPermissionsGranted) {
                    TextButton(
                        onClick = { multiplePermissionsState.launchMultiplePermissionRequest() }
                    ) {
                        Text("Grant All", color = NeonCyan)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            multiplePermissionsState.permissions.forEach { permState ->
                val granted = permState.status.isGranted
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (granted) SuccessMint else SlateBlueSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = friendlyPermissionDescription(permState.permission),
                        style = MaterialTheme.typography.bodyLarge,
                        color = IceBlueText,
                        modifier = Modifier.weight(1f)
                    )
                    if (!granted) {
                        Button(
                            onClick = { onRequestPermission(permState.permission) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElevatedBlueSurface,
                                contentColor = NeonCyan
                            )
                        ) {
                            Text("Grant")
                        }
                    } else {
                        Text(
                            text = "Granted",
                            style = MaterialTheme.typography.labelLarge,
                            color = SuccessMint
                        )
                    }
                }
            }

            HorizontalDivider(color = SubtleBlueBorder, modifier = Modifier.padding(vertical = 12.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        onEraseAllData()
                        onClearChat()
                    },
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f)),
                    modifier = Modifier.testTag("erase_all_data_button")
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = AlertRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Erase All Data", color = AlertRed)
                }

                TextButton(onClick = onDismiss) {
                    Text("Done", color = NeonCyan)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
