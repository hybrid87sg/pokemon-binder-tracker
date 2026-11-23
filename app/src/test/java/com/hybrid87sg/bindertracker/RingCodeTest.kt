package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.scan.RingCodeReader
import com.hybrid87sg.bindertracker.sync.InviteCodes
import com.hybrid87sg.bindertracker.sync.RingCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The binder ring code: made and read back, also with damage, and read from
 * pretend camera pictures (turned, tilted, blurry, grainy and with glare).
 */
class RingCodeTest {
    @Test
    fun codesGoRoundTrip() {
        val random = Random(1)
        repeat(300) {
            val code = InviteCodes.newCode(random)
            assertEquals(code, RingCode.decode(RingCode.encode(code)))
        }
    }

    @Test
    fun repairsUpToSixDamagedPieces() {
        val random = Random(2)
        repeat(200) {
            val code = InviteCodes.newCode(random)
            val bits = RingCode.encode(code)
            // Scribble over 6 of the 20 six-slot pieces.
            (0 until 20).shuffled(random).take(6).forEach { piece ->
                for (b in 0 until 6) if (random.nextBoolean()) bits[piece * 6 + b] = !bits[piece * 6 + b]
                bits[piece * 6] = !bits[piece * 6]
            }
            assertEquals(code, RingCode.decode(bits))
        }
    }

    @Test
    fun randomSlotsAreNotACode() {
        val random = Random(3)
        repeat(2000) {
            assertNull(RingCode.decode(BooleanArray(RingCode.BITS) { random.nextBoolean() }))
        }
    }

    @Test
    fun readsPretendCameraPictures() {
        val random = Random(4)
        var read = 0
        val tries = 40
        repeat(tries) { i ->
            val code = InviteCodes.newCode(random)
            val picture = PretendCamera(random).picture(
                RingCode.encode(code),
                turn = random.nextDouble() * 2 * PI,
                tiltX = (random.nextDouble() - 0.5) * 0.9,
                tiltY = (random.nextDouble() - 0.5) * 0.9,
                radius = 110.0 + random.nextDouble() * 250,
                darkRoom = i % 3 == 0,
            )
            if (RingCodeReader.read(picture, PretendCamera.W, PretendCamera.H) == code) read++
        }
        assertTrue("Read $read of $tries", read >= tries * 9 / 10)
    }

    @Test
    fun emptyPicturesHaveNoCode() {
        val random = Random(5)
        repeat(10) {
            val noise = IntArray(PretendCamera.W * PretendCamera.H) { 80 + random.nextInt(120) }
            assertNull(RingCodeReader.read(noise, PretendCamera.W, PretendCamera.H))
        }
    }
}

/** Draws a ring code the way the app does, then photographs it at an angle. */
private class PretendCamera(val random: Random) {
    companion object {
        const val W = 1280
        const val H = 960
    }

    private val disc = 40 + 0.0
    private val track = disc + (255 - disc) * 0.16

    /** The brightness of the flat ring code at (x, y), where the disc's radius is 1. */
    private fun flat(bits: BooleanArray, x: Double, y: Double, background: Double): Double {
        val r = sqrt(x * x + y * y)
        if (r > RingCode.HALO_RADIUS) return background
        if (r > 1) return 250.0
        if (y in -0.97..-0.90 && abs(x) <= 0.06 * (-(y + 0.90) / 0.07)) return 250.0
        if (r < RingCode.CENTRE_RADIUS) {
            // The logo: a white circle with a dark and a red part.
            return if (r < 0.1) 30.0 else if (r < 0.38 && y < 0) 110.0 else 245.0
        }
        val angle = (atan2(x, -y) + 2 * PI) % (2 * PI)
        for (ring in 0 until RingCode.RINGS) {
            if (abs(r - RingCode.RING_RADII[ring]) < RingCode.RING_WIDTH / 2) {
                val slot = floor(angle / (2 * PI / RingCode.POSITIONS)).toInt() - 1
                return if (slot in 0 until RingCode.SLOTS && bits[ring * RingCode.SLOTS + slot]) 250.0 else track
            }
        }
        return disc
    }

    fun picture(bits: BooleanArray, turn: Double, tiltX: Double, tiltY: Double, radius: Double, darkRoom: Boolean): IntArray {
        // The code lies flat, turned and tilted, in front of a camera.
        val focal = 1000.0
        val distance = focal / radius
        val (ct, st) = cos(turn) to sin(turn)
        val (cx, sx) = cos(tiltX) to sin(tiltX)
        val (cy, sy) = cos(tiltY) to sin(tiltY)
        // Rotation = tiltY · tiltX · turn; only its first two columns matter for a flat code.
        fun rot(x: Double, y: Double): DoubleArray {
            val x1 = x * ct - y * st
            val y1 = x * st + y * ct
            val y2 = y1 * cx
            val z2 = y1 * sx
            return doubleArrayOf(x1 * cy + z2 * sy, y2, -x1 * sy + z2 * cy)
        }
        val r1 = rot(1.0, 0.0)
        val r2 = rot(0.0, 1.0)
        val shiftX = (random.nextDouble() - 0.5) * 0.3 * W
        val shiftY = (random.nextDouble() - 0.5) * 0.3 * H
        val m = arrayOf(
            doubleArrayOf(focal * r1[0] + (W / 2.0 + shiftX) * r1[2], focal * r2[0] + (W / 2.0 + shiftX) * r2[2], (W / 2.0 + shiftX) * distance),
            doubleArrayOf(focal * r1[1] + (H / 2.0 + shiftY) * r1[2], focal * r2[1] + (H / 2.0 + shiftY) * r2[2], (H / 2.0 + shiftY) * distance),
            doubleArrayOf(r1[2], r2[2], distance),
        )
        val inv = invert(m)
        val background = if (darkRoom) 35.0 else 170.0
        val glareX = random.nextDouble() * W
        val glareY = random.nextDouble() * H
        val out = IntArray(W * H)
        for (py in 0 until H) for (px in 0 until W) {
            val u = inv[0][0] * px + inv[0][1] * py + inv[0][2]
            val v = inv[1][0] * px + inv[1][1] * py + inv[1][2]
            val q = inv[2][0] * px + inv[2][1] * py + inv[2][2]
            val shade = if (abs(q) < 1e-12) background else flat(bits, u / q, v / q, background + (px % 97) * 0.3)
            val glare = 60 * kotlin.math.exp(-((px - glareX) * (px - glareX) + (py - glareY) * (py - glareY)) / 8000.0)
            out[py * W + px] = (shade + glare + random.nextGaussian() * 12).toInt().coerceIn(0, 255)
        }
        return blur(out)
    }

    private fun blur(img: IntArray): IntArray {
        val out = img.copyOf()
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            var s = 0
            for (dy in -1..1) for (dx in -1..1) s += img[(y + dy) * W + x + dx]
            out[y * W + x] = s / 9
        }
        return out
    }

    private fun invert(m: Array<DoubleArray>): Array<DoubleArray> {
        val (a, b, c) = m[0].let { Triple(it[0], it[1], it[2]) }
        val (d, e, f) = m[1].let { Triple(it[0], it[1], it[2]) }
        val (g, h, i) = m[2].let { Triple(it[0], it[1], it[2]) }
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        return arrayOf(
            doubleArrayOf((e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det),
            doubleArrayOf((f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det),
            doubleArrayOf((d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det),
        )
    }
}
