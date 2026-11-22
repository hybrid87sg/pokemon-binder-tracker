package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.data.CardPlace
import com.hybrid87sg.bindertracker.data.OwnedFirst
import com.hybrid87sg.bindertracker.prices.ExchangeRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Exchange rates for S$ prices, and your own cards first in search. */
@RunWith(RobolectricTestRunner::class)
class SgdPricesTest {
    @Test
    fun readsFrankfurterRates() {
        val rates = ExchangeRates.parse("""{"amount":1.0,"base":"EUR","date":"2026-11-21","rates":{"SGD":1.42,"USD":1.1}}""")
        assertEquals("2026-11-21", rates.date)
        assertEquals(1.42, rates.toSgd["EUR"]!!, 1e-9)
        assertEquals(1.42 / 1.1, rates.toSgd["USD"]!!, 1e-9)
        assertEquals(14.2, rates.convert(10.0, "EUR")!!, 1e-9)
        assertEquals(5.0, rates.convert(5.0, "SGD")!!, 1e-9)
        assertNull(rates.convert(5.0, "JPY"))
    }

    private fun card(id: String) = Card(id, id.substringAfter('-'), "Pikachu", id.substringBefore('-'), "Set", null)

    @Test
    fun yourCardsComeFirst() {
        val found = listOf(card("base1-58"), card("sv03.5-025"), card("swsh4-44"), card("sv08-57"))
        val places = listOf(
            CardPlace("sv08-57", "Favourites", 3),
            CardPlace("sv03.5-025", "Kanto Binder", 1),
            CardPlace("sv03.5-025", "Kanto Binder", 1), // two copies on one page count once
            CardPlace("sv03.5-025", "Favourites", 2),
        )
        val result = OwnedFirst.arrange(found, places, limit = 1)
        assertEquals(listOf("sv03.5-025", "sv08-57", "base1-58"), result.cards.map { it.id })
        assertEquals("Kanto Binder · page 1 + 1 more", result.places["sv03.5-025"])
        assertEquals("Favourites · page 3", result.places["sv08-57"])
        assertTrue(result.moreOthers)
        assertFalse(OwnedFirst.arrange(found, emptyList()).moreOthers)
    }
}
