package com.hybrid87sg.bindertracker.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The person's app colours and background glitter, from the colour sheet on
 * the Me tab. Saved on this phone only (like light/dark mode), so people you
 * share binders with keep their own look.
 */
object AppColourSetting {
    private const val FILE = "settings"
    private const val KEY_COLOURS = "app_colours"
    private const val KEY_GLITTER = "glitter"

    /** The colours showing right now. null = the app's original violet colours. */
    var colours by mutableStateOf<List<ColourChoice>?>(null)
        private set

    /** How much glitter the background has, 0 (off) to 1 (full holo). */
    var glitter by mutableFloatStateOf(0f)
        private set

    /** Reads the saved choice. Called once when the app opens, before anything is drawn. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        colours = AppColourMath.decode(prefs.getString(KEY_COLOURS, null)).takeUnless { AppColourMath.isClassic(it) }
        glitter = prefs.getFloat(KEY_GLITTER, 0f).coerceIn(0f, 1f)
    }

    /** Shows colours straight away without saving them (the sheet's live preview, and Cancel). */
    fun show(newColours: List<ColourChoice>?, newGlitter: Float) {
        colours = newColours?.takeUnless { AppColourMath.isClassic(it) }
        glitter = newGlitter.coerceIn(0f, 1f)
    }

    /** Shows and saves. */
    fun save(context: Context, newColours: List<ColourChoice>?, newGlitter: Float) {
        show(newColours, newGlitter)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_COLOURS, colours?.let { AppColourMath.encode(it) })
            .putFloat(KEY_GLITTER, glitter)
            .apply()
    }
}
