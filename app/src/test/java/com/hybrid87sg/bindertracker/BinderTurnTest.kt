package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.ui.components.BinderTurn
import com.hybrid87sg.bindertracker.ui.components.ShelfFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the turned-binder maths: the cover meets the spine, sides mirror, things fit. */
class BinderTurnTest {
    private val w = 64f
    private val h = 280f

    @Test
    fun straightBinderIsUnchanged() {
        val t = BinderTurn.of(w, h, 0f, coverOnRight = true)
        assertEquals(0f, t.spineX(0f), 0.01f)
        assertEquals(w, t.spineX(w), 0.01f)
        assertEquals(w, t.spineWidth, 0.01f)
    }

    @Test
    fun coverMeetsTheSpineAtTheHinge() {
        for (right in listOf(true, false)) {
            val t = BinderTurn.of(w, h, 25f, right)
            val hinge = if (right) t.spineX(w) else t.spineX(0f)
            assertEquals(hinge, t.coverX(0f), 0.01f)
            // Same height there too.
            assertEquals(t.spineGrow(if (right) w else 0f), t.coverGrow(0f), 0.0001f)
        }
    }

    @Test
    fun coverShowsOnTheChosenSide() {
        val right = BinderTurn.of(w, h, 30f, coverOnRight = true)
        assertTrue(right.coverX(right.depth) > right.spineRight)
        val left = BinderTurn.of(w, h, 30f, coverOnRight = false)
        assertTrue(left.coverX(left.depth) < left.spineLeft)
        // Mirror images of each other.
        assertEquals(right.spineWidth, left.spineWidth, 0.01f)
        assertEquals(right.coverReach, left.coverReach, 0.01f)
    }

    @Test
    fun hingeEdgeLooksTallerThanTheFarEdge() {
        val t = BinderTurn.of(w, h, 30f, coverOnRight = true)
        // The hinge comes towards you, the cover's far edge goes away.
        assertTrue(t.coverGrow(0f) > 1f)
        assertTrue(t.coverGrow(t.depth) < 1f)
        // The bottom stays on the shelf.
        assertEquals(h, t.y(h, 1.3f), 0.001f)
    }

    @Test
    fun coverGrowsWithTheAngle() {
        var last = 0f
        for (a in listOf(15f, 20f, 25f, 30f, 35f)) {
            val reach = BinderTurn.of(w, h, a, true).coverReach
            assertTrue(reach > last)
            last = reach
        }
    }

    @Test
    fun fourBindersAndTheLastCoverFitTheShelf() {
        val available = 300f
        for (a in listOf(0f, 15f, 25f, 35f)) {
            val fit = ShelfFit.of(a, true, w, h, 8f, 40f, available, perRow = 4)
            val used = 4 * fit.spineWidth + 3 * fit.gap + fit.coverReach
            assertTrue("angle $a uses $used", used <= available + 0.5f)
            assertTrue(fit.scale in 0.3f..1f)
        }
    }
}
