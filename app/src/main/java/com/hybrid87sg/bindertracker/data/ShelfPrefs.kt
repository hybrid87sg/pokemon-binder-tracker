package com.hybrid87sg.bindertracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the binders show on the My binders shelf. */
enum class ShelfView {
    /** Spines facing you, up to 5 a row, the same gap between each. */
    Spine,
    /** Front covers facing you, in 2 columns (4 when the phone is unfolded). */
    Cover,
}

/**
 * How the My binders shelf looks.
 *
 * [view] picks Spine side or Cover side (the 3D shelf). [angle] and
 * [coversOnRight] are only used by the flat drawn shelf (when 3D is off) and
 * the little preview on Edit binder.
 */
data class ShelfLook(
    val angle: Int = 0,
    val coversOnRight: Boolean = true,
    // Real 3D binders (drawn with the Filament 3D engine). Off = the flat drawn shelf.
    val threeD: Boolean = true,
    val view: ShelfView = ShelfView.Spine,
) {
    val turned: Boolean get() = angle > 0

    companion object {
        /** The binder angles the flat shelf knows. 35 is the most (Ril's choice). */
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
            threeD = prefs.getBoolean(KEY_3D, true),
            view = ShelfView.entries.firstOrNull { it.name == prefs.getString(KEY_VIEW, null) } ?: ShelfView.Spine,
        ),
    )
    val look: StateFlow<ShelfLook> = _look.asStateFlow()

    fun set(look: ShelfLook) {
        prefs.edit()
            .putInt(KEY_ANGLE, look.angle)
            .putBoolean(KEY_RIGHT, look.coversOnRight)
            .putBoolean(KEY_3D, look.threeD)
            .putString(KEY_VIEW, look.view.name)
            .apply()
        _look.value = look
    }

    private companion object {
        const val KEY_ANGLE = "angle"
        const val KEY_RIGHT = "covers_right"
        const val KEY_3D = "three_d"
        const val KEY_VIEW = "view"
    }
}
