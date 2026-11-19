package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoyalIndigo
import com.example.ui.theme.SuccessMint
import com.example.ui.theme.VioletPulse
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Centerpiece animated circular Microphone Hub & Orbital Aura for MAX.
 *
 * Adapts to Light & Dark Material Design 3 schemes, optimizes animation loops to a single
 * [rememberInfiniteTransition], provides soft glow when listening, gentle pulse when speaking,
 * floating motion, and tactile spring press scale feedback.
 */
@Composable
fun MaxAiOrb(
    status: AssistantStatus,
    audioLevel: Float,
    preferredLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    activeSpeechLocale: String = "en-US",
    onCycleLanguage: (() -> Unit)? = null,
    orbSize: Dp = 172.dp,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "maxOrb")
    val colorScheme = MaterialTheme.colorScheme

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
            animation = tween(durationMillis = 4200, easing = LinearEasing)
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

    val floatOffsetDp by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )

    val smoothedAudio by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "audioLevel"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pressScale"
    )

    val primaryGlow by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> ElectricBlue
            AssistantStatus.LISTENING -> NeonCyan
            AssistantStatus.THINKING -> VioletPulse
            AssistantStatus.SPEAKING -> SuccessMint
        },
        animationSpec = tween(320),
        label = "primaryGlow"
    )

    val secondaryGlow by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> VioletPulse
            AssistantStatus.LISTENING -> ElectricBlue
            AssistantStatus.THINKING -> RoyalIndigo
            AssistantStatus.SPEAKING -> NeonCyan
        },
        animationSpec = tween(320),
        label = "secondaryGlow"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(y = floatOffsetDp.dp)
                .scale(pressScale)
                .size(orbSize)
                .clip(CircleShape)
                .testTag("ai_orb_button")
                .semantics {
                    contentDescription = "MAX microphone orb button"
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = primaryGlow),
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
                    AssistantStatus.LISTENING -> 0.16f * smoothedAudio + 0.06f * pulse
                    AssistantStatus.SPEAKING -> 0.12f * pulse
                    AssistantStatus.THINKING -> 0.07f * pulse
                    AssistantStatus.IDLE -> 0.03f * pulse
                }

                // Soft glowing aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = if (status == AssistantStatus.LISTENING) 0.42f else 0.28f + reactiveBoost),
                            secondaryGlow.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = maxR
                    ),
                    radius = maxR,
                    center = Offset(cx, cy)
                )

                // Outer orbital arc 1
                val arcRadius1 = maxR * (0.80f + reactiveBoost * 0.20f)
                val arcSpeed1 = if (status == AssistantStatus.THINKING) rotationFast * 1.4f else rotationSlow
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryGlow.copy(alpha = 0.88f),
                            secondaryGlow.copy(alpha = 0.9f),
                            Color.Transparent
                        )
                    ),
                    startAngle = arcSpeed1,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - arcRadius1, cy - arcRadius1),
                    size = Size(arcRadius1 * 2f, arcRadius1 * 2f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Inner harmonic aura ring
                val waveBaseR = maxR * (0.56f + reactiveBoost * 0.18f)
                val waveAmp = when (status) {
                    AssistantStatus.LISTENING -> maxR * (0.025f + 0.065f * smoothedAudio)
                    AssistantStatus.SPEAKING -> maxR * (0.035f + 0.035f * pulse)
                    AssistantStatus.THINKING -> maxR * 0.022f
                    AssistantStatus.IDLE -> maxR * 0.010f
                }
                val wavePath = Path()
                val steps = 64
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
                    brush = Brush.linearGradient(
                        colors = listOf(
                            ElectricBlue,
                            RoyalIndigo,
                            VioletPulse
                        ),
                        start = Offset(cx - waveBaseR, cy - waveBaseR),
                        end = Offset(cx + waveBaseR, cy + waveBaseR)
                    )
                )

                drawPath(
                    path = wavePath,
                    color = primaryGlow.copy(alpha = 0.78f),
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }

            // Inner Circular Animated Microphone Badge
            val icon = when (status) {
                AssistantStatus.IDLE -> Icons.Rounded.Mic
                AssistantStatus.LISTENING -> Icons.Rounded.Stop
                AssistantStatus.THINKING -> Icons.Rounded.GraphicEq
                AssistantStatus.SPEAKING -> Icons.AutoMirrored.Rounded.VolumeUp
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(orbSize * 0.36f)
                    .shadow(elevation = 10.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = when (status) {
                        AssistantStatus.LISTENING -> stringResource(R.string.stop_listening)
                        else -> stringResource(R.string.voice_command)
                    },
                    tint = Color.White,
                    modifier = Modifier.size(orbSize * 0.19f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        StatusPill(status = status)

        if (onCycleLanguage != null) {
            Spacer(modifier = Modifier.height(6.dp))
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
    val scheme = MaterialTheme.colorScheme
    val labelText = if (status == AssistantStatus.LISTENING) {
        "${preferredLanguage.label} • $activeSpeechLocale"
    } else {
        "Speech: ${preferredLanguage.label}"
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = scheme.surfaceVariant.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.7f)),
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
                tint = scheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurface.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun StatusPill(status: AssistantStatus) {
    val scheme = MaterialTheme.colorScheme
    val (label, icon, tint) = when (status) {
        AssistantStatus.IDLE -> Triple(
            stringResource(R.string.status_idle),
            Icons.Rounded.GraphicEq,
            scheme.onSurfaceVariant
        )
        AssistantStatus.LISTENING -> Triple(
            stringResource(R.string.status_listening),
            Icons.Rounded.Hearing,
            NeonCyan
        )
        AssistantStatus.THINKING -> Triple(
            stringResource(R.string.status_thinking),
            Icons.Rounded.Memory,
            VioletPulse
        )
        AssistantStatus.SPEAKING -> Triple(
            stringResource(R.string.status_speaking),
            Icons.AutoMirrored.Rounded.VolumeUp,
            SuccessMint
        )
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = scheme.surfaceVariant.copy(alpha = 0.92f),
        border = BorderStroke(
            width = 1.dp,
            color = if (status == AssistantStatus.IDLE) scheme.outline.copy(alpha = 0.7f) else tint.copy(alpha = 0.7f)
        ),
        tonalElevation = 2.dp,
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
                color = if (status == AssistantStatus.IDLE) scheme.onSurface else tint
            )
        }
    }
}
