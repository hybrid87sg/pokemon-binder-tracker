package com.hybrid87sg.bindertracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The prototype's colours, named by what they are used for. */
object AppColors {
    val Paper = Color(0xFFF6F4EF)       // main background
    val ShelfBackground = Color(0xFFEDE8DD)
    val Ink = Color(0xFF1A1A18)         // main text
    val Muted = Color(0xFF5C5A55)       // secondary text
    val Border = Color(0xFFD9D5CB)
    val BorderStrong = Color(0xFFA29E94)
    val DashedBorder = Color(0xFF8A867B)
    val Accent = Color(0xFF2447C8)      // blue buttons
    val Wood = Color(0xFFA67C52)        // shelf plank
    val WoodDark = Color(0xFF8A6743)
    val Disabled = Color(0xFFD9D5CB)
    val OnDisabled = Color(0xFF3E3C38)
    val White = Color(0xFFFFFFFF)
    val Danger = Color(0xFF9B2C2C)
    val Success = Color(0xFF2F6B3A)
}

private val colorScheme = lightColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.White,
    background = AppColors.Paper,
    onBackground = AppColors.Ink,
    surface = AppColors.White,
    onSurface = AppColors.Ink,
    onSurfaceVariant = AppColors.Muted,
    outline = AppColors.BorderStrong,
    outlineVariant = AppColors.Border,
    error = AppColors.Danger,
)

private val headline = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)

private val typography = Typography(
    headlineLarge = headline.copy(fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = headline.copy(fontSize = 28.sp, lineHeight = 32.sp),
    titleLarge = headline.copy(fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun BinderTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}
