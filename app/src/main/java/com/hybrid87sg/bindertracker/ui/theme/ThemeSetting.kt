package com.hybrid87sg.bindertracker.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The three choices on the Account screen. */
enum class ThemeMode(val label: String) {
    System("Follow phone"),
    Light("Light"),
    Dark("Dark"),
}

/**
 * Remembers which mode the person picked, in a small settings file on the
 * phone ("SharedPreferences"), so it is still set after the app is closed.
 */
object ThemeSetting {
    private const val FILE = "settings"
    private const val KEY = "theme_mode"

    /** The current choice. It is "state", so screens update when it changes. */
    var mode by mutableStateOf(ThemeMode.System)
        private set

    /** Reads the saved choice. Called once when the app opens. */
    fun load(context: Context) {
        val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        mode = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.System
    }

    /** Saves a new choice and shows it straight away. */
    fun set(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, newMode.name).apply()
    }

    /** True when the app should be dark, given the choice and whether the phone is in dark mode. */
    fun isDark(mode: ThemeMode, phoneIsDark: Boolean): Boolean = when (mode) {
        ThemeMode.System -> phoneIsDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    /** Whether the phone itself is set to dark mode, read outside of Compose. */
    fun phoneIsDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
