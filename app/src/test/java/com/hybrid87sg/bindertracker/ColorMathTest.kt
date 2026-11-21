package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.ui.components.ColorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorMathTest {
    @Test fun readsColourCodes() {
        assertEquals(0xFF8C2F2F.toInt(), ColorMath.parseHex("#8C2F2F"))
        assertEquals(0xFF8C2F2F.toInt(), ColorMath.parseHex(" 8c2f2f "))
        assertEquals(0xFFFF0000.toInt(), ColorMath.parseHex("#f00"))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("#GGGGGG"))
        assertNull(ColorMath.parseHex(""))
    }

    @Test fun writesColourCodes() {
        assertEquals("#8C2F2F", ColorMath.toHex(0xFF8C2F2F.toInt()))
        assertEquals("#000000", ColorMath.toHex(0xFF000000.toInt()))
    }

    @Test fun rainbowEnds() {
        assertEquals("#FF0000", ColorMath.toHex(ColorMath.hsvToArgb(0f, 1f, 1f)))
        assertEquals("#00FF00", ColorMath.toHex(ColorMath.hsvToArgb(120f, 1f, 1f)))
        assertEquals("#0000FF", ColorMath.toHex(ColorMath.hsvToArgb(240f, 1f, 1f)))
        assertEquals("#FF0000", ColorMath.toHex(ColorMath.hsvToArgb(360f, 1f, 1f)))
    }

    @Test fun hsvRoundTrip() {
        val (h, s, v) = ColorMath.argbToHsv(0xFF1F3A5F.toInt())
        assertEquals("#1F3A5F", ColorMath.toHex(ColorMath.hsvToArgb(h, s, v)))
    }

    @Test fun shadeGoesFromPaleToDark() {
        assertTrue(ColorMath.isLight(ColorMath.shadeColor(200f, 0f)))
        assertFalse(ColorMath.isLight(ColorMath.shadeColor(200f, 1f)))
    }

    @Test fun slidersFindAColourTheyMade() {
        val made = ColorMath.shadeColor(150f, 0.7f)
        val (hue, shade) = ColorMath.slidersFor(made)
        assertTrue("hue $hue", abs(hue - 150f) < 2f)
        assertTrue("shade $shade", abs(shade - 0.7f) < 0.02f)
    }

    @Test fun lightOrDark() {
        assertTrue(ColorMath.isLight(0xFFF2C230.toInt()))  // yellow
        assertFalse(ColorMath.isLight(0xFF1F3A5F.toInt())) // navy
    }
}
