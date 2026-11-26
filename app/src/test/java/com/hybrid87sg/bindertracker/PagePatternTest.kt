package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.ui.theme.AppColourMath
import com.hybrid87sg.bindertracker.ui.theme.BallColour
import com.hybrid87sg.bindertracker.ui.theme.BallLayout
import com.hybrid87sg.bindertracker.ui.theme.BallStyle
import com.hybrid87sg.bindertracker.ui.theme.ColourChoice
import com.hybrid87sg.bindertracker.ui.theme.PagePattern
import com.hybrid87sg.bindertracker.ui.theme.PagePatternMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagePatternTest {
    @Test
    fun savedTextComesBackTheSame() {
        val p = PagePattern(
            style = BallStyle.Outline,
            ball = BallColour.Pick(ColourChoice(214f, 0.8f, 0.3f)),
            page = ColourChoice(222f, 0.6f, 0.7f),
            size = 33, gap = 9, tilt = -45, mixTilt = true,
            layout = BallLayout.Scattered, strength = 0.5f, drift = true,
        )
        assertEquals(p, PagePatternMath.decode(PagePatternMath.encode(p)))
        assertEquals(BallColour.RedWhite, PagePatternMath.decode(PagePatternMath.encode(PagePattern(ball = BallColour.RedWhite))).ball)
        assertEquals(BallColour.App, PagePatternMath.decode(PagePatternMath.encode(PagePattern(ball = BallColour.App))).ball)
        // Nothing saved, or rubbish: the default look.
        assertEquals(PagePattern(), PagePatternMath.decode(null))
        assertEquals(PagePattern(), PagePatternMath.decode("rubbish"))
        // Out of range numbers are kept inside the sliders' range.
        assertEquals(PagePatternMath.SIZE_MAX, PagePatternMath.decode("Badge|x|x|999").size)
    }

    @Test
    fun ballsStayLightAndPagesStayDark() {
        for (h in 0 until 360 step 15) for (s in listOf(0f, 0.5f, 1f)) {
            val ball = PagePatternMath.ballColour(ColourChoice(h.toFloat(), s))
            val page = PagePatternMath.pageColour(ColourChoice(h.toFloat(), s))
            assertTrue("ball $h $s", AppColourMath.luminance(ball) > 0.07)
            assertTrue("page $h $s", AppColourMath.luminance(page) < 0.1)
            // Every ball colour stands out from the black page, and white balls from every page colour.
            assertTrue("ball on black $h $s", AppColourMath.contrast(ball, PagePatternMath.pageColour(ColourChoice(0f, 0f))) > 3.0)
            assertTrue("white on page $h $s", AppColourMath.contrast(PagePatternMath.ballColour(ColourChoice(0f, 0f)), page) > 3.0)
        }
        // Plain = white balls on a black page.
        assertTrue(AppColourMath.luminance(PagePatternMath.ballColour(ColourChoice(0f, 0f))) > 0.75)
        assertTrue(AppColourMath.luminance(PagePatternMath.pageColour(ColourChoice(0f, 0f))) < 0.01)
    }

    @Test
    fun namesFollowTheGemsAndTheBrightness() {
        assertEquals("White", PagePatternMath.nameFor(BallColour.Pick(ColourChoice(0f, 0f)), page = false))
        assertEquals("Black", PagePatternMath.nameFor(BallColour.Pick(ColourChoice(0f, 0f)), page = true))
        assertEquals("Navy", PagePatternMath.nameFor(BallColour.Pick(ColourChoice(222f, 0.6f)), page = true))
        assertEquals("Dark ocean", PagePatternMath.nameFor(BallColour.Pick(ColourChoice(214f, 0.8f, 0.2f)), page = false))
    }

    @Test
    fun ballsCoverThePage() {
        for (layout in BallLayout.entries) {
            val spots = PagePatternMath.spots(PagePattern(layout = layout), 600f, 900f, 40f, 30f)
            assertTrue("$layout has balls", spots.size > 20)
            // Some balls in every corner area, so there are no bare patches.
            assertTrue(spots.any { it.x < 150f && it.y < 150f })
            assertTrue(spots.any { it.x > 450f && it.y > 750f })
        }
        assertTrue(PagePatternMath.spots(PagePattern(), 0f, 0f, 40f, 30f).isEmpty())
        // Mixed tilt gives every ball its own angle, the same each time.
        val a = PagePatternMath.spots(PagePattern(mixTilt = true), 300f, 300f, 40f, 30f)
        assertEquals(a, PagePatternMath.spots(PagePattern(mixTilt = true), 300f, 300f, 40f, 30f))
        assertTrue(a.map { it.tilt }.distinct().size > 3)
    }
}
