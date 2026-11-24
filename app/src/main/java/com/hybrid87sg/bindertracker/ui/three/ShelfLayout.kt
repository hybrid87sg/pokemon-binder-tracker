package com.hybrid87sg.bindertracker.ui.three

import com.hybrid87sg.bindertracker.ui.three.BinderModel.COVER
import com.hybrid87sg.bindertracker.ui.three.BinderModel.HEIGHT
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Where each 3D binder stands on the shelves, and where the planks go.
 * Plain maths, so unit tests can check it.
 */
class ShelfLayout(count: Int, angleDegrees: Int, coversOnRight: Boolean, perRow: Int = 4) {
    /** 1 = covers to the right of the spine, -1 = to the left. Straight binders use 1. */
    val dir = if (angleDegrees == 0 || coversOnRight) 1 else -1
    /** How far each binder is turned around the up axis (radians). */
    val turn: Float = dir * (PI.toFloat() / 2f - Math.toRadians(angleDegrees.toDouble()).toFloat())

    class Spot(val position: Vec3, val row: Int)
    class Plank(val centre: Vec3, val size: Vec3)

    val spots = ArrayList<Spot>()
    val planks = ArrayList<Plank>()
    val panels = ArrayList<Plank>()
    /** The whole shelf, for fitting the camera. */
    var min = Vec3(0f, 0f, 0f); private set
    var max = Vec3(0f, 0f, 0f); private set

    init {
        val rows = max(1, (count + perRow - 1) / perRow)
        val rowGap = HEIGHT + 0.75f
        val a = Math.toRadians(angleDegrees.toDouble()).toFloat()
        val (lo, hi) = BinderModel.bounds(dir)
        // The box corners of one turned binder (it sits with its hinge at 0).
        val corners = listOf(lo.x, hi.x).flatMap { x -> listOf(lo.z, hi.z).map { z -> Vec3(x, 0f, z).rotateY(turn) } }
        val bxMin = corners.minOf { it.x }; val bxMax = corners.maxOf { it.x }; val bzMax = corners.maxOf { it.z }
        val width = bxMax - bxMin
        val step = width + 0.05f + 0.12f * sin(a)
        var widest = 0f
        val rowWidths = ArrayList<Float>()
        for (r in 0 until rows) {
            val n = min(perRow, count - r * perRow).coerceAtLeast(0)
            rowWidths += if (n == 0) step else n * step - (step - width)
        }
        widest = rowWidths.max()
        val depth = COVER + 0.5f
        for (r in 0 until rows) {
            val y = -r * rowGap
            val n = min(perRow, count - r * perRow).coerceAtLeast(0)
            val start = -rowWidths[r] / 2f
            for (k in 0 until n) {
                val left = start + k * step
                spots += Spot(Vec3(left - bxMin, y, -0.15f - bzMax), r)
            }
            planks += Plank(Vec3(0f, y - 0.08f, -depth / 2f + 0.1f), Vec3(widest + 0.6f, 0.16f, depth))
            panels += Plank(Vec3(0f, y + (HEIGHT + 0.55f) / 2f - 0.08f, -depth + 0.07f), Vec3(widest + 0.6f, HEIGHT + 0.55f, 0.06f))
        }
        val bottom = -(rows - 1) * rowGap - 0.16f
        min = Vec3(-(widest + 0.6f) / 2f, bottom, -depth)
        max = Vec3((widest + 0.6f) / 2f, HEIGHT + 0.47f, 0.1f)
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
