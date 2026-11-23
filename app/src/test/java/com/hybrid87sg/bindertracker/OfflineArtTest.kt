package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.CardArt
import com.hybrid87sg.bindertracker.data.OfflineArt
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineArtTest {
    @Test fun safeFileNames() {
        assertEquals("sm1-89.img", OfflineArt.fileName("sm1-89"))
        assertEquals("a_b-1.img", OfflineArt.fileName("a/b-1"))
    }

    private fun art(id: String) = CardArt(id, "https://assets.tcgdex.net/en/x/$id")

    @Test fun binderCardsAreNotCountedAsMissing() {
        val plan = OfflineArt.plan(
            binderCards = listOf(art("sv01-1"), art("sv01-1"), art("base1-4")),
            expansionCards = listOf(art("sv01-1"), art("sv01-2"), art("sv01-3")),
            includeMissing = true,
        )
        assertEquals(listOf("sv01-1", "base1-4"), plan.binder.map { it.id })
        assertEquals(listOf("sv01-2", "sv01-3"), plan.missing.map { it.id })
        assertEquals(setOf("sv01-1", "base1-4", "sv01-2", "sv01-3"), plan.keep)
    }

    @Test fun missingCardsOffKeepsOnlyBinderCards() {
        val plan = OfflineArt.plan(listOf(art("sv01-1")), listOf(art("sv01-2")), includeMissing = false)
        assertEquals(setOf("sv01-1"), plan.keep)
    }
}
