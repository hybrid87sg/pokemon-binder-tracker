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

    /**
     * Like [get], but if this exact screen size hasn't been seen yet, borrows the
     * spots from the closest size seen in the same fold state (folded or
     * unfolded), stretched to this screen. The phone's screen size can shift a
     * little (taskbar, display size settings), and the spots from the other
     * fold state never fit.
     */
    fun getNear(context: Context, columns: Int, rows: Int, screenWidthDp: Int, screenHeightDp: Int): List<Float>? {
        get(context, key(columns, rows, screenWidthDp, screenHeightDp))?.let { return it }
        val prefix = "$columns:$rows:"
        val wide = isSpread(screenWidthDp, screenHeightDp)
        val best = saved.keys.mapNotNull { k ->
            if (!k.startsWith(prefix)) return@mapNotNull null
            val size = parseSize(k.removePrefix(prefix)) ?: return@mapNotNull null
            if (isSpread(size.first, size.second) != wide || !close(size, screenWidthDp, screenHeightDp)) null else k to size
        }.minByOrNull { (_, size) -> kotlin.math.abs(size.first - screenWidthDp) + kotlin.math.abs(size.second - screenHeightDp) } ?: return null
        val rects = saved[best.first] ?: return null
        return stretch(rects, best.second, screenWidthDp, screenHeightDp)
    }

    /** The same test the page screen uses for showing two pages side by side. */
    fun isSpread(screenWidthDp: Int, screenHeightDp: Int) = screenWidthDp >= 600 && screenHeightDp >= 480

    private fun parseSize(text: String): Pair<Int, Int>? {
        val parts = text.split("x")
        if (parts.size != 2) return null
        val w = parts[0].toIntOrNull() ?: return null
        val h = parts[1].toIntOrNull() ?: return null
        return if (w > 0 && h > 0) w to h else null
    }

    // Within a fifth of this screen's size both ways.
    private fun close(size: Pair<Int, Int>, w: Int, h: Int) =
        kotlin.math.abs(size.first - w) <= w / 5 && kotlin.math.abs(size.second - h) <= h / 5

    /** Left, top, right, bottom spots saved at [from] (dp), stretched to a [w] x [h] dp screen. */
    private fun stretch(rects: List<Float>, from: Pair<Int, Int>, w: Int, h: Int): List<Float> {
        if (from.first == w && from.second == h) return rects
        val sx = w.toFloat() / from.first
        val sy = h.toFloat() / from.second
        return rects.mapIndexed { i, v -> if (i % 2 == 0) v * sx else v * sy }
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
    class Shot(val file: java.io.File, val rect: List<Float>, val spread: Boolean, val version: Long, val look: Int? = null)

    /**
     * A short fingerprint of what's on a binder's first page (which card, grey
     * missing card or empty pocket is where). Saved with each picture, so the
     * shelf can tell when the picture is out of date: e.g. cards were moved
     * while the phone was folded, and this is the unfolded picture.
     */
    fun lookOf(columns: Int, rows: Int, pockets: List<Pair<Int, String>>): Int =
        ("${columns}x$rows|" + pockets.sortedBy { it.first }.joinToString(";") { "${it.first}=${it.second}" }).hashCode()

    /** The same fingerprint, for the first page the 3D shelf has. */
    fun lookOf(sheet: SheetLook): Int = lookOf(
        sheet.columns, sheet.rows,
        sheet.pockets.map { p ->
            p.position to when (p.kind) {
                SheetPocket.Kind.Card -> p.cardId ?: "-"
                SheetPocket.Kind.Missing -> "g" + (p.cardId ?: "")
                SheetPocket.Kind.Empty -> "E"
                else -> "-"
            }
        },
    )

    fun shotKey(binderId: String, screenWidthDp: Int, screenHeightDp: Int, dark: Boolean) = "$binderId@${screenWidthDp}x$screenHeightDp${if (dark) ":dark" else ""}"

    private val shots = HashMap<String, Shot>()
    private var shotsLoaded = false

    /**
     * The picture to land on for this binder, this screen size and fold state.
     * If this exact size hasn't been seen, the closest size seen in the same
     * fold state, stretched to fit. Never a picture from the other fold state
     * (one page versus two side by side), which is what made the opening look
     * wrong after folding or unfolding the phone.
     */
    fun shotNear(context: Context, binderId: String, screenWidthDp: Int, screenHeightDp: Int, dark: Boolean): Shot? {
        val wide = isSpread(screenWidthDp, screenHeightDp)
        shot(context, shotKey(binderId, screenWidthDp, screenHeightDp, dark))?.let { if (it.spread == wide) return it }
        val prefix = "$binderId@"
        val best = shots.keys.mapNotNull { k ->
            if (!k.startsWith(prefix)) return@mapNotNull null
            val rest = k.removePrefix(prefix)
            val isDark = rest.endsWith(":dark")
            if (isDark != dark) return@mapNotNull null
            val size = parseSize(rest.removeSuffix(":dark")) ?: return@mapNotNull null
            val shot = shots[k]?.takeIf { it.file.exists() && it.spread == wide } ?: return@mapNotNull null
            if (isSpread(size.first, size.second) != wide || !close(size, screenWidthDp, screenHeightDp)) null else Triple(shot, size, k)
        }.minByOrNull { (_, size) -> kotlin.math.abs(size.first - screenWidthDp) + kotlin.math.abs(size.second - screenHeightDp) } ?: return null
        val (shot, size) = best
        return Shot(shot.file, stretch(shot.rect, size, screenWidthDp, screenHeightDp), shot.spread, shot.version, shot.look)
    }

    /** The latest picture for this binder and screen size, or null if the page hasn't been seen yet. */
    fun shot(context: Context, key: String): Shot? {
        version // (read so the shelf notices new pictures)
        loadShots(context)
        return shots[key]?.takeIf { it.file.exists() }
    }

    /** Saves a new picture ([rect]: left, top, right, bottom in the window, in pixels). Call off the main thread. */
    fun saveShot(context: Context, key: String, picture: android.graphics.Bitmap, rect: List<Float>, spread: Boolean, look: Int? = null) {
        loadShots(context)
        val dir = java.io.File(context.filesDir, "page-shots").apply { mkdirs() }
        val name = key.hashCode().toUInt().toString(16)
        val file = java.io.File(dir, "$name.png")
        val temp = java.io.File(dir, "$name.tmp")
        val ok = runCatching { temp.outputStream().use { picture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }.getOrDefault(false)
        if (!ok || !temp.renameTo(file)) { temp.delete(); return }
        val stamp = System.currentTimeMillis()
        val whole = rect.map { Math.round(it).toFloat() }
        val numbers = listOf(if (spread) 1 else 0, stamp) + whole.map { it.toLong() } + listOfNotNull(look?.toLong())
        shotPrefs(context).edit().putString(key, numbers.joinToString(",")).apply()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            shots[key] = Shot(file, whole, spread, stamp, look)
            version++
        }
    }

    private fun loadShots(context: Context) {
        if (shotsLoaded) return
        shotsLoaded = true
        val dir = java.io.File(context.filesDir, "page-shots")
        shotPrefs(context).all.forEach { (k, v) ->
            val nums = (v as? String)?.split(",")?.mapNotNull { it.toLongOrNull() } ?: return@forEach
            // (Older pictures have no fingerprint: 6 numbers instead of 7.)
            if (nums.size != 6 && nums.size != 7) return@forEach
            val file = java.io.File(dir, "${k.hashCode().toUInt().toString(16)}.png")
            shots[k] = Shot(file, nums.subList(2, 6).map { it.toFloat() }, nums[0] == 1L, nums[1], nums.getOrNull(6)?.toInt())
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
    fun arrive(revealInMillis: Long = 0L, outOfDate: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        softReveal = outOfDate
        revealAt = now + revealInMillis
        arrivingUntil = revealAt + 1500
    }

    val arriving: Boolean get() = SystemClock.uptimeMillis() < arrivingUntil

    // The picture landed on was out of date (cards changed since), so the page
    // screen fades in more slowly and the changed cards blend in instead of popping.
    private var softReveal = false

    /** How long the page screen takes to fade in over the landed binder. */
    fun revealFadeMillis(): Int = if (softReveal) 420 else 160

    /** How long the page screen should wait before showing itself (0: show it now). */
    fun revealDelay(): Int = (revealAt - SystemClock.uptimeMillis()).coerceIn(0L, 2000L).toInt()
}
