package com.hybrid87sg.bindertracker.ui.three

import com.hybrid87sg.bindertracker.data.ShelfView
import com.hybrid87sg.bindertracker.ui.three.BinderModel.HEIGHT
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Where each 3D binder stands on the simple shelf. Plain maths, so unit
 * tests can check it.
 *
 * Two ways to show the binders ([ShelfView]):
 * - Spine side: binders stand like books, spines facing you, up to 5 a row
 *   with the same gap between each.
 * - Cover side: front covers face you, [columns] a row (2 on a phone, 4 when
 *   the phone is unfolded), with the binder's name and count under each.
 *
 * Each row stands on a thin ledge. The top row's ledge is at y = 0, lower
 * rows go down from there; the front edge of the ledges is at z = 0.
 */
class ShelfLayout(count: Int, val view: ShelfView = ShelfView.Spine, columns: Int = 2) {
    /** The cover is always to the right of the spine on this shelf. */
    val dir = 1
    /** How far each binder is turned around the up axis (radians): spine or cover to the front. */
    val turn: Float = if (view == ShelfView.Spine) PI.toFloat() / 2f else 0f

    class Spot(val position: Vec3, val row: Int)
    class Plank(val centre: Vec3, val size: Vec3)

    /** The name and count shown under a binder (Cover side only). */
    class Label(val index: Int, val centre: Vec3, val width: Float, val height: Float)

    /** How many binders stand in one row. */
    val perRow = if (view == ShelfView.Spine) SPINES_PER_ROW else columns.coerceAtLeast(1)
    val spots = ArrayList<Spot>()
    /** The ledge under each row of binders. */
    val ledges = ArrayList<Plank>()
    val labels = ArrayList<Label>()
    val rows: Int
    /** How far down each row is from the one above. */
    val rowGap: Float
    /** How many rows the camera should at least make room for (so a few binders don't look huge). */
    val minRows: Float
    /** The whole shelf, for fitting the camera. */
    var min = Vec3(0f, 0f, 0f); private set
    var max = Vec3(0f, 0f, 0f); private set

    init {
        rows = max(1, (count + perRow - 1) / perRow)
        val (lo, hi) = BinderModel.bounds(dir)
        // The box corners of one turned binder (it sits with its hinge at 0).
        val corners = listOf(lo.x, hi.x).flatMap { x -> listOf(lo.z, hi.z).map { z -> Vec3(x, 0f, z).rotateY(turn) } }
        val bxMin = corners.minOf { it.x }; val bxMax = corners.maxOf { it.x }
        val bzMin = corners.minOf { it.z }; val bzMax = corners.maxOf { it.z }
        val binderW = bxMax - bxMin
        val spine = view == ShelfView.Spine
        val gap = if (spine) 0.3f else 0.45f
        // Every row is as wide as a full row, so the binders line up in columns.
        val rowW = perRow * binderW + (perRow - 1) * gap
        val depth = (bzMax - bzMin) + 0.35f
        rowGap = HEIGHT + if (spine) 0.75f else 1.15f
        minRows = if (spine) 1.25f else 1.5f
        for (r in 0 until rows) {
            val y = -r * rowGap
            val n = min(perRow, count - r * perRow).coerceAtLeast(0)
            for (k in 0 until n) {
                val left = -rowW / 2f + k * (binderW + gap)
                spots += Spot(Vec3(left - bxMin, y, -0.05f - bzMax), r)
                if (!spine) {
                    val h = binderW * CAPTION_H / CAPTION_W
                    labels += Label(r * perRow + k, Vec3(left + binderW / 2f, y - 0.42f, 0.02f), binderW, h)
                }
            }
            ledges += Plank(Vec3(0f, y - LEDGE / 2f, -depth / 2f + 0.08f), Vec3(rowW + 0.6f, LEDGE, depth))
        }
        val bottom = -(rows - 1) * rowGap - if (spine) 0.2f else 0.85f
        min = Vec3(-(rowW + 0.6f) / 2f, bottom, -depth)
        max = Vec3((rowW + 0.6f) / 2f, HEIGHT + 0.1f, 0.1f)
    }

    companion object {
        const val SPINES_PER_ROW = 5
        const val LEDGE = 0.07f
        /** The size of the picture each caption is drawn on. */
        const val CAPTION_W = 512
        const val CAPTION_H = 150
    }
}

/**
 * A simple camera: where it is, what it looks at, and how wide it sees.
 * Used to fit the shelf on screen and to find which binder a finger touched.
 */
class Cam(val eye: Vec3, val target: Vec3, val fovYDegrees: Float, val aspect: Float) {
    val forward = (target - eye).normalized()
    val right = forward.cross(Vec3(0f, 1f, 0f)).normalized()
    val up = right.cross(forward)
    private val t = tan(Math.toRadians(fovYDegrees / 2.0)).toFloat()

