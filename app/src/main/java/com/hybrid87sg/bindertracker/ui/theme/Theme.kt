package com.hybrid87sg.bindertracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** One full set of the app's colours. There is a light set and a dark set. */
class Palette(
    val paper: Color,           // main background
    val shelfBackground: Color,
    val surface: Color,         // cards, rows and boxes that sit on the background
    val ink: Color,             // main text
    val muted: Color,           // secondary text
    val border: Color,
    val borderStrong: Color,
    val dashedBorder: Color,
    val accent: Color,          // blue buttons (white text sits on it)
    val accentText: Color,      // blue words and links on the background
    val wood: Color,            // shelf plank
    val woodDark: Color,
    val disabled: Color,
    val onDisabled: Color,
    val danger: Color,
    val success: Color,         // green circles (white tick sits on it)
    val successText: Color,     // green words on the background
    // The card-back picture shown in empty pockets (see CardBack.kt).
    val cardBack: Color,        // top-left of the blue
    val cardBackDeep: Color,    // bottom-right of the blue
    val cardBackFrame: Color,   // thin frame line
    val cardBackSwirl: Color,   // swirl rings
    val cardBackSpark: Color,   // little star in the middle
)

val LightPalette = Palette(
    paper = Color(0xFFF6F4EF),
    shelfBackground = Color(0xFFEDE8DD),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF1A1A18),
    muted = Color(0xFF5C5A55),
    border = Color(0xFFD9D5CB),
    borderStrong = Color(0xFFA29E94),
    dashedBorder = Color(0xFF8A867B),
    accent = Color(0xFF2447C8),
    accentText = Color(0xFF2447C8),
    wood = Color(0xFFA67C52),
    woodDark = Color(0xFF8A6743),
    disabled = Color(0xFFD9D5CB),
    onDisabled = Color(0xFF3E3C38),
    danger = Color(0xFF9B2C2C),
    success = Color(0xFF2F6B3A),
    successText = Color(0xFF2F6B3A),
    cardBack = Color(0xFF23379A),
    cardBackDeep = Color(0xFF1A2A78),
    cardBackFrame = Color(0xFFC9D2F5),
    cardBackSwirl = Color(0xFF8EA4FF),
    cardBackSpark = Color(0xFFFFFFFF),
)

/** Warm charcoal rather than pure black, so it still feels like the paper look. */
val DarkPalette = Palette(
    paper = Color(0xFF161614),
    shelfBackground = Color(0xFF1E1D1A),
    surface = Color(0xFF262522),
    ink = Color(0xFFEDEBE6),
    muted = Color(0xFFA8A49A),
    border = Color(0xFF3A3934),
    borderStrong = Color(0xFF5C5A55),
    dashedBorder = Color(0xFF7A766C),
    // Two blues in dark mode, so both pass the 4.5:1 readability rule:
    // white on accent is 5.7:1, accentText on the background is 8:1.
    accent = Color(0xFF3B5BDB),
    accentText = Color(0xFF8FA8FF),
    wood = Color(0xFF7A5A3B),
    woodDark = Color(0xFF5E452D),
    disabled = Color(0xFF3A3934),
    onDisabled = Color(0xFFA8A49A),
    danger = Color(0xFFE58A8A),
    success = Color(0xFF2F7A3E),      // white tick on it: 5.3:1
    successText = Color(0xFF6CC27B),  // on the background: 8.3:1
    // Darker blue so it isn't glaring at night, with a brighter frame so the
    // card still stands out from the charcoal background.
    cardBack = Color(0xFF1C2B6E),
    cardBackDeep = Color(0xFF142057),
    cardBackFrame = Color(0xFF6F84D8),
    cardBackSwirl = Color(0xFF5F79E8),
    cardBackSpark = Color(0xFFDDE4FF),
)

/**
 * The colours every screen uses, e.g. `AppColors.Paper`.
 *
 * Each name looks up the colour in whichever set (light or dark) is showing
 * right now. [palette] is "state": when it changes, Compose redraws every
 * part of the screen that used a colour, so switching modes is instant and
 * no screen needs to know which mode is on.
 */
object AppColors {
    var palette by mutableStateOf(LightPalette)
    val isDark: Boolean get() = palette === DarkPalette

    val Paper get() = palette.paper
    val ShelfBackground get() = palette.shelfBackground
    val Surface get() = palette.surface
    val Ink get() = palette.ink
    val Muted get() = palette.muted
    val Border get() = palette.border
    val BorderStrong get() = palette.borderStrong
    val DashedBorder get() = palette.dashedBorder
    val Accent get() = palette.accent
    val AccentText get() = palette.accentText
    val Wood get() = palette.wood
    val WoodDark get() = palette.woodDark
    val Disabled get() = palette.disabled
    val OnDisabled get() = palette.onDisabled
    val Danger get() = palette.danger
    val Success get() = palette.success
    val SuccessText get() = palette.successText
    val CardBack get() = palette.cardBack
    val CardBackDeep get() = palette.cardBackDeep
    val CardBackFrame get() = palette.cardBackFrame
    val CardBackSwirl get() = palette.cardBackSwirl
    val CardBackSpark get() = palette.cardBackSpark

    /** Always white, in both modes: for text and icons on blue buttons and coloured spines. */
    val White = Color(0xFFFFFFFF)
}

/** Material's own components (dialogs, text fields) take their colours from here. */
private fun materialColors(p: Palette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        // Dialog buttons and other Material text use primary, so give it the readable blue.
        primary = p.accentText,
        onPrimary = if (dark) p.paper else AppColors.White,
        background = p.paper,
        onBackground = p.ink,
        surface = p.surface,
        onSurface = p.ink,
        surfaceVariant = p.surface,
        onSurfaceVariant = p.muted,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.surface,
        surfaceContainerLow = p.surface,
        outline = p.borderStrong,
        outlineVariant = p.border,
        error = p.danger,
    )
}

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

/**
 * Wraps the whole app. [dark] says which colours to show; MainActivity works
 * it out from the setting on the Account screen (and the phone's own setting
 * when that is "Follow phone").
 */
@Composable
fun BinderTrackerTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    // Swap the colour set after this frame is drawn. MainActivity also sets it
    // once before the first frame, so the app never flashes the wrong colours.
    SideEffect { AppColors.palette = palette }
    val colors = materialColors(palette, dark)
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Text with no colour of its own uses this. Without it, text outside a
        // Surface falls back to black, which disappears in dark mode.
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
