package com.hybrid87sg.bindertracker.ui.components

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The 3D maths for a binder turned on the shelf (no drawing here, just numbers,
 * so it can be tested on a computer).
 *
 * Picture the binder from the top: the spine faces you, and the cover goes
 * straight back from one edge of the spine (the "hinge"). Turning the binder by
 * [angleDegrees] swings the hinge edge towards you, so the cover comes into
 * view on that side ([coverOnRight]). Things further away look smaller, as seen
 * by a camera [camera] pixels in front of the spine, level with the shelf, so the
 * bottom of every binder stays on the shelf.
 *
 * All sizes are in pixels. In the binder's own box (spine [width] × [height]),
 * x = 0 is the spine's left edge and y = 0 its top.
 */
class BinderTurn(
    val width: Float,
    val height: Float,
    val depth: Float,
    val angleDegrees: Float,
    val coverOnRight: Boolean,
    val camera: Float,
) {
    private val rad = angleDegrees * PI.toFloat() / 180f
    private val s = sin(rad)
    private val c = cos(rad)
    private val side = if (coverOnRight) 1f else -1f
    private val half = width / 2f

    /** How much bigger a point at depth [z] looks (z > 0 is towards you). */
    private fun grow(z: Float) = camera / (camera - z)

    /** Where a point [u] across the spine (0..width) ends up, left to right. */
    fun spineX(u: Float): Float {
        val x = (u - half) * side
        return half + side * x * c * grow(x * s)
    }

    /** How much the spine is enlarged at [u] (used for its top edge). */
    fun spineGrow(u: Float): Float = grow((u - half) * side * s)

    /** Where a point [t] back along the cover from the hinge (0..depth) ends up. */
    fun coverX(t: Float): Float {
        val x = half * c + t * s
        val z = half * s - t * c
        return half + side * x * grow(z)
    }

    fun coverGrow(t: Float): Float = grow(half * s - t * c)

    /** The height [y] (0 = top) of a point drawn [g] times bigger, with the shelf (bottom) fixed. */
    fun y(y: Float, g: Float): Float = height + (y - height) * g

    val spineLeft: Float get() = min(spineX(0f), spineX(width))
    val spineRight: Float get() = max(spineX(0f), spineX(width))

    /** Width the spine takes up on the shelf once turned. */
    val spineWidth: Float get() = spineRight - spineLeft

    /** How far the cover pokes out past the spine's edge. */
    val coverReach: Float get() = abs(coverX(depth) - coverX(0f))

    /** Darkness laid over the spine and cover (0..1), from how much they face away from the light. */
    val spineShade: Float get() = 0.28f * (1f - c)
    val coverShade: Float get() = 0.38f * (1f - s) + 0.08f

    /**
     * The 4 corners on screen (x0,y0, … clockwise from top-left) of the cover
     * picture, which is drawn hinge-side first: on the left when the cover is on the
     * right, and the other way round.
     */
    fun coverCorners(): FloatArray {
        // The picture's left edge is at the hinge (t = 0) when the cover is on the right.
        val leftT = if (coverOnRight) 0f else depth
        val rightT = depth - leftT
        val gl = coverGrow(leftT)
        val gr = coverGrow(rightT)
        val xl = coverX(leftT)
        val xr = coverX(rightT)
        return floatArrayOf(xl, y(0f, gl), xr, y(0f, gr), xr, y(height, gr), xl, y(height, gl))
    }

    fun spineCorners(): FloatArray {
        val gl = spineGrow(0f)
        val gr = spineGrow(width)
        val xl = spineX(0f)
        val xr = spineX(width)
        return floatArrayOf(xl, y(0f, gl), xr, y(0f, gr), xr, y(height, gr), xl, y(height, gl))
    }

    companion object {
        /** Cover depth compared with the binder's height (a real binder's cover is nearly as wide as it is tall). */
        const val COVER_RATIO = 0.79f

        /** Camera distance compared with the binder's height. */
        const val CAMERA_RATIO = 3.75f

        fun of(width: Float, height: Float, angleDegrees: Float, coverOnRight: Boolean) =
            BinderTurn(width, height, height * COVER_RATIO, angleDegrees, coverOnRight, height * CAMERA_RATIO)
    }
}

/**
 * How a row of turned binders fits on a shelf: everything shrinks by [scale]
 * if four binders plus the last cover would be wider than the shelf.
 */
data class ShelfFit(val scale: Float, val gap: Float, val spineWidth: Float, val coverReach: Float) {
    companion object {
        /**
         * [width]/[height]: a binder's normal spine size. [gapStraight] is the space between
         * binders standing straight; turned binders spread out by up to [gapExtra] more so each
         * cover peeks out. [available] is the shelf's inside width.
         */
        fun of(
            angleDegrees: Float,
            coverOnRight: Boolean,
            width: Float,
            height: Float,
            gapStraight: Float,
            gapExtra: Float,
            available: Float,
            perRow: Int,
        ): ShelfFit {
            if (angleDegrees <= 0f) return ShelfFit(1f, gapStraight, width, 0f)
            val turn = BinderTurn.of(width, height, angleDegrees, coverOnRight)
            val gap = gapStraight + gapExtra * sin(angleDegrees * PI.toFloat() / 180f)
            val total = perRow * turn.spineWidth + (perRow - 1) * gap + turn.coverReach
            val scale = if (total <= 0f || available <= 0f) 1f else min(1f, available / total)
            return ShelfFit(scale, gap * scale, turn.spineWidth * scale, turn.coverReach * scale)
        }
    }
}
