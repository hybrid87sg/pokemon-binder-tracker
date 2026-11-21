package com.hybrid87sg.bindertracker.ui.components

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The maths behind the colour picker, kept free of any Android code so it
 * can be tested on a computer.
 *
 * Colours are passed around as a plain number like 0xFF8C2F2F, where the
 * pairs of hex digits are: alpha (see-through-ness), red, green, blue.
 *
 * The picker describes a colour with two easy sliders instead of numbers:
 *  - hue: where it sits on the rainbow, 0 to 360 (0 = red, 120 = green, 240 = blue)
 *  - shade: 0 = pale and pastel, 0.5 = rich and bold, 1 = deep and dark
 */
object ColorMath {

    /** Turns a rainbow position + saturation + brightness (HSV) into a colour number. */
    fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val c = value * saturation
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = value - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    /** The reverse: returns (hue 0..360, saturation 0..1, value 0..1). */
    fun argbToHsv(color: Int): Triple<Float, Float, Float> {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val hue = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0) it + 360f else it }
        val sat = if (max == 0f) 0f else d / max
        return Triple(hue, sat, max)
    }

    /**
     * The colour at [hue] on the rainbow and [shade] on the pale-to-dark slider.
     * The slider blends from a pastel, through a bold colour, to a deep one.
     */
    fun shadeColor(hue: Float, shade: Float): Int {
        val t = shade.coerceIn(0f, 1f)
        val pale = hsvToArgb(hue, 0.30f, 0.97f)
        val rich = hsvToArgb(hue, 0.85f, 0.80f)
        val deep = hsvToArgb(hue, 0.90f, 0.22f)
        return if (t <= 0.5f) mix(pale, rich, t * 2f) else mix(rich, deep, (t - 0.5f) * 2f)
    }

    /**
     * Where an existing colour sits on the sliders, so that opening the
     * picker on a binder starts the sliders near its current colour.
     * Returns (hue, shade).
     */
    fun slidersFor(color: Int): Pair<Float, Float> {
        val hue = argbToHsv(color).first
        // Try 101 points along the shade slider and keep the closest one.
        val shade = (0..100).map { it / 100f }.minBy { distance(shadeColor(hue, it), color) }
        return hue to shade
    }

    /** "#8C2F2F" style text, which is how the database stores binder colours. */
    fun toHex(color: Int): String = "#%02X%02X%02X".format(red(color), green(color), blue(color))

    /**
     * Reads text a person typed, like "8c2f2f", "#8C2F2F" or "#f00".
     * Returns null when it isn't a colour code.
     */
    fun parseHex(text: String): Int? {
        val digits = text.trim().removePrefix("#")
        val full = when (digits.length) {
            3 -> digits.map { "$it$it" }.joinToString("")
            6 -> digits
            else -> return null
        }
        if (!full.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return (0xFF000000.toInt()) or full.toInt(16)
    }

    /** True for light colours, where white writing would be hard to read. */
    fun isLight(color: Int): Boolean {
        // Standard "perceived brightness" weights: eyes see green as brightest.
        val brightness = 0.299 * red(color) + 0.587 * green(color) + 0.114 * blue(color)
        return brightness > 160
    }

    private fun mix(a: Int, b: Int, t: Float): Int = argb(
        (red(a) + (red(b) - red(a)) * t).roundToInt(),
        (green(a) + (green(b) - green(a)) * t).roundToInt(),
        (blue(a) + (blue(b) - blue(a)) * t).roundToInt(),
    )

    private fun distance(a: Int, b: Int): Int {
        val dr = red(a) - red(b)
        val dg = green(a) - green(b)
        val db = blue(a) - blue(b)
        return dr * dr + dg * dg + db * db
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF
}
