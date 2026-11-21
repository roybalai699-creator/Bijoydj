package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val BijoyDarkColorScheme = darkColorScheme(
    primary = DeckACyan,
    onPrimary = ChassisVoid,
    primaryContainer = DeckADarkGlow,
    onPrimaryContainer = DeckACyan,
    secondary = DeckBRed,
    onSecondary = TextPrimary,
    secondaryContainer = DeckBDarkGlow,
    onSecondaryContainer = DeckBOrange,
    tertiary = LedPurple,
    onTertiary = TextPrimary,
    background = ChassisVoid,
    onBackground = TextPrimary,
    surface = ChassisDark,
    onSurface = TextPrimary,
    surfaceVariant = ChassisSurface,
    onSurfaceVariant = TextSecondary,
    outline = MetalBorder,
    error = LedRed,
    onError = TextPrimary
)

val BijoyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun BijoyDjTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BijoyDarkColorScheme,
        typography = BijoyTypography,
        shapes = BijoyShapes,
        content = content
    )
}
