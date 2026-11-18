package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MaxDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = ObsidianBlack,
    primaryContainer = DeepRoyalBlue,
    onPrimaryContainer = IceBlueText,
    secondary = NeonCyan,
    onSecondary = ObsidianBlack,
    secondaryContainer = ElevatedBlueSurface,
    onSecondaryContainer = IceBlueText,
    tertiary = SuccessMint,
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

val MaxShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MaxAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MaxDarkColorScheme,
        typography = MaxTypography,
        shapes = MaxShapes,
        content = content
    )
}
