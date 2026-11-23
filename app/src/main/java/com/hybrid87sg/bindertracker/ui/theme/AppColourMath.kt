package com.hybrid87sg.bindertracker.ui.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * One colour picked on the colour ring.
 * [hue] is the place on the rainbow, 0 to 360 (0 = red, 120 = green, 240 = blue).
 * [strength] is the outer ring: 0 = soft (the gentle look the app always had), 1 = vivid.
 */
data class ColourChoice(val hue: Float, val strength: Float)

/** A ready-made colour: a gem under the ring and a dot on it. */
data class ColourPreset(val name: String, val hue: Float)

/**
 * Every colour the app needs, worked out from the person's picks.
 * Colours are plain numbers like 0xFF4B36D1 (alpha, red, green, blue).
 */
class BuiltColours(
    val paper: Int,
    val shelfBackground: Int,
    val surface: Int,
    val surface2: Int,
    val ink: Int,
    val muted: Int,
    val border: Int,
    val borderStrong: Int,
    val dashedBorder: Int,
    val accent: Int,
    val accentText: Int,
    val disabled: Int,
    val onDisabled: Int,
    /** The background gradient, top-left to bottom-right, always [BACKGROUND_STOPS] colours. */
    val background: List<Int>,
    /** The soft glow in the top-right corner (see-through). */
    val glow: Int,
)

/**
 * The maths behind the app colour sheet. It has no Android code in it, so it
 * can be tested on a computer (see AppColourMathTest).
 *
 * The idea: you pick one colour (plus up to three more for the background),
 * and everything else is worked out from it. Then every text colour is
 * checked against what it sits on, and made darker or lighter until it is
 * easy to read (4.5 : 1 or better).
 */
object AppColourMath {
    const val MAX_COLOURS = 4
    const val BACKGROUND_STOPS = 5

    /** The violet the app has always had. Choosing exactly this shows the original colours. */
    val CLASSIC = ColourChoice(248f, 0f)

    val PRESETS = listOf(
        ColourPreset("Holo Violet", 248f),
        ColourPreset("Ocean", 214f),
        ColourPreset("Lagoon", 186f),
        ColourPreset("Leaf", 146f),
        ColourPreset("Gold", 44f),
        ColourPreset("Ember", 14f),
        ColourPreset("Rose", 336f),
        ColourPreset("Orchid", 286f),
    )

    private val FAMILY = listOf(
        8f to "Ruby", 25f to "Ember", 40f to "Amber", 58f to "Gold", 80f to "Lime", 135f to "Leaf",
        165f to "Jade", 195f to "Lagoon", 225f to "Ocean", 255f to "Indigo", 275f to "Violet",
        305f to "Orchid", 325f to "Magenta", 350f to "Rose", 361f to "Ruby",
    )

    private const val WHITE = 0xFFFFFFFF.toInt()

    /** How far apart two places on the rainbow are, going the short way round. */
    fun hueDistance(a: Float, b: Float): Float {
        val d = abs((((a - b) % 360f) + 360f) % 360f)
        return min(d, 360f - d)
    }

    /** A friendly name for a colour: the preset's name if it's on one, else the colour family. */
    fun nameFor(hue: Float): String {
        PRESETS.firstOrNull { hueDistance(it.hue, hue) <= 3f }?.let { return it.name }
        val h = ((hue % 360f) + 360f) % 360f
        return FAMILY.first { h < it.first }.second
    }

    fun strengthWord(strength: Float): String = when {
        strength < 0.20f -> "Soft"
        strength < 0.45f -> "Gentle"
        strength < 0.70f -> "Rich"
        else -> "Vivid"
    }

    val GLITTER_STEPS = listOf("Off", "Faint", "Sparkly", "Dazzling", "Full holo")

    fun glitterWord(glitter: Float): String {
        val g = (glitter * 100).roundToInt()
        return when {
            g == 0 -> "Off"
            g < 30 -> "Faint"
            g < 60 -> "Sparkly"
            g < 85 -> "Dazzling"
            else -> "Full holo"
        }
    }

    /** Whether these picks are just the original violet, so the app shows its original colours. */
    fun isClassic(choices: List<ColourChoice>?): Boolean = choices == null || choices == listOf(CLASSIC)

