package com.hybrid87sg.bindertracker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** The designs a card back can have (see CardBackArt for how each is drawn). */
enum class CardBackStyle(val key: String, val title: String, val blurb: String) {
    Original("original", "Original blue", "Today's back. The same blue in every binder"),
    Orbit("orbit", "Orbit", "Spinning rings round a star"),
    Prism("prism", "Prism", "Light rays from a centre gem"),
    Lattice("lattice", "Lattice", "Woven diamonds with a ring badge"),
    Own("own", "Your own picture", "Stays on this phone");

    /** Orbit, Prism and Lattice take on each binder's colour. */
    val followsBinder get() = this == Orbit || this == Prism || this == Lattice

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Original
    }
}

/**
 * Which card back this phone shows, everywhere a card is face down: empty
 * pockets, the back of a spinning card and the page inside a 3D binder.
 * [picturePath] is your own picture (only used with [CardBackStyle.Own]).
 */
data class CardBackChoice(
    val style: CardBackStyle = CardBackStyle.Original,
    val picturePath: String? = null,
    val placement: CoverPlacement = CoverPlacement(),
    val version: Long = 0L,
)

/**
 * The card back setting. It belongs to this phone (like Edit shelf), and your
 * own picture stays in the app's private files: it isn't shared or uploaded.
 */
class CardBackPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences("card_back", Context.MODE_PRIVATE)
    private val file = File(File(context.filesDir, "card-back"), "back.jpg")
    private val _choice = MutableStateFlow(load())
    val choice: StateFlow<CardBackChoice> = _choice.asStateFlow()

    /** Picks a design. Picking "Your own picture" only works once there is one. */
    fun setStyle(style: CardBackStyle) {
        if (style == CardBackStyle.Own && _choice.value.picturePath == null) return
        prefs.edit().putString(STYLE, style.key).apply()
        _choice.value = _choice.value.copy(style = style)
    }

    /**
     * Saves your own picture and uses it. [path] is a picture from
     * CoverPictures.importDraft, or the current one (then only the placement changes).
     */
    suspend fun savePicture(path: String, placement: CoverPlacement) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        if (File(path).absolutePath != file.absolutePath) {
            File(path).copyTo(file, overwrite = true)
            File(path).delete()
        }
        val p = placement.clamped()
        val version = System.currentTimeMillis()
        prefs.edit().putString(STYLE, CardBackStyle.Own.key)
            .putFloat(X, p.x).putFloat(Y, p.y).putFloat(ZOOM, p.zoom).putLong(VERSION, version).apply()
        _choice.value = CardBackChoice(CardBackStyle.Own, file.absolutePath, p, version)
    }

    /** Removes your own picture and goes back to the original blue back. */
    suspend fun removePicture() = withContext(Dispatchers.IO) {
        file.delete()
        prefs.edit().putString(STYLE, CardBackStyle.Original.key).remove(X).remove(Y).remove(ZOOM).remove(VERSION).apply()
        _choice.value = CardBackChoice()
    }

    private fun load(): CardBackChoice {
        val path = file.takeIf { it.exists() }?.absolutePath
        val style = CardBackStyle.of(prefs.getString(STYLE, null)).let { if (it == CardBackStyle.Own && path == null) CardBackStyle.Original else it }
        return CardBackChoice(
            style = style,
            picturePath = path,
            placement = CoverPlacement(prefs.getFloat(X, 0.5f), prefs.getFloat(Y, 0.5f), prefs.getFloat(ZOOM, 1f)).clamped(),
            version = prefs.getLong(VERSION, 0L),
        )
    }

    private companion object {
        const val STYLE = "style"
        const val X = "x"
        const val Y = "y"
        const val ZOOM = "zoom"
        const val VERSION = "version"
    }
}
