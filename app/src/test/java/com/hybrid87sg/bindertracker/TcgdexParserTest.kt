package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.cards.CardSet
import com.hybrid87sg.bindertracker.cards.TcgdexParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks the app reads TCGdex's replies correctly (small made-up samples in TCGdex's format). */
@RunWith(RobolectricTestRunner::class)
class TcgdexParserTest {
    @Test
    fun readsSeriesList() {
        val text = """[{"id":"base","name":"Base","logo":"https://assets.tcgdex.net/en/base/base1/logo"},{"id":"swsh","name":"Sword & Shield"}]"""
        assertEquals(listOf("base", "swsh"), TcgdexParser.seriesIds(text))
    }

    @Test
    fun readsSetsInSeries() {
        val text = """{"id":"swsh","name":"Sword & Shield","sets":[
            {"id":"swsh1","name":"Sword & Shield","cardCount":{"total":216,"official":202}},
            {"id":"swshp","name":"SWSH Black Star Promos","cardCount":{"total":307,"official":0}}]}"""
        val sets = TcgdexParser.setsInSeries(text)
        assertEquals(2, sets.size)
        assertEquals(CardSet("swsh1", "Sword & Shield", 202), sets[0])
        assertNull(sets[1].officialCount) // promos have no "/total" printed
    }

    @Test
    fun readsCardsAndFindsTheirSet() {
        val sets = mapOf(
            "swsh1" to CardSet("swsh1", "Sword & Shield", 202, order = 3),
            "sv03.5" to CardSet("sv03.5", "151", 165, order = 7),
        )
        val text = """[
            {"id":"swsh1-49","localId":"049","name":"Pikachu","image":"https://assets.tcgdex.net/en/swsh/swsh1/049"},
            {"id":"sv03.5-025","localId":"025","name":"Pikachu"},
            {"id":"A1-094","localId":"094","name":"Pikachu","image":"https://assets.tcgdex.net/en/tcgp/A1/094"}]"""
        val cards = TcgdexParser.cards(text, sets)
        assertEquals(2, cards.size) // the Pocket card (set A1) is left out

        val first = cards[0]
        assertEquals("swsh1", first.setId)
        assertEquals("Sword & Shield", first.setName)
        assertEquals("049/202", first.numberLabel)
        assertEquals("49", first.numberKey)
        assertEquals("https://assets.tcgdex.net/en/swsh/swsh1/049/low.webp", first.smallImageUrl)

        val second = cards[1]
        assertEquals("sv03.5", second.setId)
        assertEquals(7, second.setOrder)
        assertNull(second.smallImageUrl) // no picture yet
    }
}
