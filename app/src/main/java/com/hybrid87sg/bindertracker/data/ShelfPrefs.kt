package com.hybrid87sg.bindertracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which LED strips light each cubby of the shelf. */
enum class LedStrips(val label: String) {
    Both("Back + front"), Front("Front"), Back("Back"), Off("Off");

    val back get() = this == Both || this == Back
    val front get() = this == Both || this == Front
}

/** The wood the storage shelf is made of. */
enum class ShelfWood(val label: String) { Oak("Light oak"), Walnut("Walnut"), White("White"), Black("Black") }

/**
 * One LED colour, picked on the LED colour ring like the app colour.
 * [hue] is the place on the rainbow (0 to 360). [strength] goes from
 * 0 (white light with a hint of the colour) to 100 (vivid).
 */
data class LedColour(val hue: Int, val strength: Int)

/** A ready-made LED colour (one colour, or a few that blend along the strip). */
data class LedPreset(val name: String, val colours: List<LedColour>)

/**
 * How the My binders shelf looks.
 *
 * [angle] is how far each binder is turned, in degrees (0 = straight on, the
 * classic shelf). [coversOnRight] picks the side the cover shows on.
 * The rest is the wooden storage shelf: its LED strips, their brightness and
 * colours (up to 4 that blend along the strip), the wood, and whether the
 * lights are switched on.
 */
data class ShelfLook(
    val angle: Int = 0,
    val coversOnRight: Boolean = true,
    // Real 3D binders (drawn with the Filament 3D engine). Off = the flat drawn shelf.
    val threeD: Boolean = true,
    val strips: LedStrips = LedStrips.Both,
    /** 0 to 100. */
    val brightness: Int = 70,
    val led: List<LedColour> = listOf(WARM_WHITE),
    val wood: ShelfWood = ShelfWood.Walnut,
    val lightsOn: Boolean = true,
) {
    val turned: Boolean get() = angle > 0

    /** True when some light is actually showing. */
    val lit: Boolean get() = lightsOn && strips != LedStrips.Off

    companion object {
        /** The stops on the Edit shelf slider. 35 is the most (Ril's choice). */
        val Angles = listOf(0, 15, 20, 25, 30, 35)
        const val MAX_LED_COLOURS = 4
        val WARM_WHITE = LedColour(34, 48)

        val LedPresets = listOf(
            LedPreset("Warm white", listOf(WARM_WHITE)),
            LedPreset("Cool white", listOf(LedColour(205, 30))),
            LedPreset("App colour", listOf(LedColour(248, 80))),
            LedPreset("Holo rainbow", listOf(LedColour(330, 85), LedColour(44, 90), LedColour(160, 75), LedColour(214, 85))),
            LedPreset("Ocean", listOf(LedColour(214, 90))),
            LedPreset("Leaf", listOf(LedColour(146, 80))),
            LedPreset("Gold", listOf(LedColour(44, 95))),
            LedPreset("Ember", listOf(LedColour(14, 95))),
            LedPreset("Rose", listOf(LedColour(336, 85))),
        )

        /** LED colours as saved: "34:48;330:85". */
        fun ledToText(colours: List<LedColour>) = colours.joinToString(";") { "${it.hue}:${it.strength}" }

        fun ledFromText(text: String?): List<LedColour>? {
            if (text.isNullOrBlank()) return null
            val list = text.split(';').mapNotNull { part ->
                val bits = part.split(':')
                val h = bits.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val s = bits.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                LedColour(((h % 360) + 360) % 360, s.coerceIn(0, 100))
            }.take(MAX_LED_COLOURS)
            return list.ifEmpty { null }
        }
    }
}

/**
 * The shelf look, kept on this phone only (like [ViewPrefs]): friends who
 * share a binder choose their own. Saved in the small "shelf" settings file.
 */
class ShelfPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("shelf", Context.MODE_PRIVATE)

    private val _look = MutableStateFlow(
        ShelfLook(
            angle = prefs.getInt(KEY_ANGLE, 0).takeIf { it in ShelfLook.Angles } ?: 0,
            coversOnRight = prefs.getBoolean(KEY_RIGHT, true),
            threeD = prefs.getBoolean(KEY_3D, true),
            strips = LedStrips.entries.firstOrNull { it.name == prefs.getString(KEY_STRIPS, null) } ?: LedStrips.Both,
            brightness = prefs.getInt(KEY_BRIGHT, 70).coerceIn(0, 100),
            led = ShelfLook.ledFromText(prefs.getString(KEY_LED, null)) ?: listOf(ShelfLook.WARM_WHITE),
            wood = ShelfWood.entries.firstOrNull { it.name == prefs.getString(KEY_WOOD, null) } ?: ShelfWood.Walnut,
            lightsOn = prefs.getBoolean(KEY_ON, true),
        ),
    )
    val look: StateFlow<ShelfLook> = _look.asStateFlow()

    fun set(look: ShelfLook) {
        prefs.edit()
            .putInt(KEY_ANGLE, look.angle)
            .putBoolean(KEY_RIGHT, look.coversOnRight)
            .putBoolean(KEY_3D, look.threeD)
            .putString(KEY_STRIPS, look.strips.name)
            .putInt(KEY_BRIGHT, look.brightness)
            .putString(KEY_LED, ShelfLook.ledToText(look.led))
            .putString(KEY_WOOD, look.wood.name)
            .putBoolean(KEY_ON, look.lightsOn)
            .apply()
        _look.value = look
    }

    private companion object {
        const val KEY_ANGLE = "angle"
        const val KEY_RIGHT = "covers_right"
        const val KEY_3D = "three_d"
        const val KEY_STRIPS = "led_strips"
        const val KEY_BRIGHT = "led_brightness"
        const val KEY_LED = "led_colours"
        const val KEY_WOOD = "wood"
        const val KEY_ON = "lights_on"
    }
}
