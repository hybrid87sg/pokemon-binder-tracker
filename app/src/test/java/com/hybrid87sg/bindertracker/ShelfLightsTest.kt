package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.LedColour
import com.hybrid87sg.bindertracker.data.LedStrips
import com.hybrid87sg.bindertracker.data.ShelfLook
import com.hybrid87sg.bindertracker.ui.three.BinderModel
import com.hybrid87sg.bindertracker.ui.three.Led
import com.hybrid87sg.bindertracker.ui.three.ShelfLayout
import com.hybrid87sg.bindertracker.ui.three.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the wooden storage shelf layout and the LED colour maths. */
class ShelfLightsTest {
    @Test
    fun everyBinderStandsInsideItsCubby() {
        for (count in listOf(1, 4, 5, 9)) for (angle in ShelfLook.Angles) for (right in listOf(true, false)) {
            val l = ShelfLayout(count, angle, right)
            assertEquals((count + 3) / 4, l.rows)
            assertEquals(l.rows, l.bays.size)
            assertEquals(count, l.labels.size)
            val (lo, hi) = BinderModel.bounds(l.dir)
            for (s in l.spots) {
                val bay = l.bays[s.row]
                val corners = listOf(lo.x, hi.x).flatMap { x -> listOf(lo.z, hi.z).map { z -> Vec3(x, 0f, z).rotateY(l.turn) + s.position } }
                assertTrue(corners.all { it.x > -bay.width / 2f && it.x < bay.width / 2f })
                assertTrue(corners.all { it.z < 0f && it.z > -bay.depth })
                // Standing on the cubby's floor, with room above for the back strip.
                assertEquals(bay.floor, s.position.y, 0.0001f)
                assertTrue(bay.top - bay.floor > BinderModel.HEIGHT + 0.3f)
            }
        }
    }

    @Test
    fun namePlatesSitUnderTheirBinders() {
        val l = ShelfLayout(6, 25, coversOnRight = true)
        for (lab in l.labels) {
            val bay = l.bays[lab.index / 4]
            assertTrue(lab.centre.y < bay.floor)
            assertTrue(lab.centre.z > 0f) // on the front of the board
        }
    }

    @Test
    fun theShelfIsCentred() {
        val l = ShelfLayout(7, 0, coversOnRight = true)
        assertEquals(-l.max.x, l.min.x, 0.001f)
        assertTrue(l.min.y < l.bays.last().floor)
    }

    @Test
    fun ledColoursGoFromWhiteToVivid() {
        val white = Led.argb(LedColour(214, 0))
        val vivid = Led.argb(LedColour(214, 100))
        fun channels(c: Int) = listOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        // Strength 0 is nearly white: all channels close together and bright.
        assertTrue(channels(white).all { it > 220 })
        // Vivid blue: blue far above red.
        assertTrue(channels(vivid)[2] - channels(vivid)[0] > 150)
        assertEquals("White", Led.strengthWord(0))
        assertEquals("Vivid", Led.strengthWord(100))
    }

    @Test
    fun blendedStripGoesThroughEachColour() {
        val cols = listOf(LedColour(0, 100), LedColour(120, 100))
        assertEquals(Led.argb(cols[0]), Led.at(cols, 0f))
        assertEquals(Led.argb(cols[1]), Led.at(cols, 1f))
        assertEquals(Led.argb(cols[0]), Led.at(listOf(cols[0]), 0.7f))
    }

    @Test
    fun ledColoursAreSavedAndReadBack() {
        val holo = ShelfLook.LedPresets.first { it.name == "Holo rainbow" }.colours
        assertEquals(holo, ShelfLook.ledFromText(ShelfLook.ledToText(holo)))
        assertNull(ShelfLook.ledFromText(""))
        assertNull(ShelfLook.ledFromText("rubbish"))
        // Never more than 4, and numbers kept in range.
        val many = ShelfLook.ledFromText("10:5;20:5;30:5;40:5;50:5;400:150")
        assertEquals(4, many?.size)
        assertEquals(LedColour(40, 0), ShelfLook.ledFromText("400:-3")?.first())
    }

    @Test
    fun stripsOffMeansNoLight() {
        assertFalse(ShelfLook(strips = LedStrips.Off).lit)
        assertFalse(ShelfLook(lightsOn = false).lit)
        assertTrue(ShelfLook().lit)
        assertTrue(LedStrips.Both.back && LedStrips.Both.front)
        assertFalse(LedStrips.Front.back)
    }
}