    // ---------------------------------------------------------------- colour basics

    /** Rainbow place + saturation (0..100) + lightness (0..100), the "HSL" way of naming a colour. */
    fun hsl(hue: Float, saturation: Float, lightness: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 100f) / 100f
        val l = lightness.coerceIn(0f, 100f) / 100f
        val a = s * min(l, 1 - l)
        fun f(n: Int): Int {
            val k = (n + h / 30f) % 12f
            val v = l - a * max(-1f, min(min(k - 3f, 9f - k), 1f))
            return (v * 255f).roundToInt().coerceIn(0, 255)
        }
        return argb(255, f(0), f(8), f(4))
    }

    fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF
    fun withAlpha(c: Int, alpha: Float): Int = (c and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

    /** How bright a colour looks to the eye, 0 (black) to 1 (white). */
    fun luminance(c: Int): Double {
        fun ch(v: Int): Double {
            val x = v / 255.0
            return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(red(c)) + 0.7152 * ch(green(c)) + 0.0722 * ch(blue(c))
    }

    /** How easy one colour is to read on another. 4.5 or more is the usual "easy to read" line. */
    fun contrast(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /** Blends two colours: t = 0 gives [a], t = 1 gives [b]. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun m(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        return argb(m((a ushr 24) and 0xFF, (b ushr 24) and 0xFF), m(red(a), red(b)), m(green(a), green(b)), m(blue(a), blue(b)))
    }

    private fun darker(c: Int) = mix(c, 0xFF000000.toInt(), 0.04f)
    private fun lighter(c: Int) = mix(c, WHITE, 0.06f)

    /** Spreads a list of colours evenly into exactly [count] colours, so gradients can fade from one to another. */
    fun resample(stops: List<Int>, count: Int = BACKGROUND_STOPS): List<Int> {
        if (stops.size == 1) return List(count) { stops[0] }
        return List(count) { i ->
            val pos = i.toFloat() / (count - 1) * (stops.size - 1)
            val lo = pos.toInt().coerceAtMost(stops.size - 2)
            mix(stops[lo], stops[lo + 1], pos - lo)
        }
    }

    // ---------------------------------------------------------------- the palette

    /**
     * Builds the app's colours. The first pick is the main colour: it colours
     * buttons, headings and boxes. Any more picks only colour the background,
     * which blends from the main colour at the top to the last pick at the bottom.
     */
    fun build(choices: List<ColourChoice>, dark: Boolean): BuiltColours {
        val main = choices.first()
        val h = main.hue
        val k = main.strength.coerceIn(0f, 1f)
        val extras = choices.drop(1).take(MAX_COLOURS - 1)

        return if (!dark) {
            val s1 = hsl(h, 80f, 98.5f - 11 * k)
            val s2 = hsl(h + 6, 65 + 30 * k, 94.5f - 16 * k)
            val stops = if (extras.isEmpty()) listOf(s1, s2, hsl(h - 28, 70 + 25 * k, 92 - 20 * k))
            else listOf(s1, s2) + extras.map { e -> val ek = e.strength.coerceIn(0f, 1f); hsl(e.hue, 70 + 25 * ek, 92 - 20 * ek) }
            val background = resample(stops)
            val paper = hsl(h, 55 + 30 * k, 96.5f - 9 * k)
            val surface = hsl(h, 100f, 100 - 5 * k)
            // The darkest bit of the background is where text is hardest to read.
            val darkest = background.minBy { luminance(it) }
            var ink = hsl(h, 35 + 15 * k, 14 - 4 * k)
            var guard = 0
            while (contrast(ink, darkest) < 7.0 && guard++ < 60) ink = darker(ink)
            var muted = hsl(h, 15 + 15 * k, 41f)
            guard = 0
            while ((contrast(muted, darkest) < 4.5 || contrast(muted, surface) < 4.5) && guard++ < 60) muted = darker(muted)
            val sat = min(62 + 33 * k, 96f)
            var l = 52f
            var accent = hsl(h, sat, l)
            while ((contrast(accent, WHITE) < 6.0 || contrast(accent, darkest) < 4.5 || contrast(accent, paper) < 4.5) && l > 8f) {
                l -= 1f; accent = hsl(h, sat, l)
            }
            BuiltColours(
                paper = paper,
                shelfBackground = hsl(h, 50 + 30 * k, 93 - 9 * k),
                surface = surface,
                surface2 = hsl(h, 60 + 30 * k, 96 - 11 * k),
                ink = ink,
                muted = muted,
                border = hsl(h, 35 + 30 * k, 89 - 9 * k),
                borderStrong = hsl(h, 28 + 30 * k, 77 - 10 * k),
                dashedBorder = hsl(h, 18 + 20 * k, 58 - 8 * k),
                accent = accent,
                accentText = accent,
                disabled = hsl(h, 35 + 30 * k, 89 - 9 * k),
                onDisabled = hsl(h, 20f, 27f),
                background = background,
                glow = withAlpha(hsl(h + 140, 100f, 79 - 6 * k), 0.35f + 0.15f * k),
            )
        } else {
            val s1 = hsl(h, 37 + 38 * k, 15 + 9 * k)
            val s2 = hsl(h, 43 + 35 * k, 12 + 6 * k)
            val stops = if (extras.isEmpty()) listOf(s1, s2, hsl(h - 28, 50 + 35 * k, 9 + 5 * k))
            else listOf(s1, s2) + extras.map { e -> val ek = e.strength.coerceIn(0f, 1f); hsl(e.hue, 50 + 35 * ek, 11 + 7 * ek) }
            val background = resample(stops)
            val paper = hsl(h, 32 + 25 * k, 9 + 3 * k)
            val surface = hsl(h, 29 + 25 * k, 15 + 5 * k)
            // In dark mode the lightest bit of the background is where pale text is hardest to read.
            val lightest = background.maxBy { luminance(it) }
            var ink = hsl(h, 60f, 96f)
            var guard = 0
            while (contrast(ink, lightest) < 7.0 && guard++ < 60) ink = lighter(ink)
            var muted = hsl(h, 18 + 20 * k, 72f)
            guard = 0
            while ((contrast(muted, lightest) < 4.5 || contrast(muted, surface) < 4.5) && guard++ < 60) muted = lighter(muted)
            val sat = min(76 + 22 * k, 98f)
            var l = 59f
            var accent = hsl(h, sat, l)
            while (contrast(accent, WHITE) < 4.5 && l > 8f) { l -= 1f; accent = hsl(h, sat, l) }
            var t = 80f
            var accentText = hsl(h, 100f, t)
            while ((contrast(accentText, surface) < 7.0 || contrast(accentText, lightest) < 4.5) && t < 97f) { t += 1f; accentText = hsl(h, 100f, t) }
            BuiltColours(
                paper = paper,
                shelfBackground = hsl(h, 30 + 25 * k, 11 + 3 * k),
                surface = surface,
                surface2 = hsl(h, 30 + 25 * k, 20 + 6 * k),
                ink = ink,
                muted = muted,
                border = hsl(h, 26 + 20 * k, 26 + 5 * k),
                borderStrong = hsl(h, 23 + 20 * k, 36 + 5 * k),
                dashedBorder = hsl(h, 20 + 15 * k, 52 + 4 * k),
                accent = accent,
                accentText = accentText,
                disabled = hsl(h, 26 + 20 * k, 26 + 5 * k),
                onDisabled = muted,
                background = background,
                glow = withAlpha(hsl(h + 15, 52 + 30 * k, 55f), 0.28f + 0.27f * k),
            )
        }
    }

    // ---------------------------------------------------------------- saving

    /** Turns the picks into a short line of text for saving, like "214.0:0.65,286.0:0.65". */
    fun encode(choices: List<ColourChoice>): String = choices.joinToString(",") { "${it.hue}:${it.strength}" }

    /** Reads [encode]'s text back. Anything broken gives null (the original colours). */
    fun decode(text: String?): List<ColourChoice>? {
        if (text.isNullOrBlank()) return null
        val list = text.split(",").mapNotNull { part ->
            val bits = part.split(":")
            val hue = bits.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
            val strength = bits.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
            ColourChoice(((hue % 360f) + 360f) % 360f, strength.coerceIn(0f, 1f))
        }.take(MAX_COLOURS)
        return list.ifEmpty { null }
    }
}
