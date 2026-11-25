package com.hybrid87sg.bindertracker.ui.three

import com.hybrid87sg.bindertracker.data.LedColour
import com.hybrid87sg.bindertracker.ui.theme.AppColourMath
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The LED colour maths, shared by the 3D shelf and the Edit shelf sheet.
 * Plain Kotlin (colours are numbers like 0xFFFFD9A8), so unit tests can check it.
 */
object Led {
    /**
     * The colour an LED gives off. Strength 0 is almost white with a hint of
     * the colour, 100 is the vivid colour (a little darker, so it still reads
     * as a colour and not as white).
     */
    fun argb(c: LedColour): Int = AppColourMath.hsl(c.hue.toFloat(), c.strength.toFloat(), 92f - 0.4f * c.strength)

    /** The colour at [t] (0 = left end, 1 = right end) of a strip that blends [colours]. */
    fun at(colours: List<LedColour>, t: Float): Int {
        if (colours.isEmpty()) return argb(com.hybrid87sg.bindertracker.data.ShelfLook.WARM_WHITE)
        if (colours.size == 1) return argb(colours[0])
        val p = t.coerceIn(0f, 1f) * (colours.size - 1)
        val i = floor(p).toInt().coerceAtMost(colours.size - 2)
        return mix(argb(colours[i]), argb(colours[i + 1]), p - i)
    }

    /** Halfway (or [f] of the way) from colour [a] to colour [b]. */
    fun mix(a: Int, b: Int, f: Float): Int {
        fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
        fun m(shift: Int) = (ch(a, shift) + (ch(b, shift) - ch(a, shift)) * f).roundToInt().coerceIn(0, 255)
        return AppColourMath.argb(255, m(16), m(8), m(0))
    }

    /** A word for the outer ring: how much colour there is. */
    fun strengthWord(strength: Int) = when {
        strength < 20 -> "White"
        strength < 45 -> "Soft"
        strength < 75 -> "Rich"
        else -> "Vivid"
    }

    /** The red, green and blue of a colour as the 3D engine wants them (linear, 0 to 1). */
    fun linear(argb: Int): FloatArray = floatArrayOf(
        BinderModel.lin((argb shr 16) and 0xFF),
        BinderModel.lin((argb shr 8) and 0xFF),
        BinderModel.lin(argb and 0xFF),
    )
}
