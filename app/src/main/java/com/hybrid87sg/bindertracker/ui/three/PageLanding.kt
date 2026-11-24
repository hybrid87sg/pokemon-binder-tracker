package com.hybrid87sg.bindertracker.ui.three

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Where the card page draws its cards on this phone's screen, so the 3D
 * binder can glide in and stop with its cards in exactly those spots. Then
 * the card page takes over without anything jumping.
 *
 * The page screen notes the spots each time it settles (see PageScreen), one
 * set for each pocket layout and screen size (folded and unfolded are
 * different). They're kept on the phone, so they're there after a restart.
 */
object PageLanding {
    private val saved = HashMap<String, List<Float>>()
    private var loaded = false
    // Bumped when spots change, so the shelf paints its binders again.
    private var version by mutableIntStateOf(0)

    fun key(columns: Int, rows: Int, screenWidthDp: Int, screenHeightDp: Int) =
        "$columns:$rows:${screenWidthDp}x$screenHeightDp"

    /** Each card's left, top, right, bottom in the window (pixels), in pocket order; null if not seen yet. */
    fun get(context: Context, key: String): List<Float>? {
        version // (read so the shelf notices new spots)
        load(context)
        return saved[key]
    }

    fun record(context: Context, key: String, rects: List<Float>) {
        load(context)
        val whole = rects.map { Math.round(it).toFloat() }
        if (saved[key] == whole) return
        saved[key] = whole
        prefs(context).edit().putString(key, whole.joinToString(",") { it.toInt().toString() }).apply()
        version++
    }

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        prefs(context).all.forEach { (k, v) ->
            val nums = (v as? String)?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: return@forEach
            if (nums.isNotEmpty() && nums.size % 4 == 0) saved[k] = nums
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("page_landing", Context.MODE_PRIVATE)

    // ---- Arriving from the 3D binder ----

    private var arrivingUntil = 0L

    /** The 3D binder has just opened onto the page: its cards are already in place, so skip their pop-in. */
    fun arrive() { arrivingUntil = SystemClock.uptimeMillis() + 1500 }

    val arriving: Boolean get() = SystemClock.uptimeMillis() < arrivingUntil
}
