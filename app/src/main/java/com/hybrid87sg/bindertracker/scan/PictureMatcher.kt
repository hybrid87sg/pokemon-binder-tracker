package com.hybrid87sg.bindertracker.scan

import kotlin.math.PI
import kotlin.math.cos

/**
 * Compares card pictures by their "fingerprint".
 *
 * A fingerprint (a "perceptual hash") squeezes a picture down to 64 yes/no
 * answers about its overall light and dark shapes. Two photos of the same
 * card give nearly the same answers even if one is a bit blurry, darker or
 * shot through a sleeve; different cards give very different answers.
 * Counting how many answers differ gives a "distance": small = same card.
 *
 * This part is plain Kotlin (no camera, no internet), so it can be tested
 * on a computer. Turning a real picture into the grey squares it needs is
 * done in CardPictures.
 */
object PictureMatcher {

    /** Pictures are shrunk to SIZE x SIZE grey squares before fingerprinting. */
    const val SIZE = 32
    private const val KEEP = 8 // keep the 8x8 coarsest patterns = 64 answers

    // Pre-worked cosine table for the pattern maths (a "DCT").
    private val cosTable = Array(KEEP) { u -> DoubleArray(SIZE) { x -> cos((2 * x + 1) * u * PI / (2 * SIZE)) } }

    /**
     * Fingerprint of a picture given as SIZE*SIZE grey values (0 = black,
     * 255 = white), row by row.
     */
    fun fingerprint(gray: IntArray): Long {
        require(gray.size == SIZE * SIZE) { "Expected ${SIZE * SIZE} grey values" }
        // Coarse patterns along each row, then down each column.
        val rows = Array(SIZE) { y -> DoubleArray(KEEP) { u -> (0 until SIZE).sumOf { x -> gray[y * SIZE + x] * cosTable[u][x] } } }
        val coeffs = DoubleArray(KEEP * KEEP) { i ->
            val v = i / KEEP
            val u = i % KEEP
            (0 until SIZE).sumOf { y -> rows[y][u] * cosTable[v][y] }
        }
        // Each answer: is this pattern stronger than the typical one?
        // (The very first one is just overall brightness, so it's left out of the typical value.)
        val median = coeffs.drop(1).sorted().let { (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
        var bits = 0L
        for (i in coeffs.indices) if (coeffs[i] > median) bits = bits or (1L shl i)
        return bits
    }

    /** How many of the 64 answers differ: 0 = identical, about 32 = unrelated. */
    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    // How close a picture must be to count as a match. Photos through a
    // sleeve are never pixel-perfect, so this allows some difference, but
    // stays well under the ~32 that two unrelated pictures score.
    const val MAX_DISTANCE = 20

    // The best match must also beat the runner-up by this much, so two
    // similar-looking prints of the same Pokémon don't get mixed up.
    const val MIN_LEAD = 4

    /**
     * Picks the candidate whose fingerprint is clearly closest to [target],
     * or null when none is close enough or two are too close to call.
     */
    fun <T> best(target: Long, candidates: List<Pair<T, Long>>): T? {
        if (candidates.isEmpty()) return null
        val ranked = candidates.map { (item, print) -> item to distance(target, print) }.sortedBy { it.second }
        val (winner, d) = ranked.first()
        if (d > MAX_DISTANCE) return null
        val runnerUp = ranked.getOrNull(1)?.second ?: return winner
        return if (runnerUp - d >= MIN_LEAD) winner else null
    }
}
