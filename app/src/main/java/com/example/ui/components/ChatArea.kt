package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ChatMessageEntity
import com.example.domain.WakeWordParser
import com.example.ui.theme.BengaliFontFamily
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.VioletPulse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatArea(
    messages: List<ChatMessageEntity>,
    partialTranscript: String,
    onQuickPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, partialTranscript) {
        val total = messages.size + if (partialTranscript.isNotBlank()) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    if (messages.isEmpty() && partialTranscript.isBlank()) {
        EmptyChatState(
            onQuickPromptClick = onQuickPromptClick,
            modifier = modifier
        )
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier
                .fillMaxSize()
                .testTag("chat_message_list")
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(message = msg)
            }

            if (partialTranscript.isNotBlank()) {
                item(key = "partial_live_transcript") {
                    PartialTranscriptBubble(text = partialTranscript)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChatState(
    onQuickPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val suggestions = listOf(
        "MAX, what time is it?",
        "আগামীকাল সকাল ৮টার জন্য রিমাইন্ডার দাও",
        "MAX, এটা বাংলা থেকে ইংরেজিতে অনুবাদ করো",
        "MAX, WhatsApp খোলো",
        "আজকের ক্যালেন্ডার দেখাও",
        "MAX, take a picture"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = scheme.secondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Suggested Actions for Sanju",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Say \"MAX\" anytime or tap a smart suggestion below:",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { prompt ->
                val isBn = WakeWordParser.isBengaliText(prompt)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = scheme.surfaceVariant.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.65f)),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onQuickPromptClick(prompt) }
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = if (isBn) BengaliFontFamily else BodyFontFamily
                        ),
                        color = scheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessageEntity) {
    val scheme = MaterialTheme.colorScheme
    val isBn = message.language == "bn" || WakeWordParser.isBengaliText(message.text)
    val fontFamily = if (isBn) BengaliFontFamily else BodyFontFamily
    val timeText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))

    Row(
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.isUser) 20.dp else 6.dp,
                bottomEnd = if (message.isUser) 6.dp else 20.dp
            ),
            color = when {
                message.isError -> scheme.error.copy(alpha = 0.16f)
                message.isUser -> scheme.primaryContainer
                else -> scheme.surfaceVariant
            },
            border = BorderStroke(
                width = 1.dp,
                color = when {
                    message.isError -> scheme.error.copy(alpha = 0.55f)
                    message.isUser -> scheme.primary.copy(alpha = 0.5f)
                    else -> scheme.outline.copy(alpha = 0.6f)
                }
            ),
            tonalElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .testTag(if (message.isUser) "user_chat_bubble" else "max_chat_bubble")
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!message.isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "MAX",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary
                        )
                        message.actionBadge?.let { badge ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(scheme.secondaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.secondary
                                )
                            }
                        }
                        if (message.isOffline) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = "Offline reply",
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamily),
                    color = if (message.isUser) scheme.onPrimaryContainer else scheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun PartialTranscriptBubble(text: String) {
    val scheme = MaterialTheme.colorScheme
    val isBn = WakeWordParser.isBengaliText(text)
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            ElectricBlue.copy(alpha = 0.25f),
                            VioletPulse.copy(alpha = 0.25f)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "$text…",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = if (isBn) BengaliFontFamily else BodyFontFamily
                ),
                color = scheme.onBackground
            )
        }
    }
}
