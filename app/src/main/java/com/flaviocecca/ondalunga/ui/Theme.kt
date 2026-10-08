package com.flaviocecca.ondalunga.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Night = Color(0xFF14213D)
    val NightRaised = Color(0xFF1F2F52)
    val Cream = Color(0xFFF3E9D2)
    val Sun = Color(0xFFF2B84B)
    val Ember = Color(0xFFE8743B)
    val Sky = Color(0xFF3BA7C9)
    val Lagoon = Color(0xFF2F8F9D)
    val Needle = Color(0xFFD7263D)
}

private val Colors = darkColorScheme(
    primary = Palette.Sun,
    onPrimary = Palette.Night,
    secondary = Palette.Sky,
    onSecondary = Palette.Night,
    secondaryContainer = Palette.Sun,
    onSecondaryContainer = Palette.Night,
    background = Palette.Night,
    onBackground = Palette.Cream,
    surface = Palette.Night,
    onSurface = Palette.Cream,
    surfaceVariant = Palette.NightRaised,
    onSurfaceVariant = Palette.Cream.copy(alpha = 0.75f),
    surfaceContainerHigh = Palette.NightRaised,
    outline = Palette.Cream.copy(alpha = 0.4f),
    error = Palette.Needle,
)

@Composable
fun OndaLungaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
