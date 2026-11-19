package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ChatMessageEntity
import com.example.domain.VoiceLanguage
import com.example.ui.theme.BengaliFontFamily
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoyalIndigo
import com.example.ui.theme.SuccessMint
import com.example.ui.theme.VioletPulse
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Premium geometric "MAX" emblem combining sapphire blue and violet gradient.
 */
@Composable
fun MaxBrandLogo(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.testTag("max_brand_logo")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(ElectricBlue, RoyalIndigo, VioletPulse)
                    )
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "MAX Logo",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = stringResource(R.string.assistant_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Top greeting banner showing Sanju's bilingual dynamic greeting along with current time and date.
 */
@Composable
fun MaxGreetingHeaderCard(
    preferredLanguage: VoiceLanguage,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val isBnPrimary = preferredLanguage == VoiceLanguage.BENGALI ||
        (preferredLanguage == VoiceLanguage.AUTO && ((nowMillis / 10_000L) % 2L == 0L))

    val timeStr = remember(nowMillis / 60_000L) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(nowMillis))
    }
    val dateStr = remember(nowMillis / 60_000L) {
        SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(nowMillis))
    }

    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surface.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.55f)),
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("home_greeting_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = isBnPrimary,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "greetingTransition"
                ) { showBn ->
                    Text(
                        text = if (showBn) {
                            stringResource(R.string.greeting_bn)
                        } else {
                            stringResource(R.string.greeting_en)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = if (showBn) BengaliFontFamily else MaterialTheme.typography.titleMedium.fontFamily
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (isBnPrimary) {
                        stringResource(R.string.greeting_en)
                    } else {
                        stringResource(R.string.greeting_bn)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class QuickActionItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit
)

/**
 * Scrollable rounded Material Design 3 Quick Action Cards for one-tap access to
 * Camera, Gallery, Browser, Phone, WhatsApp, Notes, Reminder, and Settings.
 */
@Composable
fun MaxQuickActionStrip(
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenPhone: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onCreateNote: () -> Unit,
    onCreateReminder: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = remember(
        onOpenCamera,
        onOpenGallery,
        onOpenBrowser,
        onOpenPhone,
        onOpenWhatsApp,
        onCreateNote,
        onCreateReminder,
        onOpenSettings
    ) {
        listOf(
            QuickActionItem("camera", "Camera", Icons.Rounded.CameraAlt, ElectricBlue, onOpenCamera),
            QuickActionItem("gallery", "Gallery", Icons.Rounded.PhotoLibrary, VioletPulse, onOpenGallery),
            QuickActionItem("browser", "Browser", Icons.Rounded.Public, NeonCyan, onOpenBrowser),
            QuickActionItem("phone", "Phone", Icons.Rounded.Phone, SuccessMint, onOpenPhone),
            QuickActionItem("whatsapp", "WhatsApp", Icons.AutoMirrored.Rounded.Chat, Color(0xFF25D366), onOpenWhatsApp),
            QuickActionItem("notes", "Notes", Icons.Rounded.EditNote, Color(0xFFF59E0B), onCreateNote),
            QuickActionItem("reminder", "Reminder", Icons.Rounded.Alarm, RoyalIndigo, onCreateReminder),
            QuickActionItem("settings", "Settings", Icons.Rounded.Settings, ElectricBlue, onOpenSettings)
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_action_strip")
    ) {
        items(actions, key = { it.id }) { item ->
            QuickActionCard(item = item)
        }
    }
}

@Composable
private fun QuickActionCard(item: QuickActionItem) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "quickActionScale"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.55f)),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .scale(scale)
            .minimumInteractiveComponentSize()
            .width(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .testTag("quick_action_${item.id}")
            .semantics {
                contentDescription = item.label
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = item.accent),
                role = Role.Button,
                onClick = item.onClick
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(item.accent.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact highlight card showing the most recently completed task by MAX.
 */
@Composable
fun LastCompletedTaskCard(
    lastTaskMessage: ChatMessageEntity?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = lastTaskMessage != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        lastTaskMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = scheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("last_completed_task_card")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Last completed task",
                        tint = SuccessMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Last Task: ${msg.actionBadge ?: "Completed"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary
                        )
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
