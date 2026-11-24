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

    // ---- A picture of the page ----

    /**
     * A picture of a binder's first page exactly as the page screen draws it
     * (the cover round it, the rings, the cards and their names), and where on
     * the screen it was. The 3D binder is painted with it, so when it lands the
     * page screen can take over with nothing changing.
     */
    class Shot(val file: java.io.File, val rect: List<Float>, val spread: Boolean, val version: Long)

    fun shotKey(binderId: String, screenWidthDp: Int, screenHeightDp: Int, dark: Boolean) = "$binderId@${screenWidthDp}x$screenHeightDp${if (dark) ":dark" else ""}"

    private val shots = HashMap<String, Shot>()
    private var shotsLoaded = false

    /** The latest picture for this binder and screen size, or null if the page hasn't been seen yet. */
    fun shot(context: Context, key: String): Shot? {
        version // (read so the shelf notices new pictures)
        loadShots(context)
        return shots[key]?.takeIf { it.file.exists() }
    }

    /** Saves a new picture ([rect]: left, top, right, bottom in the window, in pixels). Call off the main thread. */
    fun saveShot(context: Context, key: String, picture: android.graphics.Bitmap, rect: List<Float>, spread: Boolean) {
        loadShots(context)
        val dir = java.io.File(context.filesDir, "page-shots").apply { mkdirs() }
        val name = key.hashCode().toUInt().toString(16)
        val file = java.io.File(dir, "$name.png")
        val temp = java.io.File(dir, "$name.tmp")
        val ok = runCatching { temp.outputStream().use { picture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }.getOrDefault(false)
        if (!ok || !temp.renameTo(file)) { temp.delete(); return }
        val stamp = System.currentTimeMillis()
        val whole = rect.map { Math.round(it).toFloat() }
        shotPrefs(context).edit().putString(key, (listOf(if (spread) 1 else 0, stamp) + whole.map { it.toLong() }).joinToString(",")).apply()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            shots[key] = Shot(file, whole, spread, stamp)
            version++
        }
    }

    private fun loadShots(context: Context) {
        if (shotsLoaded) return
        shotsLoaded = true
        val dir = java.io.File(context.filesDir, "page-shots")
        shotPrefs(context).all.forEach { (k, v) ->
            val nums = (v as? String)?.split(",")?.mapNotNull { it.toLongOrNull() } ?: return@forEach
            if (nums.size != 6) return@forEach
            val file = java.io.File(dir, "${k.hashCode().toUInt().toString(16)}.png")
            shots[k] = Shot(file, nums.drop(2).map { it.toFloat() }, nums[0] == 1L, nums[1])
        }
    }

    private fun shotPrefs(context: Context) = context.applicationContext.getSharedPreferences("page_shots", Context.MODE_PRIVATE)

    // ---- Arriving from the 3D binder ----

    private var arrivingUntil = 0L
    private var revealAt = 0L

    /**
     * The 3D binder has started landing on the page. [revealInMillis] is how long
     * until it has landed: the page screen stays hidden until then, and its cards
     * are already in place, so they skip their pop-in.
     */
    fun arrive(revealInMillis: Long = 0L) {
        val now = SystemClock.uptimeMillis()
        revealAt = now + revealInMillis
        arrivingUntil = revealAt + 1500
    }

    val arriving: Boolean get() = SystemClock.uptimeMillis() < arrivingUntil

    /** How long the page screen should wait before showing itself (0: show it now). */
    fun revealDelay(): Int = (revealAt - SystemClock.uptimeMillis()).coerceIn(0L, 2000L).toInt()
}
