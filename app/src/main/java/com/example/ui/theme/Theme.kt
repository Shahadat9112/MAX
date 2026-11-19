package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val MaxDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = IceBlueText,
    primaryContainer = DeepRoyalBlue,
    onPrimaryContainer = IceBlueText,
    secondary = VioletPulse,
    onSecondary = IceBlueText,
    secondaryContainer = ElevatedBlueSurface,
    onSecondaryContainer = IceBlueText,
    tertiary = NeonCyan,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = IceBlueText,
    surface = DeepNavySurface,
    onSurface = IceBlueText,
    surfaceVariant = DarkBlueCard,
    onSurfaceVariant = SlateBlueSecondary,
    outline = SubtleBlueBorder,
    outlineVariant = ElevatedBlueSurface,
    error = AlertRed,
    onError = IceBlueText
)

private val MaxLightColorScheme = lightColorScheme(
    primary = LightPrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = LightElevatedSurface,
    onPrimaryContainer = LightPrimaryText,
    secondary = LightAccentPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = LightPrimaryText,
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = LightCanvasTop,
    onBackground = LightPrimaryText,
    surface = LightSurface,
    onSurface = LightPrimaryText,
    surfaceVariant = LightCardSurface,
    onSurfaceVariant = LightSecondaryText,
    outline = LightSubtleBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = AlertRed,
    onError = Color.White
)

val MaxShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun MaxAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MaxDarkColorScheme
        else -> MaxLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaxTypography,
        shapes = MaxShapes,
        content = content
    )
}
