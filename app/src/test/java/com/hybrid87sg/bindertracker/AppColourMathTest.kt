package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.ui.theme.AppColourMath
import com.hybrid87sg.bindertracker.ui.theme.ColourChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppColourMathTest {
    private val white = 0xFFFFFFFF.toInt()

    /** Every hue, at soft, middle and vivid strength. */
    private fun everyChoice() = (0 until 360 step 5).flatMap { h -> listOf(0f, 0.5f, 1f).map { ColourChoice(h.toFloat(), it) } }

    @Test
    fun textIsReadableForEveryColourInBothModes() {
        for (dark in listOf(false, true)) {
            for (c in everyChoice()) {
                val b = AppColourMath.build(listOf(c), dark)
                val where = "hue ${c.hue}, strength ${c.strength}, dark $dark"
                assertTrue("white on button: $where", AppColourMath.contrast(white, b.accent) >= 4.5)
                assertTrue("ink on paper: $where", AppColourMath.contrast(b.ink, b.paper) >= 4.5)
                assertTrue("ink on surface: $where", AppColourMath.contrast(b.ink, b.surface) >= 4.5)
                assertTrue("muted on surface: $where", AppColourMath.contrast(b.muted, b.surface) >= 4.5)
                for (stop in b.background) {
                    assertTrue("ink on background: $where", AppColourMath.contrast(b.ink, stop) >= 4.5)
                    assertTrue("muted on background: $where", AppColourMath.contrast(b.muted, stop) >= 4.5)
                    assertTrue("coloured words on background: $where", AppColourMath.contrast(b.accentText, stop) >= 4.5)
                }
            }
        }
    }

    @Test
    fun extraColoursStayReadable() {
        for (dark in listOf(false, true)) {
            for (h in 0 until 360 step 30) {
                val picks = listOf(ColourChoice(h.toFloat(), 1f), ColourChoice((h + 90f) % 360f, 1f), ColourChoice((h + 180f) % 360f, 0f), ColourChoice((h + 270f) % 360f, 0.5f))
                val b = AppColourMath.build(picks, dark)
                assertEquals(AppColourMath.BACKGROUND_STOPS, b.background.size)
                for (stop in b.background) {
                    assertTrue("muted, hue $h dark $dark", AppColourMath.contrast(b.muted, stop) >= 4.5)
                    assertTrue("ink, hue $h dark $dark", AppColourMath.contrast(b.ink, stop) >= 4.5)
                }
            }
        }
    }

    @Test
    fun savedTextComesBackTheSame() {
        val picks = listOf(ColourChoice(214f, 0.65f), ColourChoice(286f, 0.3f))
        assertEquals(picks, AppColourMath.decode(AppColourMath.encode(picks)))
        assertNull(AppColourMath.decode(null))
        assertNull(AppColourMath.decode("rubbish"))
        // Never more than four colours, even if the saved text has more.
        assertEquals(4, AppColourMath.decode("1:0,2:0,3:0,4:0,5:0")!!.size)
    }

    @Test
    fun namesAndWords() {
        assertEquals("Holo Violet", AppColourMath.nameFor(248f))
        assertEquals("Ocean", AppColourMath.nameFor(215f))
        assertEquals("Soft", AppColourMath.strengthWord(0f))
        assertEquals("Vivid", AppColourMath.strengthWord(1f))
        assertEquals("Off", AppColourMath.glitterWord(0f))
        assertEquals("Full holo", AppColourMath.glitterWord(1f))
        assertTrue(AppColourMath.isClassic(listOf(AppColourMath.CLASSIC)))
    }

    @Test
    fun resampleAlwaysGivesFive() {
        assertEquals(5, AppColourMath.resample(listOf(1, 2, 3)).size)
        assertEquals(5, AppColourMath.resample(listOf(0xFF000000.toInt())).size)
    }
}
