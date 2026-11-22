package com.hybrid87sg.bindertracker.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.hybrid87sg.bindertracker.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    val surface2: Color,        // a slightly tinted box, e.g. the first stat tile
    val ink: Color,             // main text
    val muted: Color,           // secondary text
    val border: Color,
    val borderStrong: Color,
    val dashedBorder: Color,
    val accent: Color,          // violet buttons (white text sits on it)
    val accentText: Color,      // violet words and links on the background
    val wood: Color,            // shelf plank
    val woodDark: Color,
    val disabled: Color,
    val onDisabled: Color,
    val danger: Color,
    val success: Color,         // green circles (white tick sits on it)
    val successText: Color,     // green words on the background
    val check: Color,           // the "?" on a pocket that still needs identifying
    val checkFill: Color,       // the pale label under it (dark writing sits on it)
    // The card-back picture shown in empty pockets (see CardBack.kt).
    val cardBack: Color,        // top-left of the blue
    val cardBackDeep: Color,    // bottom-right of the blue
    val cardBackFrame: Color,   // thin frame line
    val cardBackSwirl: Color,   // swirl rings
    val cardBackSpark: Color,   // little star in the middle
    // The rainbow "holo foil" colours, left to right.
    val holo: List<Color>,
)

/**
 * "Holo Foil": soft violet paper with a rainbow foil shine.
 * Every text colour here reads at 4.5:1 or better on the backgrounds it sits on.
 */
val LightPalette = Palette(
    paper = Color(0xFFF4F2FB),
    shelfBackground = Color(0xFFECE8F7),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF0EDFA),
    ink = Color(0xFF1B1730),          // 15.6:1 on paper
    muted = Color(0xFF5D5878),        // 6.0:1 on paper
    border = Color(0xFFDCD8EC),
    borderStrong = Color(0xFFB9B3D3),
    dashedBorder = Color(0xFF8C86A8),
    accent = Color(0xFF4B36D1),       // white on it: 7.6:1
    accentText = Color(0xFF4B36D1),   // on paper: 6.9:1
    wood = Color(0xFFB08559),
    woodDark = Color(0xFF8E6A44),
    disabled = Color(0xFFDCD8EC),
    onDisabled = Color(0xFF3E3A55),
    danger = Color(0xFF9B2C2C),
    success = Color(0xFF2F6B3A),
    successText = Color(0xFF2F6B3A),
    check = Color(0xFF8A5A12),        // 5.4:1 on paper (the old orange was 3.3:1)
    checkFill = Color(0xFFFBE3A6),
    cardBack = Color(0xFF23379A),
    cardBackDeep = Color(0xFF1A2A78),
    cardBackFrame = Color(0xFFC9D2F5),
    cardBackSwirl = Color(0xFF8EA4FF),
    cardBackSpark = Color(0xFFFFFFFF),
    holo = listOf(Color(0xFFFF9AD5), Color(0xFFFFD36E), Color(0xFF8CF0C8), Color(0xFF7FB7FF), Color(0xFFC59BFF)),
)

