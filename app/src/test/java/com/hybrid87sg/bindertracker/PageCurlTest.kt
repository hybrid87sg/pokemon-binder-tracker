package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.ui.components.PageCurl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** Checks the page-turn maths: flat, rolled, then lying face down. */
class PageCurlTest {
    private val width = 300f
    private val radius = 20f

    @Test fun notTurnedIsFlat() {
        val fold = PageCurl.fold(0f, width, radius)
        assertEquals(width, fold, 0.001f)
        val (x, z) = PageCurl.position(150f, fold, radius)
        assertEquals(150f, x, 0.001f)
        assertEquals(0f, z, 0.001f)
    }

    @Test fun fullyTurnedLiesPastTheRings() {
        val fold = PageCurl.fold(1f, width, radius)
        assertEquals(-PI.toFloat() * radius, fold, 0.001f)
        // Every point of the sheet is now face down, left of the rings.
        listOf(0f, 100f, width).forEach { s ->
            val (x, z) = PageCurl.position(s, fold, radius)
            assertTrue("x=$x", x <= 0.001f)
            assertEquals(2f * radius, z, 0.001f)
            assertEquals(PI.toFloat(), PageCurl.angle(s, fold, radius), 0.001f)
        }
    }

    @Test fun rollIsAHalfCircle() {
        val fold = 100f
        // Halfway round the roll the sheet stands straight up, one radius out.
        val (x, z) = PageCurl.position(fold + PI.toFloat() * radius / 2f, fold, radius)
        assertEquals(fold + radius, x, 0.01f)
        assertEquals(radius, z, 0.01f)
    }

    @Test fun cutsCoverTheWholeSheetInOrder() {
        val cuts = PageCurl.cuts(width, 120f, radius)
        assertEquals(0f, cuts.first(), 0f)
        assertEquals(width, cuts.last(), 0f)
        assertTrue(cuts.zipWithNext().all { (a, b) -> b > a })
    }
}
