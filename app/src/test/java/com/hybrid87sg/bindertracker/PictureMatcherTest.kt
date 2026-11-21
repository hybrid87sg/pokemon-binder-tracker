package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.scan.PictureMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Checks the picture fingerprints with made-up grey pictures, so no camera
 * or internet is needed.
 */
class PictureMatcherTest {
    private val n = PictureMatcher.SIZE

    // A made-up "card": a few light and dark blobs placed by [seed].
    private fun picture(seed: Int): IntArray {
        val r = Random(seed)
        val blobs = List(5) { Triple(r.nextInt(n), r.nextInt(n), r.nextInt(40, 255)) }
        return IntArray(n * n) { i ->
            val x = i % n
            val y = i / n
            blobs.maxOf { (bx, by, shade) ->
                val d = (x - bx) * (x - bx) + (y - by) * (y - by)
                if (d < 60) shade else 20
            }
        }
    }

    // The same picture as a phone photo might see it: darker and a bit noisy.
    private fun photoOf(gray: IntArray, seed: Int): IntArray {
        val r = Random(seed)
        return IntArray(gray.size) { i -> (gray[i] * 0.8 + 15 + r.nextInt(-12, 13)).toInt().coerceIn(0, 255) }
    }

    @Test fun samePictureIsClose_differentIsFar() {
        val card = picture(1)
        val same = PictureMatcher.distance(PictureMatcher.fingerprint(card), PictureMatcher.fingerprint(photoOf(card, 7)))
        val other = PictureMatcher.distance(PictureMatcher.fingerprint(card), PictureMatcher.fingerprint(picture(2)))
        assertTrue("same card distance $same", same <= 10)
        assertTrue("different card distance $other", other >= 20)
    }

    @Test fun picksTheMatchingCard() {
        val official = (1..20).map { "card$it" to PictureMatcher.fingerprint(picture(it)) }
        val photo = PictureMatcher.fingerprint(photoOf(picture(13), 99))
        assertEquals("card13", PictureMatcher.best(photo, official))
    }

    @Test fun refusesWhenNothingIsClose() {
        val official = (1..5).map { "card$it" to PictureMatcher.fingerprint(picture(it)) }
        val photo = PictureMatcher.fingerprint(picture(500))
        assertNull(PictureMatcher.best(photo, official))
    }

    @Test fun refusesWhenTwoAreTooCloseToCall() {
        val print = PictureMatcher.fingerprint(picture(3))
        assertNull(PictureMatcher.best(print, listOf("a" to print, "b" to print)))
    }
}