    /** The point on screen (0..1 across, 0..1 down) for a point in the world. */
    fun project(p: Vec3): Pair<Float, Float> {
        val d = p - eye
        val z = d.dot(forward)
        val x = d.dot(right) / (z * t * aspect)
        val y = d.dot(up) / (z * t)
        return (x + 1f) / 2f to (1f - y) / 2f
    }

    /** The direction from the eye through a point on screen (0..1, 0..1). */
    fun ray(sx: Float, sy: Float): Vec3 {
        val x = (sx * 2f - 1f) * t * aspect
        val y = (1f - sy * 2f) * t
        return (forward + right * x + up * y).normalized()
    }
}

/** Does a ray hit a box? Returns how far along, or null. */
fun hitBox(origin: Vec3, dir: Vec3, lo: Vec3, hi: Vec3): Float? {
    var tMin = -Float.MAX_VALUE; var tMax = Float.MAX_VALUE
    val o = floatArrayOf(origin.x, origin.y, origin.z); val d = floatArrayOf(dir.x, dir.y, dir.z)
    val l = floatArrayOf(lo.x, lo.y, lo.z); val h = floatArrayOf(hi.x, hi.y, hi.z)
    for (i in 0..2) {
        if (kotlin.math.abs(d[i]) < 1e-6f) {
            if (o[i] < l[i] || o[i] > h[i]) return null
        } else {
            var a = (l[i] - o[i]) / d[i]; var b = (h[i] - o[i]) / d[i]
            if (a > b) { val s = a; a = b; b = s }
            tMin = max(tMin, a); tMax = min(tMax, b)
            if (tMin > tMax) return null
        }
    }
    return if (tMax < 0f) null else max(tMin, 0f)
}

/**
 * Where each card goes on the first page inside the 3D binder, laid out
 * like the page screen: [columns] x [rows] pockets, row by row.
 * Each entry is left, top, right, bottom as fractions of the page (0..1).
 */
object SheetGrid {
    /** The page's size inside the 3D binder (same as BinderModel's sheet). */
    const val WIDTH = BinderModel.COVER - 0.26f
    const val HEIGHT = BinderModel.HEIGHT - 0.22f
    private const val CARD = 63f / 88f

    fun cards(columns: Int, rows: Int): List<FloatArray> {
        val cols = columns.coerceAtLeast(1); val rws = rows.coerceAtLeast(1)
        // Work in page units so the cards keep their real shape.
        val pad = WIDTH * 0.06f
        val gap = WIDTH * 0.035f
        val cellW = (WIDTH - 2 * pad - (cols - 1) * gap) / cols
        val cellH = (HEIGHT - 2 * pad - (rws - 1) * gap) / rws
        val w = min(cellW, cellH * CARD)
        val h = w / CARD
        // Centre the whole grid on the page.
        val gridW = cols * w + (cols - 1) * gap
        val gridH = rws * h + (rws - 1) * gap
        val x0 = (WIDTH - gridW) / 2f
        val y0 = (HEIGHT - gridH) / 2f
        return (0 until cols * rws).map { i ->
            val x = x0 + (i % cols) * (w + gap)
            val y = y0 + (i / cols) * (h + gap)
            floatArrayOf(x / WIDTH, y / HEIGHT, (x + w) / WIDTH, (y + h) / HEIGHT)
        }
    }

    /**
     * Where the page screen showed its cards ([rects]: left, top, right, bottom
     * in screen pixels, card after card), turned into places on the 3D page.
     * The cards keep exactly the same arrangement, scaled so they fill most
     * of the 3D page, centred on it.
     */
    class Landing(
        /** Each card as fractions of the page, like [cards]. */
        val cards: List<FloatArray>,
        /** How big one screen pixel is on the 3D page (for placing the camera). */
        val worldPerPx: Float,
        /** The middle of the cards on the screen, in pixels (the middle of the 3D page). */
        val centreX: Float,
        val centreY: Float,
    )

    fun fromScreen(rects: List<Float>, fill: Float = 0.88f): Landing? {
        if (rects.isEmpty() || rects.size % 4 != 0) return null
        val n = rects.size / 4
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until n) {
            l = min(l, rects[4 * i]); t = min(t, rects[4 * i + 1])
            r = max(r, rects[4 * i + 2]); b = max(b, rects[4 * i + 3])
        }
        val gw = r - l; val gh = b - t
        if (gw < 1f || gh < 1f) return null
        val s = min(fill * WIDTH / gw, fill * HEIGHT / gh)
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        val cards = (0 until n).map { i ->
            floatArrayOf(
                0.5f + (rects[4 * i] - cx) * s / WIDTH,
                0.5f + (rects[4 * i + 1] - cy) * s / HEIGHT,
                0.5f + (rects[4 * i + 2] - cx) * s / WIDTH,
                0.5f + (rects[4 * i + 3] - cy) * s / HEIGHT,
            )
        }
        return Landing(cards, s, cx, cy)
    }
}
