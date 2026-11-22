package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.tcgdex.Tcgdex
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Reading the extra TCGdex replies the Missing cards page needs: set sizes and rarities. */
@RunWith(RobolectricTestRunner::class)
class TcgdexRarityTest {
    private fun resource(name: String) = javaClass.getResource("/tcgdex/$name")!!.readText()

    @Test
    fun readsOfficialSetSizes() {
        val sizes = Tcgdex.parseSetOfficialCounts(resource("sets.json"))
        assertEquals(102, sizes["base1"])
        assertEquals(64, sizes["base2"])
    }

    @Test
    fun readsRarityNamesAndCards() {
        assertEquals(listOf("Common", "Rare Holo"), Tcgdex.parseRarityNames("""["Common","Rare Holo"]"""))
        val reply = """{"name":"Rare Holo","cards":[{"id":"base1-4","localId":"4","name":"Charizard"},{"id":"base1-2","localId":"2","name":"Blastoise"}]}"""
        assertEquals(listOf("base1-4", "base1-2"), Tcgdex.parseRarityCards(reply))
        // A plain list works too.
        assertEquals(listOf("base1-4"), Tcgdex.parseRarityCards("""[{"id":"base1-4"}]"""))
    }
}
