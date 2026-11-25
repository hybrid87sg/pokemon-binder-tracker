package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.CardFact
import com.hybrid87sg.bindertracker.data.CardFacts
import com.hybrid87sg.bindertracker.tcgdex.MarketPrice
import com.hybrid87sg.bindertracker.tcgdex.Tcgdex
import com.hybrid87sg.bindertracker.ui.components.isShinyRarity
import com.hybrid87sg.bindertracker.ui.screens.FilterLogic
import com.hybrid87sg.bindertracker.ui.screens.FilterLogic.Choice
import com.hybrid87sg.bindertracker.ui.screens.FilterLogic.Item
import com.hybrid87sg.bindertracker.ui.screens.FilterLogic.Quick
import com.hybrid87sg.bindertracker.ui.screens.FilterLogic.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The binder page's Filter: how ticks combine, and saving card details. */
@RunWith(RobolectricTestRunner::class)
class FilterLogicTest {
    private val pika = Item("p1", 0, "sv03.5-025", "Pikachu", "Common", "151", owned = true)
    private val pikaIr = Item("p1", 1, "sv03.5-173", "Pikachu", "Illustration rare", "151", owned = true)
    private val zard = Item("p1", 2, "sv03.5-006", "Charizard ex", "Double rare", "151", owned = true)
    private val mew = Item("p2", 0, "sv03.5-151", "Mew ex", "Double rare", "151", owned = false)
    private val pikaAgain = Item("p2", 1, "sv03.5-025", "Pikachu", "Common", "151", owned = true)
    private val items = listOf(pika, pikaIr, zard, mew, pikaAgain)
    private val facts = mapOf(
        "sv03.5-025" to CardFact("Atsuko Nishida", "Pokemon", listOf("Lightning"), 0.5, "USD", 0),
        "sv03.5-006" to CardFact("5ban Graphics", "Pokemon", listOf("Fire"), 40.0, "USD", 0),
    )
    private val sgd: (CardFact) -> Double? = { f -> f.price?.times(1.3) }
    private fun hits(c: Choice) = items.filter { FilterLogic.matches(c, it, facts[it.cardId], FilterLogic.duplicates(items), ::isShinyRarity, sgd) }

    @Test
    fun ticksInOneListAddUp_ticksAcrossListsNarrowDown() {
        val both = Choice().toggle(Section.Pokemon, "Pikachu").toggle(Section.Pokemon, "Charizard")
        assertEquals(listOf(pika, pikaIr, zard, pikaAgain), hits(both))
        val holoPika = Choice().toggle(Section.Pokemon, "Pikachu").toggle(Quick.Holo)
        assertEquals(listOf(pikaIr), hits(holoPika))
    }

    @Test
    fun quickPicks() {
        assertEquals(listOf(mew), hits(Choice().toggle(Quick.Missing)))
        assertEquals(items, hits(Choice().toggle(Quick.Missing).toggle(Quick.Collected)))
        assertEquals(listOf(pika, pikaAgain), hits(Choice().toggle(Quick.Duplicates)))
    }

    @Test
    fun detailsFromTcgdex() {
        assertEquals(listOf(zard), hits(Choice().toggle(Section.Type, "Fire")))
        assertEquals(listOf(zard), hits(Choice().toggle(Section.Price, "S$50 and up")))
        assertEquals(listOf(pika, pikaAgain), hits(Choice().toggle(Section.Artist, "Atsuko Nishida")))
        assertEquals("Charizard", FilterLogic.pokemonName("Charizard ex"))
        assertEquals("Pikachu", FilterLogic.pokemonName("Pikachu VMAX"))
    }

    @Test
    fun choiceSurvivesSaving() {
        val c = Choice().toggle(Quick.Holo).toggle(Section.Artist, "Name: with colon")
        assertEquals(c, Choice.restore(c.save()))
        assertFalse(Choice().active)
        assertTrue(c.active)
        assertEquals(2, c.count)
    }

    @Test
    fun cardFactsSaveAndReadBack() {
        val saved = CardFacts.decode(CardFacts.encode(facts))
        assertEquals(facts, saved)
        val price = CardFacts.typicalPrice(listOf(MarketPrice("Cardmarket", "Normal", 1.0, "EUR", null), MarketPrice("TCGplayer", "Holo", 2.0, "USD", null)))
        assertEquals(2.0, price!!.amount, 0.0)
    }

    @Test
    fun readsCategoryAndTypes() {
        val d = Tcgdex.parseCardDetails("""{"id":"x-1","name":"Pikachu","category":"Pokemon","types":["Lightning"],"illustrator":"A"}""")
        assertEquals("Pokemon", d.category)
        assertEquals(listOf("Lightning"), d.types)
    }
}
