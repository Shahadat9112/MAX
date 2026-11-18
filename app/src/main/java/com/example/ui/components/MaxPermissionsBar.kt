package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkBlueCard
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SlateBlueSecondary
import com.example.ui.theme.SubtleBlueBorder
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale

val maxCorePermissionsList: List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

fun friendlyPermissionLabel(permission: String): String {
    return when (permission) {
        Manifest.permission.RECORD_AUDIO -> "Microphone"
        Manifest.permission.READ_CONTACTS -> "Contacts"
        Manifest.permission.CALL_PHONE -> "Phone Calls"
        Manifest.permission.SEND_SMS -> "SMS"
        Manifest.permission.CAMERA -> "Camera"
        Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
        else -> permission.substringAfterLast('.')
    }
}

fun friendlyPermissionDescription(permission: String): String {
    return when (permission) {
        Manifest.permission.RECORD_AUDIO -> "Microphone (Voice commands & \"MAX\" wake word)"
        Manifest.permission.READ_CONTACTS -> "Contacts (Find recipients for calls & SMS)"
        Manifest.permission.CALL_PHONE -> "Phone (Direct voice calling)"
        Manifest.permission.SEND_SMS -> "SMS (Voice messaging)"
        Manifest.permission.CAMERA -> "Camera (Open camera & take photos)"
        Manifest.permission.POST_NOTIFICATIONS -> "Notifications (Background wake-word service)"
        else -> permission.substringAfterLast('.')
    }
}

fun openAppPermissionSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AccompanistPermissionBanner(
    multiplePermissionsState: MultiplePermissionsState,
    hasRequestedPermissionsBefore: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (multiplePermissionsState.allPermissionsGranted) return

    val context = LocalContext.current
    val missingNames = multiplePermissionsState.revokedPermissions.joinToString(", ") {
        friendlyPermissionLabel(it.permission)
    }
    val permanentlyDenied = hasRequestedPermissionsBefore &&
        !multiplePermissionsState.shouldShowRationale

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkBlueCard,
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("accompanist_permission_banner")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = "Permissions required",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (multiplePermissionsState.shouldShowRationale) {
                        "Permissions needed for full assistant actions"
                    } else {
                        "Enable MAX AI permissions"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = IceBlueText
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Missing: $missingNames",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateBlueSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = ObsidianBlack
                    ),
                    modifier = Modifier.testTag("grant_missing_permissions_button")
                ) {
                    Text("Grant Access")
                }
                if (permanentlyDenied) {
                    OutlinedButton(
                        onClick = { openAppPermissionSettings(context) },
                        border = BorderStroke(1.dp, SubtleBlueBorder),
                        modifier = Modifier.testTag("open_app_settings_permissions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Settings", color = NeonCyan)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRationaleDialog(
    permissionState: PermissionState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val showSettingsFallback = !permissionState.status.isGranted && !permissionState.status.shouldShowRationale

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepNavySurface,
        titleContentColor = IceBlueText,
        textContentColor = SlateBlueSecondary,
        title = {
            Text(text = "${friendlyPermissionLabel(permissionState.permission)} Access Needed")
        },
        text = {
            Text(
                text = "${friendlyPermissionDescription(permissionState.permission)}. Please grant this permission so MAX AI can complete your request."
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (showSettingsFallback) {
                        openAppPermissionSettings(context)
                    } else {
                        permissionState.launchPermissionRequest()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricBlue,
                    contentColor = ObsidianBlack
                )
            ) {
                Text(if (showSettingsFallback) "Open Settings" else "Allow")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now", color = SlateBlueSecondary)
            }
        }
    )
}
