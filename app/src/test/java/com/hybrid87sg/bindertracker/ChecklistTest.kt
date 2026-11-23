package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.BinderPocket
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.data.Checklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the Missing cards maths: secret rares, order, where a card goes, the copied list. */
class ChecklistTest {
    private fun card(set: String, number: String, name: String = "Card $number", rarity: String = "Common", official: Int = 10) =
        Card(id = "$set-$number", localId = number, name = name, setId = set, setName = set.uppercase(), imageUrl = null, rarity = rarity, setOfficial = official)

    private val set = (1..12).map { card("s1", "$it") } + card("s1", "TG01")

    @Test
    fun secretRaresAreNumberedPastTheSetSize() {
        assertFalse(Checklist.isSecret(card("s1", "10")))
        assertTrue(Checklist.isSecret(card("s1", "11")))
        assertTrue(Checklist.isSecret(card("s1", "TG01")))
        // Unknown set size: nothing counts as secret.
        assertFalse(Checklist.isSecret(card("s1", "99", official = 0)))
    }

    @Test
    fun mainSetLeavesOutSecretRaresAndSortsByNumber() {
        val main = Checklist.entries(set.shuffled(), listOf("s1"), owned = setOf("s1-2"), countSecret = false)
        assertEquals((1..10).map { "$it" }, main.map { it.card.number })
        assertEquals(listOf("s1-2"), main.filter { it.owned }.map { it.card.id })
        val master = Checklist.entries(set, listOf("s1"), emptySet(), countSecret = true)
        assertEquals(listOf("11", "12", "TG01"), master.takeLast(3).map { it.card.number })
    }

    @Test
    fun expansionsKeepTheOrderTheyWereChosenIn() {
        val cards = listOf(card("a", "1"), card("b", "1"), card("a", "2"))
        assertEquals(listOf("b-1", "a-1", "a-2"), Checklist.entries(cards, listOf("b", "a"), emptySet(), false).map { it.card.id })
    }

    @Test
    fun aMissingCardGoesRightAfterItsNeighbour() {
        val entries = Checklist.entries(set, listOf("s1"), owned = setOf("s1-1", "s1-2", "s1-5"), countSecret = false)
        // Page A: #1, #2, (free), #5. Page B: (free) (free).
        val pockets = listOf(
            BinderPocket("A", 1, 0, "s1-1"), BinderPocket("A", 1, 1, "s1-2"), BinderPocket("A", 1, 2, null), BinderPocket("A", 1, 3, "s1-5"),
            BinderPocket("B", 2, 0, null), BinderPocket("B", 2, 1, null),
        )
        val three = Checklist.suggestSpot(card("s1", "3"), entries, pockets)!!
        assertEquals("A", three.pageId)
        assertEquals(2, three.position)
        assertEquals("s1-2", three.after?.id)
        // #6 follows #5, but the pocket after #5 is on page B and free.
        val six = Checklist.suggestSpot(card("s1", "6"), entries, pockets)!!
        assertEquals(2, six.pageNumber)
        assertEquals(0, six.position)
        assertEquals("s1-5", six.after?.id)
    }

    @Test
    fun freePocketsShowTheMissingCardsInSetOrder() {
        val entries = Checklist.entries(set, listOf("s1"), owned = setOf("s1-1", "s1-2", "s1-5"), countSecret = false)
        // Page A: #1, #2, (free), (free), #5. Page B: (free) (free).
        val pockets = listOf(
            BinderPocket("A", 1, 0, "s1-1"), BinderPocket("A", 1, 1, "s1-2"), BinderPocket("A", 1, 2, null),
            BinderPocket("A", 1, 3, null), BinderPocket("A", 1, 4, "s1-5"),
            BinderPocket("B", 2, 0, null), BinderPocket("B", 2, 1, null),
        )
        val ghosts = Checklist.ghosts(entries, pockets)
        assertEquals("s1-3", ghosts["A" to 2]?.id)
        assertEquals("s1-4", ghosts["A" to 3]?.id)
        assertEquals("s1-6", ghosts["B" to 0]?.id)
        assertEquals("s1-7", ghosts["B" to 1]?.id)
        // "Put it in" from the card screen goes to the pocket showing it.
        val four = Checklist.suggestSpot(card("s1", "4"), entries, pockets)!!
        assertEquals("A", four.pageId)
        assertEquals(3, four.position)
    }

    @Test
    fun outOfOrderBindersStillShowEachMissingCardOnce() {
        val entries = Checklist.entries(set, listOf("s1"), owned = setOf("s1-9"), countSecret = false)
        // #9 first, then free pockets: #10, then back to the start (#1, #2...).
        val pockets = listOf(BinderPocket("A", 1, 0, "s1-9")) + (1..4).map { BinderPocket("A", 1, it, null) }
        val ghosts = Checklist.ghosts(entries, pockets)
        assertEquals(listOf("s1-10", "s1-1", "s1-2", "s1-3"), (1..4).map { ghosts["A" to it]?.id })
        // Everything owned: nothing to show.
        val full = Checklist.entries(set, listOf("s1"), owned = set.map { it.id }.toSet(), countSecret = false)
        assertTrue(Checklist.ghosts(full, pockets).isEmpty())
        // More free pockets than missing cards: the rest stay blank.
        val few = Checklist.entries(set.take(2), listOf("s1"), owned = emptySet(), countSecret = false)
        assertEquals(2, Checklist.ghosts(few, pockets).size)
    }

    @Test
    fun withNoNeighbourItTakesTheFirstFreePocket() {
        val entries = Checklist.entries(set, listOf("s1"), owned = emptySet(), countSecret = false)
        val pockets = listOf(BinderPocket("A", 1, 0, "other-1"), BinderPocket("A", 1, 1, null))
        val spot = Checklist.suggestSpot(card("s1", "4"), entries, pockets)!!
        assertEquals(1, spot.position)
        assertNull(spot.after)
        assertNull(Checklist.suggestSpot(card("s1", "4"), entries, listOf(BinderPocket("A", 1, 0, "x"))))
    }

    @Test
    fun almostThereListsTheRaritiesClosestToDone() {
        val cards = listOf(
            card("s1", "1", rarity = "Common"), card("s1", "2", rarity = "Common"),
            card("s1", "3", rarity = "Rare"), card("s1", "4", rarity = "Rare"),
            card("s1", "5", rarity = "Uncommon"),
        )
        val entries = Checklist.entries(cards, listOf("s1"), owned = setOf("s1-1", "s1-3", "s1-5"), countSecret = false)
        assertEquals(listOf("Common" to 1, "Rare" to 1), Checklist.almostThere(entries))
        assertEquals(listOf("Uncommon"), Checklist.completeRarities(entries))
    }

    @Test
    fun copiedListIsGroupedByExpansion() {
        val entries = Checklist.entries(set, listOf("s1"), owned = (3..10).map { "s1-$it" }.toSet(), countSecret = false)
        val text = Checklist.copyText("Kanto", entries.filter { !it.owned }, mapOf("s1" to "Base Set"))
        assertEquals("Missing from my Kanto binder (2 cards)\n\nBase Set\n#1/10 Card 1\n#2/10 Card 2", text)
    }

    @Test
    fun milestonesFireOnceWhenPassed() {
        assertEquals(50, Checklist.milestone(before = 9, after = 10, total = 20))
        assertNull(Checklist.milestone(before = 10, after = 11, total = 20))
        assertEquals(100, Checklist.milestone(before = 19, after = 20, total = 20))
        assertNull(Checklist.milestone(before = 5, after = 5, total = 20))
    }
}
