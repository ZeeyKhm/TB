package com.zeeykhm.tb.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val TbBlack = Color(0xFF070709)
val TbSurface = Color(0xFF121217)
val TbSurfaceHigh = Color(0xFF1D1D24)
val TbAccent = Color(0xFFFF4D5E)
val TbText = Color(0xFFF5F5F7)
val TbMuted = Color(0xFF9A9AA5)

private val TbColorScheme = darkColorScheme(
    primary = TbAccent,
    onPrimary = Color.White,
    secondary = TbAccent,
    onSecondary = Color.White,
    background = TbBlack,
    onBackground = TbText,
    surface = TbSurface,
    onSurface = TbText,
    surfaceVariant = TbSurfaceHigh,
    onSurfaceVariant = TbMuted,
    outline = Color(0xFF2C2C35),
    outlineVariant = Color(0xFF1E1E26),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
)

private val TbTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.5).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
    ),
)

@Composable
fun TbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TbColorScheme,
        typography = TbTypography,
        content = content,
    )
}
