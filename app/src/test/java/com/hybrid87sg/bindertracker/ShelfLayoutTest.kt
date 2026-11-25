package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.ShelfView
import com.hybrid87sg.bindertracker.ui.three.BinderModel
import com.hybrid87sg.bindertracker.ui.three.ShelfLayout
import com.hybrid87sg.bindertracker.ui.three.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the simple shelf: Spine side (5 a row) and Cover side (2 or 4 columns). */
class ShelfLayoutTest {
    /** The left and right edges of each binder, as it stands on the shelf. */
    private fun edges(l: ShelfLayout): List<Pair<Float, Float>> {
        val (lo, hi) = BinderModel.bounds(l.dir)
        return l.spots.map { s ->
            val xs = listOf(lo.x, hi.x).flatMap { x -> listOf(lo.z, hi.z).map { z -> (Vec3(x, 0f, z).rotateY(l.turn) + s.position).x } }
            xs.min() to xs.max()
        }
    }

    @Test
    fun bindersNeverTouchAndStayOnTheirLedge() {
        for (view in ShelfView.entries) for (cols in listOf(2, 4)) for (count in listOf(1, 2, 5, 9)) {
            val l = ShelfLayout(count, view, cols)
            assertEquals(count, l.spots.size)
            val e = edges(l)
            for (row in 0 until l.rows) {
                val inRow = e.filterIndexed { i, _ -> l.spots[i].row == row }
                assertTrue(inRow.zipWithNext().all { (a, b) -> b.first > a.second + 0.1f })
                val ledge = l.ledges[row]
                assertTrue(inRow.all { it.first > ledge.centre.x - ledge.size.x / 2f && it.second < ledge.centre.x + ledge.size.x / 2f })
            }
            // Standing on the ledge's top.
            for (s in l.spots) assertEquals(l.ledges[s.row].centre.y + l.ledges[s.row].size.y / 2f, s.position.y, 0.0001f)
        }
    }

    @Test
    fun spineGapsAreEqualOnEveryRow() {
        val l = ShelfLayout(10, ShelfView.Spine)
        assertEquals(2, l.rows)
        val e = edges(l)
        val gaps = (0 until 2).flatMap { r -> e.subList(r * 5, r * 5 + 5).zipWithNext().map { (a, b) -> b.first - a.second } }
        assertTrue(gaps.all { kotlin.math.abs(it - gaps[0]) < 0.0001f })
        // A short last row starts at the left, lined up with the row above.
        val short = ShelfLayout(7, ShelfView.Spine)
        assertEquals(short.spots[0].position.x, short.spots[5].position.x, 0.0001f)
    }

    @Test
    fun captionsSitUnderTheirCovers() {
        val l = ShelfLayout(6, ShelfView.Cover, columns = 2)
        assertEquals(6, l.labels.size)
        val e = edges(l)
        for (lab in l.labels) {
            val s = l.spots[lab.index]
            assertTrue(lab.centre.y + lab.height / 2f < s.position.y)
            assertEquals((e[lab.index].first + e[lab.index].second) / 2f, lab.centre.x, 0.0001f)
            assertTrue(lab.centre.y - lab.height / 2f > l.min.y)
        }
    }

    @Test
    fun theShelfIsCentredAndHoldsEverything() {
        for (view in ShelfView.entries) {
            val l = ShelfLayout(7, view, 4)
            assertEquals(-l.max.x, l.min.x, 0.001f)
            assertTrue(l.max.y >= BinderModel.HEIGHT)
            assertTrue(l.min.y < l.ledges.last().centre.y)
        }
    }
}