/** Deep indigo night, so the foil colours glow. */
val DarkPalette = Palette(
    paper = Color(0xFF12101F),
    shelfBackground = Color(0xFF161327),
    surface = Color(0xFF1E1B31),
    surface2 = Color(0xFF272341),
    ink = Color(0xFFF1EEFC),          // 16.4:1 on paper
    muted = Color(0xFFABA6C6),        // 7.2:1 on surface
    border = Color(0xFF353052),
    borderStrong = Color(0xFF4D4772),
    dashedBorder = Color(0xFF7C76A0),
    accent = Color(0xFF5B45E6),       // white on it: 6.1:1
    accentText = Color(0xFFB3A8FF),   // on paper: 8.9:1
    wood = Color(0xFF6E5236),
    woodDark = Color(0xFF54402B),
    disabled = Color(0xFF353052),
    onDisabled = Color(0xFFABA6C6),
    danger = Color(0xFFE58A8A),
    success = Color(0xFF2F7A3E),      // white tick on it: 5.3:1
    successText = Color(0xFF6CC27B),  // on the background: 8.3:1
    check = Color(0xFFF2B84B),        // 9:1 on paper
    checkFill = Color(0xFFFBE3A6),
    // Darker blue so it isn't glaring at night, with a brighter frame so the
    // card still stands out from the background.
    cardBack = Color(0xFF1C2B6E),
    cardBackDeep = Color(0xFF142057),
    cardBackFrame = Color(0xFF6F84D8),
    cardBackSwirl = Color(0xFF5F79E8),
    cardBackSpark = Color(0xFFDDE4FF),
    holo = listOf(Color(0xFFE77BC0), Color(0xFFE9B84C), Color(0xFF5FD3A6), Color(0xFF5C93F0), Color(0xFFA77BF0)),
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
    /** True when the dark colours are showing (also while fading into them). */
    val isDark: Boolean get() = palette.paper.luminance() < 0.5f

    val Paper get() = palette.paper
    val ShelfBackground get() = palette.shelfBackground
    val Surface get() = palette.surface
    val Surface2 get() = palette.surface2
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
    val Check get() = palette.check
    val CheckFill get() = palette.checkFill
    val Holo get() = palette.holo
    val CardBack get() = palette.cardBack
    val CardBackDeep get() = palette.cardBackDeep
    val CardBackFrame get() = palette.cardBackFrame
    val CardBackSwirl get() = palette.cardBackSwirl
    val CardBackSpark get() = palette.cardBackSpark

    // ---- Textures and depth (see Backdrop.kt) ----
    /** Soft shadow under cards and binders. */
    val Shadow get() = if (isDark) Color(0x99000000) else Color(0x471E1446)
    /** The clear plastic edge of a pocket sleeve. */
    val SleeveEdge get() = if (isDark) Color(0x2EC8BEFF) else Color(0x59786EAA)
    /** The shine on a pocket sleeve's plastic. */
    val SleeveShine get() = if (isDark) Color(0x12FFFFFF) else Color(0x8CFFFFFF)
    /** Inside an empty-looking sleeve: the background with a little violet-grey. */
    val PocketFill get() = androidx.compose.ui.graphics.lerp(palette.paper, Color(0xFF8A82B8), 0.15f)
    /** The app's background gradient, top-left to bottom-right. */
    val BackgroundStops: List<Color>
        get() = if (isDark) listOf(Color(0xFF1C1834), Color(0xFF14112A), Color(0xFF0C1024))
        else listOf(Color(0xFFF8F6FE), Color(0xFFECE6FB), Color(0xFFDFE6FA))
    /** The soft glow in the background's top-right corner. */
    val BackgroundGlow get() = if (isDark) Color(0x477850C8) else Color(0x59FFC496)

    /** Always white, in both modes: for text and icons on blue buttons and coloured spines. */
    val White = Color(0xFFFFFFFF)

    /** Always near-black, in both modes: for writing on pale things like a yellow spine or the "?" label. */
    val Black = Color(0xFF1A1A18)
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

/** Figtree for everyday text, Bricolage Grotesque for big headings. Both are free fonts bundled in res/font. */
val BodyFont = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)
val DisplayFont = FontFamily(Font(R.font.bricolage_bold, FontWeight.Bold))

private val headline = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)

private val typography = Typography().let { t ->
    fun TextStyle.body() = copy(fontFamily = BodyFont)
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = DisplayFont),
        displayMedium = t.displayMedium.copy(fontFamily = DisplayFont),
        displaySmall = t.displaySmall.copy(fontFamily = DisplayFont),
        headlineLarge = headline.copy(fontSize = 32.sp, lineHeight = 36.sp),
        headlineMedium = headline.copy(fontSize = 28.sp, lineHeight = 32.sp),
        headlineSmall = headline.copy(fontSize = 24.sp, lineHeight = 28.sp),
        titleLarge = headline.copy(fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.body(),
        bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = BodyFont, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.body(),
        labelSmall = t.labelSmall.body(),
    )
}

@Composable
private fun a(c: Color): Color = animateColorAsState(c, tween(durationMillis = 450), label = "palette").value

/** Fades every colour from the old set to the new one, so switching light and dark melts instead of snapping. */
@Composable
private fun animatedPalette(target: Palette): Palette {
    return Palette(
        paper = a(target.paper), shelfBackground = a(target.shelfBackground), surface = a(target.surface),
        surface2 = a(target.surface2), ink = a(target.ink), muted = a(target.muted), border = a(target.border),
        borderStrong = a(target.borderStrong), dashedBorder = a(target.dashedBorder), accent = a(target.accent),
        accentText = a(target.accentText), wood = a(target.wood), woodDark = a(target.woodDark),
        disabled = a(target.disabled), onDisabled = a(target.onDisabled), danger = a(target.danger),
        success = a(target.success), successText = a(target.successText), check = a(target.check),
        checkFill = a(target.checkFill), cardBack = a(target.cardBack), cardBackDeep = a(target.cardBackDeep),
        cardBackFrame = a(target.cardBackFrame), cardBackSwirl = a(target.cardBackSwirl),
        cardBackSpark = a(target.cardBackSpark), holo = target.holo,
    )
}

/**
 * Wraps the whole app. [dark] says which colours to show; MainActivity works
 * it out from the setting on the Account screen (and the phone's own setting
 * when that is "Follow phone").
 */
@Composable
fun BinderTrackerTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = animatedPalette(if (dark) DarkPalette else LightPalette)
    // Swap the colour set after this frame is drawn. MainActivity also sets it
    // once before the first frame, so the app never flashes the wrong colours.
    SideEffect { AppColors.palette = palette }
    MaterialTheme(colorScheme = materialColors(palette, dark), typography = typography) {
        // Text with no colour of its own uses the main ink colour. Without
        // this, Android drew such text black, which vanished in dark mode.
        CompositionLocalProvider(LocalContentColor provides palette.ink, content = content)
    }
}
