package com.hybrid87sg.bindertracker.scan

import com.hybrid87sg.bindertracker.sync.RingCode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reads a binder ring code (see [RingCode]) from a camera picture.
 *
 * It works on a grey picture (one brightness number per pixel), in steps:
 * 1. Shrink the picture, and split it into dark and light pixels.
 * 2. Find a dark patch shaped like a circle or oval with holes in it: that's
 *    the disc. A circle seen at an angle looks like an oval, and the oval's
 *    size and tilt say how to "unsquash" it back into a circle.
 * 3. Read the brightness all the way round each of the three rings.
 * 4. Try every turn of the disc (the phone can be held any way up), and keep
 *    the one whose slots repair into a real code.
 *
 * Plain Kotlin with no Android parts, so it can be tested on a computer.
 */
object RingCodeReader {
    private const val MAX_SIDE = 640
    private const val STEPS_PER_POSITION = 24
    private const val ROUND = RingCode.POSITIONS * STEPS_PER_POSITION
    // Smallest disc worth trying, in pixels of the shrunk picture.
    private const val MIN_AREA = 700

    /**
     * Where a found disc sits in the picture. [toPicture] turns a point on the
     * flat ring code (disc radius 1, y pointing down) into picture pixels.
     */
    private class Disc(
        // Centre and size of the outline, used to keep the numbers small.
        val mx: Double, val my: Double, val size: Double,
        // How far the code leans away from the camera (its "vanishing line"), 0 when flat-on.
        val l1: Double, val l2: Double,
        // The unsquashed circle: centre and shape.
        val qx: Double, val qy: Double, val q11: Double, val q21: Double, val q22: Double,
    ) {
        fun toPicture(ux: Double, uy: Double, out: DoubleArray) {
            val x = qx + q11 * ux
            val y = qy + q21 * ux + q22 * uy
            val wt = 1 - l1 * x - l2 * y
            out[0] = mx + size * x / wt
            out[1] = my + size * y / wt
        }
    }

    /**
     * A ring code the camera spotted. [x], [y] and [radius] are in pixels of
     * the picture (radius includes the white edge). [code] is null when the
     * disc was seen but couldn't be read yet (too small, blurry, half hidden).
     */
    class Sighting(val x: Float, val y: Float, val radius: Float, val code: String?)

    /** The share code in the picture, or null. [gray] holds 0 to 255 per pixel, row by row. */
    fun read(gray: IntArray, width: Int, height: Int): String? = look(gray, width, height)?.code

    /**
     * Where a ring code is in the picture, and its code if it can be read.
     * Null when there's no ring code to be seen at all.
     */
    fun look(gray: IntArray, width: Int, height: Int): Sighting? {
        val factor = max(1, ceil(max(width, height) / MAX_SIDE.toDouble()).toInt())
        val w = width / factor
        val h = height / factor
        if (w < 32 || h < 32) return null
        val img = if (factor == 1) gray else shrink(gray, width, w, h, factor)
        fun sighting(d: Disc, code: String?) = Sighting(
            ((d.mx + 0.5) * factor).toFloat(),
            ((d.my + 0.5) * factor).toFloat(),
            (d.size * RingCode.HALO_RADIUS * factor).toFloat(),
            code,
        )
        var seen: Disc? = null
        val split = otsu(img)
        for (threshold in intArrayOf(split, (split * 0.8).toInt(), minOf(250, (split * 1.15).toInt()))) {
            for (disc in findDiscs(img, w, h, threshold)) {
                if (seen == null) seen = disc
                readDisc(img, w, h, disc)?.let { return sighting(disc, it) }
            }
        }
        return seen?.let { sighting(it, null) }
    }

