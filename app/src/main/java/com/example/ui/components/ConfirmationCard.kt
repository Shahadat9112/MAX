package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.PendingAction
import com.example.ui.theme.DarkBlueCard
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedBlueSurface
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SlateBlueSecondary
import com.example.ui.theme.SubtleBlueBorder
import com.example.ui.theme.SuccessMint

@Composable
fun ConfirmationCard(
    pendingAction: PendingAction,
    onConfirmCall: () -> Unit,
    onConfirmSms: (String) -> Unit,
    onReadSmsAloud: (String, String) -> Unit,
    onCancel: () -> Unit,
    onGrantPermission: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceVariant,
        border = BorderStroke(1.2.dp, ElectricBlue.copy(alpha = 0.7f)),
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("pending_confirmation_card")
    ) {
        when (pendingAction) {
            is PendingAction.ConfirmCall -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "Confirm Call",
                            tint = SuccessMint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Confirm Phone Call",
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface
                            )
                            Text(
                                text = "${pendingAction.contact.name} • ${pendingAction.contact.phoneNumber}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            border = BorderStroke(1.dp, SubtleBlueBorder),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cancel_call_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.cancel_action), color = SlateBlueSecondary)
                        }
                        Button(
                            onClick = onConfirmCall,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessMint,
                                contentColor = ObsidianBlack
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_call_button")
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.confirm_call))
                        }
                    }
                }
            }

            is PendingAction.AwaitSmsBody -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sms,
                        contentDescription = "Composing SMS",
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SMS to ${pendingAction.contact.name}",
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface
                            )
                            Text(
                                text = "Speak or type your message below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cancel SMS",
                                tint = scheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is PendingAction.ConfirmSms -> {
                    var editedText by remember(pendingAction.messageBody) {
                        mutableStateOf(pendingAction.messageBody)
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sms,
                                contentDescription = "Review SMS",
                                tint = scheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Confirm SMS to ${pendingAction.contact.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = scheme.onSurface
                                )
                                Text(
                                    text = pendingAction.contact.phoneNumber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onReadSmsAloud(editedText, pendingAction.language) },
                                modifier = Modifier.testTag("read_sms_aloud_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "Read SMS aloud",
                                    tint = ElectricBlue
                                )
                            }
                        }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        label = { Text("Message") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = SubtleBlueBorder,
                            focusedTextColor = IceBlueText,
                            unfocusedTextColor = IceBlueText,
                            focusedContainerColor = ElevatedBlueSurface.copy(alpha = 0.5f),
                            unfocusedContainerColor = ElevatedBlueSurface.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sms_draft_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            border = BorderStroke(1.dp, SubtleBlueBorder),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cancel_sms_button")
                        ) {
                            Text(stringResource(R.string.cancel_action), color = SlateBlueSecondary)
                        }
                        Button(
                            onClick = { onConfirmSms(editedText) },
                            enabled = editedText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = ObsidianBlack
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_send_sms_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.confirm_send_sms))
                        }
                    }
                }
            }

            is PendingAction.RequestPermission -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = "Permission required",
                        tint = ElectricBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = pendingAction.rationale,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onGrantPermission(pendingAction.permission) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = ObsidianBlack
                        )
                    ) {
                        Text(stringResource(R.string.grant_permission))
                    }
                }
            }
        }
    }
}
