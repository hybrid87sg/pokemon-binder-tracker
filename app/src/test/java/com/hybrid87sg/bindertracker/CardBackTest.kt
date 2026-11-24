package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.CardBackStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the card back setting reads back the way it was saved. */
class CardBackTest {
    @Test
    fun savedKeysReadBackToTheSameDesign() {
        for (style in CardBackStyle.entries) assertEquals(style, CardBackStyle.of(style.key))
        // Nothing saved yet, or something unknown: the original blue back.
        assertEquals(CardBackStyle.Original, CardBackStyle.of(null))
        assertEquals(CardBackStyle.Original, CardBackStyle.of("rainbow"))
    }

    @Test
    fun onlyTheThreeNewDesignsFollowTheBinderColour() {
        assertEquals(listOf(CardBackStyle.Orbit, CardBackStyle.Prism, CardBackStyle.Lattice), CardBackStyle.entries.filter { it.followsBinder })
        assertFalse(CardBackStyle.Own.followsBinder)
        assertTrue(CardBackStyle.entries.first() == CardBackStyle.Original)
    }
}
