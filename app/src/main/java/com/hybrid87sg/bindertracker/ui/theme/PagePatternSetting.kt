package com.hybrid87sg.bindertracker.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * Each binder's page pattern, saved on this phone only (like App colour and
 * Main/Master set), so friends you share a binder with keep their own look.
 *
 * Saved in a small settings file ("page_pattern"), one line per binder, keyed
 * by the binder's ID. No database change needed.
 */
object PagePatternSetting {
    private const val FILE = "page_pattern"

    // Binder ID -> its pattern. Binders not listed use the default look.
    private val patterns = mutableStateMapOf<String, PagePattern>()

    /** Reads every saved pattern. Called once when the app opens. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        patterns.clear()
        prefs.all.forEach { (id, v) -> if (v is String) patterns[id] = PagePatternMath.decode(v) }
    }

    /** The pattern for a binder (the default look if it hasn't been changed). Compose redraws when it changes. */
    fun of(binderId: String?): PagePattern = binderId?.let { patterns[it] } ?: PagePattern()

    /** Shows a pattern straight away without saving it (the editor's live preview, and Cancel). */
    fun show(binderId: String, pattern: PagePattern) {
        patterns[binderId] = pattern
    }

    /** Shows and saves. */
    fun save(context: Context, binderId: String, pattern: PagePattern) {
        show(binderId, pattern)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(binderId, PagePatternMath.encode(pattern))
            .apply()
    }
}
