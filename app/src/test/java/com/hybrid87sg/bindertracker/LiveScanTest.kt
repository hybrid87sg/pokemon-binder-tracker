package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.scan.LiveCheck
import com.hybrid87sg.bindertracker.scan.LiveStep
import com.hybrid87sg.bindertracker.scan.MoveMatcher
import com.hybrid87sg.bindertracker.scan.ReadCard
import com.hybrid87sg.bindertracker.tcgdex.Tcgdex
import com.hybrid87sg.bindertracker.ui.components.Motif
import com.hybrid87sg.bindertracker.ui.components.accentFor
import com.hybrid87sg.bindertracker.ui.components.isShinyRarity
import com.hybrid87sg.bindertracker.ui.components.pageExpansion
import com.hybrid87sg.bindertracker.ui.screens.StepState
import com.hybrid87sg.bindertracker.ui.screens.ladderFor
import com.hybrid87sg.bindertracker.ui.screens.nextPocketToScan
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Live scanning's pieces that don't need a camera: moves, the checks list and the next pocket. */
@RunWith(RobolectricTestRunner::class)
class LiveScanTest {

    private fun card(id: String, name: String) = Card(id = id, localId = id.substringAfter('-'), name = name, setId = id.substringBefore('-'), setName = "Set", imageUrl = null)

    @Test
    fun readsMovesFromTcgdex() {
        val json = JSONObject("""{"id":"sv3-125","name":"Charizard ex","abilities":[{"name":"Infernal Reign"}],"attacks":[{"name":"Burning Darkness"},{"name":" "}]}""")
        assertEquals(listOf("Infernal Reign", "Burning Darkness"), Tcgdex.parseMoves(json))
        assertEquals(emptyList<String>(), Tcgdex.parseMoves(JSONObject("""{"id":"x","name":"Potion"}""")))
    }

    @Test
    fun movesNarrowCardsWithTheSameName() {
        val read = "Charizard ex\nINFERNAL REIGN\nBurning  Darkness 180"
        val moves = mapOf(
            "sv3-125" to listOf("Infernal Reign", "Burning Darkness"),
            "sv3-223" to listOf("Infernal Reign", "Burning Darkness"),
            "sv35-6" to listOf("Brave Wing", "Explosive Vortex"),
        )
        assertEquals(setOf("sv3-125", "sv3-223"), MoveMatcher.narrow(read, moves).toSet())
        // Nothing read that matches: no narrowing at all.
        assertEquals(emptyList<String>(), MoveMatcher.narrow("Charizard ex", moves))
        assertEquals(2, MoveMatcher.found(read, moves.getValue("sv3-125")))
    }

    @Test
    fun nextPocketSkipsFilledEmptyAndJustPlaced() {
        val pockets = List(6) { i ->
            when (i) {
                1 -> PocketWithCard(i, false, "a-1", "A", "1", "S", null)
                3 -> PocketWithCard(i, true, null, null, null, null, null)
                else -> PocketWithCard(i, false, null, null, null, null, null)
            }
        }
        assertEquals(2, nextPocketToScan(pockets, from = 0, placed = setOf(0)))
        assertEquals(4, nextPocketToScan(pockets, from = 2, placed = setOf(0, 2)))
        // Goes back to the start for pockets skipped earlier.
        assertEquals(0, nextPocketToScan(pockets, from = 5, placed = setOf(5)))
        assertNull(nextPocketToScan(pockets, from = 5, placed = setOf(0, 2, 4, 5)))
    }

    @Test
    fun ladderShowsWhichCheckFoundTheCard() {
        val read = ReadCard(0, "Pikachu", "025", 198, "Pikachu\n025/198")
        val byNumber = ladderFor(read, LiveCheck.Sure(card("sv1-25", "Pikachu"), LiveStep.Number))
        assertEquals(StepState.Found, byNumber.number.state)
        assertEquals("025/198", byNumber.number.note)
        assertEquals(StepState.Skipped, byNumber.artwork.state)

        val worn = ReadCard(0, "Charizard ex", null, null, "Charizard ex")
        val unsure = ladderFor(worn, LiveCheck.Unsure("Charizard ex", listOf(card("a-1", "Charizard ex")), 6, true, false))
        assertEquals(StepState.NotFound, unsure.number.state)
        assertEquals("Charizard ex · 6 cards", unsure.name.note)
        assertEquals("6 → 1 cards", unsure.moves.note)
    }

    @Test
    fun shinyCardsAndExpansionLooks() {
        assertTrue(isShinyRarity("Rare Holo"))
        assertTrue(isShinyRarity("Special illustration rare"))
        assertFalse(isShinyRarity("Common"))
        assertFalse(isShinyRarity(null))
        assertEquals(Motif.SunMoon, accentFor("sm")?.motif)
        assertNull(accentFor(""))

        val pockets = listOf(
            PocketWithCard(0, false, "sm2-1", "A", "1", "Guardians Rising", null, "Common", "sm2", "sm"),
            PocketWithCard(1, false, "sm2-2", "B", "2", "Guardians Rising", null, "Common", "sm2", "sm"),
            PocketWithCard(2, false, "sv1-3", "C", "3", "Scarlet & Violet", null, "Common", "sv1", "sv"),
            PocketWithCard(3, false, null, null, null, null, null),
        )
        assertEquals("Guardians Rising", pageExpansion(pockets)?.setName)
        assertNull(pageExpansion(listOf(PocketWithCard(0, true, null, null, null, null, null))))
    }
}
