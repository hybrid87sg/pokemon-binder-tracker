package com.hybrid87sg.bindertracker.ui.three

import com.hybrid87sg.bindertracker.ui.three.BinderModel.COVER
import com.hybrid87sg.bindertracker.ui.three.BinderModel.HEIGHT
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Where each 3D binder stands in the wooden storage shelf, and where every
 * part of the shelf goes: the boards, the sides, the back, the LED strips at
 * the back and front of each cubby, and the name plates. Plain maths, so unit
 * tests can check it.
 *
 * The shelf is one wooden unit with a cubby (a "bay") for each row of binders.
 * Binders stand left to right like books. The top cubby's floor is at y = 0,
 * lower cubbies go down from there; the front edge of the boards is at z = 0.
 */
class ShelfLayout(count: Int, angleDegrees: Int, coversOnRight: Boolean, perRow: Int = 4) {
    /** 1 = covers to the right of the spine, -1 = to the left. Straight binders use 1. */
    val dir = if (angleDegrees == 0 || coversOnRight) 1 else -1
    /** How far each binder is turned around the up axis (radians). */
    val turn: Float = dir * (PI.toFloat() / 2f - Math.toRadians(angleDegrees.toDouble()).toFloat())

    class Spot(val position: Vec3, val row: Int)
    class Plank(val centre: Vec3, val size: Vec3)

    /** What a piece of the shelf is made of. */
    enum class Kind { Wood, Inside, Metal }
    class Piece(val centre: Vec3, val size: Vec3, val kind: Kind)

    /** One cubby: its floor and ceiling, and where its two LED strips go. */
    class Bay(val row: Int, val floor: Float, val top: Float, val width: Float, val depth: Float) {
        /** The strip along the top of the back wall. */
        val backStrip get() = Plank(Vec3(0f, top - 0.105f, -depth + 0.11f), Vec3(width - 0.22f, 0.03f, 0.03f))
        val backChannel get() = Plank(Vec3(0f, top - 0.06f, -depth + 0.08f), Vec3(width - 0.16f, 0.07f, 0.09f))
        /** The soft wash of light on the back wall, under the back strip. */
        val glow get() = Plank(Vec3(0f, (floor + top) / 2f, -depth + 0.012f), Vec3(width, top - floor, 0f))
        /** The strip tucked under the front edge of the board above, shining onto the spines. */
        val frontStrip get() = Plank(Vec3(0f, top - 0.07f, -0.14f), Vec3(width - 0.22f, 0.025f, 0.05f))
        val frontChannel get() = Plank(Vec3(0f, top - 0.03f, -0.14f), Vec3(width - 0.16f, 0.06f, 0.08f))

        /** Where the lights from the back strip sit ([n] along the cubby). */
        fun backLights(n: Int) = (0 until n).map { k -> Vec3(-width / 2f + width * (k + 0.5f) / n, top - 0.25f, -depth + 0.35f) }
        /** Where the lights from the front strip sit: just in front, so they light the spines. */
        fun frontLights(n: Int) = (0 until n).map { k -> Vec3(-width / 2f + width * (k + 0.5f) / n, top - 0.12f, 0.35f) }
    }

    /** A name plate on the front of the board under a binder. */
    class Label(val index: Int, val centre: Vec3, val width: Float, val height: Float)

    val spots = ArrayList<Spot>()
    /** The board under each row of binders. */
    val planks = ArrayList<Plank>()
    /** Every box the wooden unit is made of (boards, sides, top, back, kick plate, LED channels). */
    val pieces = ArrayList<Piece>()
    val bays = ArrayList<Bay>()
    val labels = ArrayList<Label>()
    val rows: Int
    /** The whole shelf, for fitting the camera. */
    var min = Vec3(0f, 0f, 0f); private set
    var max = Vec3(0f, 0f, 0f); private set

    init {
        rows = max(1, (count + perRow - 1) / perRow)
        val a = Math.toRadians(angleDegrees.toDouble()).toFloat()
        val (lo, hi) = BinderModel.bounds(dir)
        // The box corners of one turned binder (it sits with its hinge at 0).
        val corners = listOf(lo.x, hi.x).flatMap { x -> listOf(lo.z, hi.z).map { z -> Vec3(x, 0f, z).rotateY(turn) } }
        val bxMin = corners.minOf { it.x }; val bxMax = corners.maxOf { it.x }
        val bzMin = corners.minOf { it.z }; val bzMax = corners.maxOf { it.z }
        val binderW = bxMax - bxMin
        // Each binder's slot: its width and a little air (more when they lean).
        val slot = binderW + 0.12f + 0.1f * sin(a)
        val inner = perRow * slot + 0.3f // the inside width of every cubby
        val bayH = HEIGHT + 0.62f // room above the binders for the back strip
        val board = 0.2f
        val side = 0.16f
        val depth = max(bzMax - bzMin, 1.2f) + 0.45f
        val rowGap = bayH + board
        for (r in 0 until rows) {
            val y = -r * rowGap
            val n = min(perRow, count - r * perRow).coerceAtLeast(0)
            for (k in 0 until n) {
                val left = -inner / 2f + 0.15f + k * slot + (slot - binderW) / 2f
                // Binders stand a little back from the front edge (the front strip is above them).
                spots += Spot(Vec3(left - bxMin, y, -0.16f - bzMax), r)
                // Its name plate hangs on the front of the board, under the binder.
                val pw = slot - 0.16f
                val ph = pw * 150f / 400f
                labels += Label(r * perRow + k, Vec3(left + binderW / 2f, y - board / 2f - ph / 2f + 0.09f, 0.012f), pw, ph)
            }
            val plank = Plank(Vec3(0f, y - board / 2f, -depth / 2f), Vec3(inner, board, depth))
            planks += plank
            pieces += Piece(plank.centre, plank.size, Kind.Wood)
            val bay = Bay(r, y, y + bayH, inner, depth)
            bays += bay
            pieces += Piece(bay.backChannel.centre, bay.backChannel.size, Kind.Metal)
            pieces += Piece(bay.frontChannel.centre, bay.frontChannel.size, Kind.Metal)
        }
        val top = bayH + board // top of the unit
        val bottom = -(rows - 1) * rowGap - board // bottom of the lowest board
        val tall = top - bottom
        // The top, the two sides, the back, and a kick plate set back under it all.
        pieces += Piece(Vec3(0f, bayH + board / 2f, -depth / 2f + 0.02f), Vec3(inner + 2 * side, board, depth + 0.04f), Kind.Wood)
        for (s in listOf(-1f, 1f)) {
            pieces += Piece(Vec3(s * (inner / 2f + side / 2f), (top + bottom) / 2f, -depth / 2f + 0.02f), Vec3(side, tall, depth + 0.04f), Kind.Wood)
        }
        pieces += Piece(Vec3(0f, (top + bottom) / 2f, -depth - 0.03f), Vec3(inner + 2 * side, tall, 0.06f), Kind.Inside)
        pieces += Piece(Vec3(0f, bottom - 0.16f, -0.12f), Vec3(inner + 2 * side, 0.32f, 0.08f), Kind.Wood)
        min = Vec3(-(inner / 2f + side), bottom - 0.32f, -depth - 0.06f)
        max = Vec3(inner / 2f + side, top, 0.02f)
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
