package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AssistantStatus
import com.example.domain.VoiceLanguage
import com.example.ui.theme.DarkBlueCard
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SlateBlueSecondary
import com.example.ui.theme.SubtleBlueBorder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MaxAiOrb(
    status: AssistantStatus,
    audioLevel: Float,
    preferredLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    activeSpeechLocale: String = "en-US",
    onCycleLanguage: (() -> Unit)? = null,
    orbSize: Dp = 176.dp,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "maxOrb")

    val rotationSlow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing)
        ),
        label = "rotationSlow"
    )

    val rotationFast by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing)
        ),
        label = "rotationFast"
    )

    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breathPhase"
    )

    val smoothedAudio by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "audioLevel"
    )

    val primaryGlow by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> ElectricBlue
            AssistantStatus.LISTENING -> NeonCyan
            AssistantStatus.THINKING -> Color(0xFF4F7BFF)
            AssistantStatus.SPEAKING -> Color(0xFF00D4FF)
        },
        animationSpec = tween(350),
        label = "primaryGlow"
    )

    val secondaryGlow by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> DeepRoyalBlue
            AssistantStatus.LISTENING -> ElectricBlue
            AssistantStatus.THINKING -> NeonCyan
            AssistantStatus.SPEAKING -> Color(0xFF00FFA3)
        },
        animationSpec = tween(350),
        label = "secondaryGlow"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(orbSize)
                .clip(CircleShape)
                .testTag("ai_orb_button")
                .semantics {
                    contentDescription = "MAX AI animated voice orb"
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onOrbClick
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = size.minDimension / 2f
                val pulse = 0.5f + 0.5f * sin(breathPhase)

                val reactiveBoost = when (status) {
                    AssistantStatus.LISTENING -> 0.14f * smoothedAudio + 0.04f * pulse
                    AssistantStatus.SPEAKING -> 0.10f * pulse
                    AssistantStatus.THINKING -> 0.06f * pulse
                    AssistantStatus.IDLE -> 0.03f * pulse
                }

                // Outer ambient glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = 0.34f + reactiveBoost),
                            secondaryGlow.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = maxR
                    ),
                    radius = maxR,
                    center = Offset(cx, cy)
                )

                // Outer orbital arc 1
                val arcRadius1 = maxR * (0.80f + reactiveBoost * 0.25f)
                val arcSpeed1 = if (status == AssistantStatus.THINKING) rotationFast * 1.4f else rotationSlow
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryGlow.copy(alpha = 0.85f),
                            NeonCyan.copy(alpha = 0.9f),
                            Color.Transparent
                        )
                    ),
                    startAngle = arcSpeed1,
                    sweepAngle = 125f,
                    useCenter = false,
                    topLeft = Offset(cx - arcRadius1, cy - arcRadius1),
                    size = Size(arcRadius1 * 2f, arcRadius1 * 2f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            secondaryGlow.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    ),
                    startAngle = arcSpeed1 + 180f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(cx - arcRadius1, cy - arcRadius1),
                    size = Size(arcRadius1 * 2f, arcRadius1 * 2f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Inner counter-rotating orbital ring
                val arcRadius2 = maxR * (0.68f + reactiveBoost * 0.2f)
                val arcSpeed2 = if (status == AssistantStatus.THINKING) rotationFast * 2f else rotationFast
                drawArc(
                    color = primaryGlow.copy(alpha = 0.45f),
                    startAngle = arcSpeed2,
                    sweepAngle = 210f,
                    useCenter = false,
                    topLeft = Offset(cx - arcRadius2, cy - arcRadius2),
                    size = Size(arcRadius2 * 2f, arcRadius2 * 2f),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )

                // Organic audio-reactive harmonic wave ring
                val waveBaseR = maxR * (0.52f + reactiveBoost * 0.22f)
                val waveAmp = when (status) {
                    AssistantStatus.LISTENING -> maxR * (0.03f + 0.08f * smoothedAudio)
                    AssistantStatus.SPEAKING -> maxR * (0.04f + 0.04f * pulse)
                    AssistantStatus.THINKING -> maxR * 0.025f
                    AssistantStatus.IDLE -> maxR * 0.012f
                }
                val wavePath = Path()
                val steps = 72
                val lobes = if (status == AssistantStatus.THINKING) 6 else 5
                for (i in 0..steps) {
                    val angle = (i.toFloat() / steps) * (2f * PI.toFloat())
                    val offset = waveAmp * sin(angle * lobes + breathPhase * 2f) *
                        cos(angle * 2f - breathPhase)
                    val r = waveBaseR + offset
                    val x = cx + r * cos(angle)
                    val y = cy + r * sin(angle)
                    if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                }
                wavePath.close()

                drawPath(
                    path = wavePath,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.92f),
                            ElectricBlue.copy(alpha = 0.85f),
                            DeepRoyalBlue.copy(alpha = 0.95f),
                            ObsidianBlack
                        ),
                        center = Offset(cx - waveBaseR * 0.18f, cy - waveBaseR * 0.18f),
                        radius = waveBaseR * 1.25f
                    )
                )

                drawPath(
                    path = wavePath,
                    color = primaryGlow.copy(alpha = 0.7f),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Inner luminous core highlight
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.72f),
                            NeonCyan.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = Offset(cx - waveBaseR * 0.22f, cy - waveBaseR * 0.24f),
                        radius = waveBaseR * 0.55f
                    ),
                    radius = waveBaseR * 0.55f,
                    center = Offset(cx - waveBaseR * 0.14f, cy - waveBaseR * 0.16f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        StatusPill(status = status)

        if (onCycleLanguage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            SpeechLocalePill(
                status = status,
                preferredLanguage = preferredLanguage,
                activeSpeechLocale = activeSpeechLocale,
                onClick = onCycleLanguage
            )
        }
    }
}

@Composable
private fun SpeechLocalePill(
    status: AssistantStatus,
    preferredLanguage: VoiceLanguage,
    activeSpeechLocale: String,
    onClick: () -> Unit
) {
    val labelText = if (status == AssistantStatus.LISTENING) {
        "${preferredLanguage.label} • $activeSpeechLocale"
    } else {
        "Speech: ${preferredLanguage.label}"
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = DarkBlueCard.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, SubtleBlueBorder.copy(alpha = 0.85f)),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("speech_locale_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Translate,
                contentDescription = "Switch speech recognition language",
                tint = NeonCyan,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelMedium,
                color = IceBlueText.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun StatusPill(status: AssistantStatus) {
    val (label, icon, tint) = when (status) {
        AssistantStatus.IDLE -> Triple(
            stringResource(R.string.status_idle),
            Icons.Rounded.GraphicEq,
            SlateBlueSecondary
        )
        AssistantStatus.LISTENING -> Triple(
            stringResource(R.string.status_listening),
            Icons.Rounded.Hearing,
            NeonCyan
        )
        AssistantStatus.THINKING -> Triple(
            stringResource(R.string.status_thinking),
            Icons.Rounded.Memory,
            ElectricBlue
        )
        AssistantStatus.SPEAKING -> Triple(
            stringResource(R.string.status_speaking),
            Icons.Rounded.VolumeUp,
            Color(0xFF00E5A3)
        )
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = DarkBlueCard.copy(alpha = 0.9f),
        border = BorderStroke(
            width = 1.dp,
            color = if (status == AssistantStatus.IDLE) SubtleBlueBorder else tint.copy(alpha = 0.65f)
        ),
        modifier = Modifier.testTag("assistant_status_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (status == AssistantStatus.IDLE) IceBlueText.copy(alpha = 0.85f) else tint
            )
        }
    }
}
