package com.portsotc.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The restrained palette shared by the launcher and in-session overlay. */
object SotcPalette {
    val Basalt = Color(0xFF0B0D10)
    val Card = Color(0xFF12151B)
    val Hairline = Color(0xFF222731)
    val Brass = Color(0xFFC6B282)
    val Ivory = Color(0xFFEAE4D7)
    val Muted = Color(0xFF8A867C)
    val Sage = Color(0xFF6BBF8A)
    val Amber = Color(0xFFD99B52)
    val Danger = Color(0xFFC96A5A)
}

private val DarkColors = darkColorScheme(
    primary = SotcPalette.Brass,
    onPrimary = SotcPalette.Basalt,
    secondary = SotcPalette.Ivory,
    onSecondary = SotcPalette.Basalt,
    tertiary = SotcPalette.Sage,
    background = SotcPalette.Basalt,
    onBackground = SotcPalette.Ivory,
    surface = SotcPalette.Card,
    onSurface = SotcPalette.Ivory,
    surfaceVariant = SotcPalette.Hairline,
    onSurfaceVariant = SotcPalette.Muted,
    error = SotcPalette.Danger,
    onError = SotcPalette.Basalt
)

private val SotcTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        letterSpacing = 10.sp,
        color = SotcPalette.Ivory
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = 2.sp,
        color = SotcPalette.Ivory
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 1.4.sp,
        color = SotcPalette.Brass
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        color = SotcPalette.Ivory
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        color = SotcPalette.Muted,
        fontFeatureSettings = "tnum"
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        color = SotcPalette.Ivory
    )
)

@Composable
fun SotcTheme(content: @Composable () -> Unit) {
    // The product is intentionally dark on every device; following the system
    // here would break the matte-basalt identity on light-themed phones.
    @Suppress("UNUSED_VARIABLE")
    val systemDark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = DarkColors,
        typography = SotcTypography,
        content = content
    )
}
