package com.hybrid87sg.bindertracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the binders stand on the My binders shelf.
 *
 * [angle] is how far each binder is turned, in degrees (0 = straight on, the
 * classic shelf). [coversOnRight] picks the side the cover shows on.
 */
data class ShelfLook(val angle: Int = 0, val coversOnRight: Boolean = true) {
    val turned: Boolean get() = angle > 0

    companion object {
        /** The stops on the Edit shelf slider. 35 is the most (Ril's choice). */
        val Angles = listOf(0, 15, 20, 25, 30, 35)
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
        ),
    )
    val look: StateFlow<ShelfLook> = _look.asStateFlow()

    fun set(look: ShelfLook) {
        prefs.edit().putInt(KEY_ANGLE, look.angle).putBoolean(KEY_RIGHT, look.coversOnRight).apply()
        _look.value = look
    }

    private companion object {
        const val KEY_ANGLE = "angle"
        const val KEY_RIGHT = "covers_right"
    }
}