    private fun shrink(gray: IntArray, width: Int, w: Int, h: Int, f: Int): IntArray {
        val out = IntArray(w * h)
        val n = f * f
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0
            for (dy in 0 until f) {
                val row = (y * f + dy) * width + x * f
                for (dx in 0 until f) sum += gray[row + dx]
            }
            out[y * w + x] = sum / n
        }
        return out
    }

    /** The brightness that best splits the picture into dark and light (Otsu's method). */
    private fun otsu(img: IntArray): Int {
        val hist = IntArray(256)
        for (v in img) hist[v.coerceIn(0, 255)]++
        val total = img.size.toDouble()
        var sumAll = 0.0
        for (i in 0 until 256) sumAll += i * hist[i].toDouble()
        var sumDark = 0.0
        var countDark = 0.0
        var best = 0.0
        var split = 128
        for (t in 0 until 256) {
            countDark += hist[t]
            if (countDark == 0.0) continue
            val countLight = total - countDark
            if (countLight == 0.0) break
            sumDark += t * hist[t].toDouble()
            val meanDark = sumDark / countDark
            val meanLight = (sumAll - sumDark) / countLight
            val between = countDark * countLight * (meanDark - meanLight) * (meanDark - meanLight)
            if (between > best) {
                best = between
                split = t
            }
        }
        return split
    }

    /** Dark patches shaped like a disc with holes, biggest first. */
    private fun findDiscs(img: IntArray, w: Int, h: Int, threshold: Int): List<Disc> {
        val labels = IntArray(w * h)
        val stack = IntArray(w * h)
        class Patch(val label: Int, val area: Int, val x0: Int, val y0: Int, val x1: Int, val y1: Int)
        val patches = mutableListOf<Patch>()
        var next = 0
        for (start in 0 until w * h) {
            if (labels[start] != 0 || img[start] >= threshold) continue
            next++
            labels[start] = next
            var top = 0
            stack[top++] = start
            var area = 0
            var x0 = w; var y0 = h; var x1 = 0; var y1 = 0
            while (top > 0) {
                val p = stack[--top]
                area++
                val x = p % w
                val y = p / w
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
                if (x > 0 && labels[p - 1] == 0 && img[p - 1] < threshold) { labels[p - 1] = next; stack[top++] = p - 1 }
                if (x < w - 1 && labels[p + 1] == 0 && img[p + 1] < threshold) { labels[p + 1] = next; stack[top++] = p + 1 }
                if (y > 0 && labels[p - w] == 0 && img[p - w] < threshold) { labels[p - w] = next; stack[top++] = p - w }
                if (y < h - 1 && labels[p + w] == 0 && img[p + w] < threshold) { labels[p + w] = next; stack[top++] = p + w }
            }
            // A disc cut off by the picture's edge can't be read, so skip it.
            if (area >= MIN_AREA && x0 > 0 && y0 > 0 && x1 < w - 1 && y1 < h - 1) {
                patches += Patch(next, area, x0, y0, x1, y1)
            }
        }
        return patches.sortedByDescending { it.area }.take(6).mapNotNull { p ->
            discShape(labels, w, p.label, p.area, p.x0 - 1, p.y0 - 1, p.x1 + 1, p.y1 + 1)
        }
    }

    /** The oval a patch fills once its holes are filled in, or null if it isn't disc shaped. */
    private fun discShape(labels: IntArray, w: Int, label: Int, area: Int, x0: Int, y0: Int, x1: Int, y1: Int): Disc? {
        val lw = x1 - x0 + 1
        val lh = y1 - y0 + 1
        // Mark everything reachable from the box's edge without crossing the patch: that's outside.
        val outside = BooleanArray(lw * lh)
        val stack = IntArray(lw * lh)
        var top = 0
        fun push(lx: Int, ly: Int) {
            val i = ly * lw + lx
            if (!outside[i] && labels[(y0 + ly) * w + x0 + lx] != label) {
                outside[i] = true
                stack[top++] = i
            }
        }
        for (lx in 0 until lw) { push(lx, 0); push(lx, lh - 1) }
        for (ly in 0 until lh) { push(0, ly); push(lw - 1, ly) }
        while (top > 0) {
            val i = stack[--top]
            val lx = i % lw
            val ly = i / lw
            if (lx > 0) push(lx - 1, ly)
            if (lx < lw - 1) push(lx + 1, ly)
            if (ly > 0) push(lx, ly - 1)
            if (ly < lh - 1) push(lx, ly + 1)
        }
        var n = 0.0; var sx = 0.0; var sy = 0.0
        for (ly in 0 until lh) for (lx in 0 until lw) if (!outside[ly * lw + lx]) { n++; sx += lx; sy += ly }
        if (n == 0.0) return null
        val mx = sx / n
        val my = sy / n
        var cxx = 0.0; var cyy = 0.0; var cxy = 0.0
        for (ly in 0 until lh) for (lx in 0 until lw) if (!outside[ly * lw + lx]) {
            val dx = lx - mx
            val dy = ly - my
            cxx += dx * dx; cyy += dy * dy; cxy += dx * dy
        }
        cxx /= n; cyy /= n; cxy /= n
        val half = (cxx + cyy) / 2
        val spread = sqrt(((cxx - cyy) / 2) * ((cxx - cyy) / 2) + cxy * cxy)
        val l1 = half + spread
        val l2 = half - spread
        if (l2 <= 0) return null
        val a = 2 * sqrt(l1)
        val b = 2 * sqrt(l2)
        if (b / a < 0.45) return null
        // A filled oval covers π·a·b pixels.
        val fill = n / (PI * a * b)
        if (fill < 0.9 || fill > 1.1) return null
        // The rings and the middle leave holes: a solid dark blob isn't a ring code.
        val holes = (n - area) / n
        if (holes < 0.18 || holes > 0.7) return null
        val angle = 0.5 * atan2(2 * cxy, cxx - cyy)
        val c = cos(angle)
        val s = sin(angle)
        // The edge must follow the oval all the way round (a square would not).
        var edge = 0
        var onOval = 0
        for (ly in 1 until lh - 1) for (lx in 1 until lw - 1) {
            val i = ly * lw + lx
            if (outside[i]) continue
            if (!outside[i - 1] && !outside[i + 1] && !outside[i - lw] && !outside[i + lw]) continue
            edge++
            val dx = lx - mx
            val dy = ly - my
            val u = (dx * c + dy * s) / a
            val v = (-dx * s + dy * c) / b
            val r = sqrt(u * u + v * v)
            if (r in 0.88..1.12) onOval++
        }
        if (edge == 0 || onOval < edge * 0.85) return null

        // Work in small numbers: centred on the outline, with its long radius as 1.
        val outer = Conic.ofFilledOval(0.0, 0.0, cxx / (a * a), cyy / (a * a), cxy / (a * a))
        val flatOn = Disc(x0 + mx, y0 + my, a, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0)
        // A tilted code looks smaller at its far side. The white middle circle
        // shows by how much: with both circles, the lean can be worked out.
        val middle = middleCircle(labels, w, label, x0, y0, lw, lh, mx, my, a, n) ?: return unsquash(outer, 0.0, 0.0, flatOn)
        val lean = vanishingLine(outer, middle) ?: return unsquash(outer, 0.0, 0.0, flatOn)
        return unsquash(outer, lean.first, lean.second, flatOn)
    }

    /**
     * The white circle in the middle (with the logo filled in), as an oval in
     * the small numbers, or null if it can't be found cleanly.
     */
    private fun middleCircle(labels: IntArray, w: Int, label: Int, x0: Int, y0: Int, lw: Int, lh: Int, mx: Double, my: Double, a: Double, discPixels: Double): Conic? {
        val start = my.toInt() * lw + mx.toInt()
        if (labels[(y0 + my.toInt()) * w + x0 + mx.toInt()] == label) return null
        // The white middle: everything reachable from the centre without crossing the disc.
        val inside = BooleanArray(lw * lh)
        val stack = IntArray(lw * lh)
        var top = 0
        inside[start] = true
        stack[top++] = start
        while (top > 0) {
            val i = stack[--top]
            val lx = i % lw
            val ly = i / lw
            if (lx == 0 || ly == 0 || lx == lw - 1 || ly == lh - 1) return null
            for (j in intArrayOf(i - 1, i + 1, i - lw, i + lw)) {
                if (!inside[j] && labels[(y0 + j / lw) * w + x0 + j % lw] != label) {
                    inside[j] = true
                    stack[top++] = j
                }
            }
        }
        var n = 0.0; var sx = 0.0; var sy = 0.0
        for (i in inside.indices) if (inside[i]) { n++; sx += i % lw; sy += i / lw }
        val share = n / discPixels
        val expected = RingCode.CENTRE_RADIUS * RingCode.CENTRE_RADIUS
        if (share < expected * 0.6 || share > expected * 1.5) return null
        val cx = sx / n
        val cy = sy / n
        if (sqrt((cx - mx) * (cx - mx) + (cy - my) * (cy - my)) > 0.25 * a) return null
        var cxx = 0.0; var cyy = 0.0; var cxy = 0.0
        for (i in inside.indices) if (inside[i]) {
            val dx = i % lw - cx
            val dy = i / lw - cy
            cxx += dx * dx; cyy += dy * dy; cxy += dx * dy
        }
        val a2 = a * a
        return Conic.ofFilledOval((cx - mx) / a, (cy - my) / a, cxx / n / a2, cyy / n / a2, cxy / n / a2)
    }

    /**
     * The picture of two circles with the same centre gives away the
     * "vanishing line" of the flat surface they're on: (l1, l2) for the
     * line 1 + l1·x + l2·y = 0. Null if the two ovals don't fit together.
     */
    private fun vanishingLine(outer: Conic, middle: Conic): Pair<Double, Double>? {
        val inv = middle.inverse() ?: return null
        val n = Conic.times(inv, outer.m)
        val t = n[0] + n[4] + n[8]
        val minors = (n[0] * n[4] - n[1] * n[3]) + (n[0] * n[8] - n[2] * n[6]) + (n[4] * n[8] - n[5] * n[7])
        val root = sqrt(maxOf(0.0, t * t - 3 * minors))
        var best: DoubleArray? = null
        var bestScore = Double.MAX_VALUE
        for (lambda in doubleArrayOf((t + root) / 3, (t - root) / 3)) {
            val m = DoubleArray(9) { outer.m[it] - lambda * middle.m[it] }
            // How far m is from a single line (all its 2×2 minors would be 0).
            var minorSum = 0.0
            for (r1 in 0 until 3) for (r2 in r1 + 1 until 3) for (c1 in 0 until 3) for (c2 in c1 + 1 until 3) {
                val d = m[r1 * 3 + c1] * m[r2 * 3 + c2] - m[r1 * 3 + c2] * m[r2 * 3 + c1]
                minorSum += d * d
            }
            val size = m.sumOf { it * it }
            if (size == 0.0) continue
            val score = minorSum / (size * size)
            if (score < bestScore) { bestScore = score; best = m }
        }
        val m = best ?: return null
        if (bestScore > 0.05) return null
        // Each column of m points along the line; take the strongest.
        var col = 0
        var colSize = -1.0
        for (cIdx in 0 until 3) {
            val sz = m[cIdx] * m[cIdx] + m[3 + cIdx] * m[3 + cIdx] + m[6 + cIdx] * m[6 + cIdx]
            if (sz > colSize) { colSize = sz; col = cIdx }
        }
        val l3 = m[6 + col]
        if (abs(l3) < 1e-9) return null
        // The line is 1 + l1·x + l2·y = 0 in the picture.
        val l1 = m[col] / l3
        val l2 = m[3 + col] / l3
        // The line must lie well outside the disc, or the numbers are off.
        if (l1 * l1 + l2 * l2 > 0.8) return null
        return l1 to l2
    }

    /** Turns the outline into the disc's position, after taking the lean out. */
    private fun unsquash(outer: Conic, l1: Double, l2: Double, fallback: Disc): Disc? {
        // A picture point p is the flat point q leaned: p = q / (1 - l1·qx - l2·qy).
        // So q = p / (1 + l1·px + l2·py), a 3×3 change of the outline's equation.
        val h = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, -l1, -l2, 1.0)
        val ht = doubleArrayOf(h[0], h[3], h[6], h[1], h[4], h[7], h[2], h[5], h[8])
        val c = Conic.times(ht, Conic.times(outer.m, h))
        val det = c[0] * c[4] - c[1] * c[3]
        if (det <= 0) return null
        // Centre of the unsquashed oval, and its shape.
        val qx = -(c[4] * c[2] - c[1] * c[5]) / det
        val qy = -(-c[3] * c[2] + c[0] * c[5]) / det
        val k = c[0] * qx * qx + 2 * c[1] * qx * qy + c[4] * qy * qy - c[8]
        if (k <= 0) return null
        // The oval is (q - centre)ᵀ·S·(q - centre) = 1 with S = top-left / k.
        val s11 = c[0] / k
        val s12 = c[1] / k
        val s22 = c[4] / k
        val sd = s11 * s22 - s12 * s12
        if (sd <= 0) return null
        // Shape of the circle → oval stretch: lower-triangular L with L·Lᵀ = S⁻¹.
        val i11 = s22 / sd
        val i12 = -s12 / sd
        val i22 = s11 / sd
        val q11 = sqrt(i11)
        val q21 = i12 / q11
        val q22 = sqrt(maxOf(1e-12, i22 - q21 * q21))
        return Disc(fallback.mx, fallback.my, fallback.size, l1, l2, qx, qy, q11, q21, q22)
    }

    /** A conic (circle, oval…) as a 3×3 matrix: points p with (x, y, 1)·m·(x, y, 1) = 0. */
    private class Conic(val m: DoubleArray) {
        fun inverse(): DoubleArray? {
            val (a, b, c) = Triple(m[0], m[1], m[2])
            val (d, e, f) = Triple(m[3], m[4], m[5])
            val (g, h, i) = Triple(m[6], m[7], m[8])
            val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
            if (abs(det) < 1e-15) return null
            return doubleArrayOf(
                (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
                (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
                (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det,
            )
        }

        companion object {
            fun times(x: DoubleArray, y: DoubleArray) = DoubleArray(9) { k ->
                val r = k / 3
                val col = k % 3
                x[r * 3] * y[col] + x[r * 3 + 1] * y[3 + col] + x[r * 3 + 2] * y[6 + col]
            }

            /** The edge of a filled oval with this centre and spread (from its pixels). */
            fun ofFilledOval(cx: Double, cy: Double, sxx: Double, syy: Double, sxy: Double): Conic {
                // A filled oval's spread is a quarter of its shape: edge is (p-c)ᵀ·(4·spread)⁻¹·(p-c) = 1.
                val det = 16 * (sxx * syy - sxy * sxy)
                val a = 4 * syy / det
                val b = -4 * sxy / det
                val d = 4 * sxx / det
                val e = -(a * cx + b * cy)
                val f = -(b * cx + d * cy)
                val g = a * cx * cx + 2 * b * cx * cy + d * cy * cy - 1
                return Conic(doubleArrayOf(a, b, e, b, d, f, e, f, g))
            }
        }
    }

    private fun readDisc(img: IntArray, w: Int, h: Int, d: Disc): String? {
        val point = DoubleArray(2)
        fun sample(ux: Double, uy: Double): Double {
            d.toPicture(ux, uy, point)
            val px = point[0]
            val py = point[1]
            if (px < 0 || py < 0 || px >= w - 1 || py >= h - 1) return 0.0
            val x = px.toInt()
            val y = py.toInt()
            val fx = px - x
            val fy = py - y
            val i = y * w + x
            return img[i] * (1 - fx) * (1 - fy) + img[i + 1] * fx * (1 - fy) +
                img[i + w] * (1 - fx) * fy + img[i + w + 1] * fx * fy
        }
        val bits = BooleanArray(RingCode.BITS)
        val values = Array(RingCode.RINGS) { DoubleArray(ROUND) }
        for (scale in doubleArrayOf(1.0, 0.97, 1.03)) {
            for (ring in 0 until RingCode.RINGS) {
                val r = RingCode.RING_RADII[ring] * scale
                for (k in 0 until ROUND) {
                    val angle = k * 2 * PI / ROUND
                    val sx = sin(angle)
                    val sy = -cos(angle)
                    var sum = 0.0
                    for (dr in doubleArrayOf(-0.012, 0.0, 0.012)) sum += sample(sx * (r + dr), sy * (r + dr))
                    values[ring][k] = sum / 3
                }
            }
            val sorted = values.flatMap { it.asList() }.sorted()
            val dark = sorted[sorted.size * 15 / 100]
            val light = sorted[sorted.size * 85 / 100]
            if (light - dark < 25) continue
            val split = (dark + light) / 2
            // The picture may be mirrored (a selfie camera), so try both ways round.
            for (mirrored in booleanArrayOf(false, true)) {
                for (turn in 0 until ROUND step 3) {
                    for (ring in 0 until RingCode.RINGS) for (slot in 0 until RingCode.SLOTS) {
                        val along = slot * STEPS_PER_POSITION + STEPS_PER_POSITION * 3 / 2
                        val k = Math.floorMod(if (mirrored) turn - along else turn + along, ROUND)
                        bits[ring * RingCode.SLOTS + slot] = values[ring][k] > split
                    }
                    RingCode.decode(bits)?.let { return it }
                }
            }
        }
        return null
    }
}
