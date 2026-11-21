package com.hybrid87sg.bindertracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hybrid87sg.bindertracker.data.AppDatabase
import com.hybrid87sg.bindertracker.data.CardIndex
import com.hybrid87sg.bindertracker.tcgdex.Tcgdex
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks reading real TCGdex replies (saved in src/test/resources/tcgdex)
 * and searching the card index.
 */
@RunWith(RobolectricTestRunner::class)
class CardIndexTest {
    private lateinit var db: AppDatabase
    private lateinit var index: CardIndex

    private fun resource(name: String) = javaClass.getResource("/tcgdex/$name")!!.readText()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        index = CardIndex(context, db.cardDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun readsCardListWithSetNames() {
        val sets = Tcgdex.parseSetNames(resource("sets.json"))
        val cards = Tcgdex.parseCardList(resource("cards-sample.json"), sets)
        val charizard = cards.single { it.id == "base1-4" }
        assertEquals("Charizard", charizard.name)
        assertEquals("4", charizard.localId)
        assertEquals("base1", charizard.setId)
        assertEquals("Base Set", charizard.setName)
        assertEquals("https://assets.tcgdex.net/en/base/base1/4/low.webp", charizard.thumbnailUrl)
        // A set id containing dashes still works ("tk-hs-r" + "-2").
        assertEquals("tk-hs-r", cards.single { it.id == "tk-hs-r-2" }.setId)
        // A web-encoded number ("%3F") shows as "?".
        assertEquals("?", cards.single { it.id == "exu-%3F" }.number)
    }

    @Test
    fun readsMarketPrices() {
        val furret = Tcgdex.parseCardDetails(resource("card-swsh3-136.json"))
        assertEquals("Furret", furret.name)
        assertEquals("Uncommon", furret.rarity)
        val tcgNormal = furret.prices.single { it.source == "TCGplayer" && it.variant == "Normal" }
        assertEquals("USD", tcgNormal.currency)
        assertTrue(tcgNormal.amount > 0)
        assertTrue(furret.prices.any { it.source == "Cardmarket" && it.currency == "EUR" })

        val charizard = Tcgdex.parseCardDetails(resource("card-base1-4.json"))
        assertTrue(charizard.prices.any { it.source == "TCGplayer" && it.variant == "Holo" })
    }

    @Test
    fun splitsNameAndNumber() {
        assertEquals(CardIndex.Companion.Query("Pikachu", "025"), CardIndex.parseQuery("Pikachu 025/165"))
        assertEquals(CardIndex.Companion.Query("", "25"), CardIndex.parseQuery("#25"))
        assertEquals(CardIndex.Companion.Query("charizard", null), CardIndex.parseQuery("  charizard "))
        assertEquals(CardIndex.Companion.Query("Porygon2", null), CardIndex.parseQuery("Porygon2"))
        assertNull(CardIndex.parseQuery("   "))
    }

    @Test
    fun searchesByNameAndNumber() = runTest {
        val cards = Tcgdex.parseCardList(resource("cards-sample.json"), Tcgdex.parseSetNames(resource("sets.json")))
        db.cardDao().replaceAll(cards)

        val pikachus = index.search("pika")
        assertTrue(pikachus.isNotEmpty())
        assertTrue(pikachus.all { it.name.contains("Pikachu", ignoreCase = true) })

        // "004/102" finds Base Set Charizard; leading zeros don't matter.
        assertTrue(index.search("004/102").any { it.id == "base1-4" })
        assertEquals(listOf("base1-4"), index.search("charizard 4").map { it.id })
        assertTrue(index.search("zzzz").isEmpty())
    }

    @Test
    fun filtersBySeries() = runTest {
        // Small made-up replies in TCGdex's shape.
        val series = Tcgdex.parseSeriesList("""[{"id":"base","name":"Base"},{"id":"swsh","name":"Sword & Shield"}]""")
        assertEquals(listOf("base" to "Base", "swsh" to "Sword & Shield"), series)
        assertEquals(listOf("swsh3"), Tcgdex.parseSeriesSets("""{"id":"swsh","name":"Sword & Shield","sets":[{"id":"swsh3","name":"Darkness Ablaze"}]}"""))

        val seriesOfSet = mapOf(
            "base1" to Tcgdex.SeriesInfo("base", "Base", 0),
            "swsh3" to Tcgdex.SeriesInfo("swsh", "Sword & Shield", 1),
        )
        val cards = Tcgdex.parseCardList(resource("cards-sample.json"), Tcgdex.parseSetNames(resource("sets.json")), seriesOfSet)
        db.cardDao().replaceAll(cards)
        assertEquals("Base", cards.single { it.id == "base1-4" }.seriesName)

        // Newest series first in the drop-down.
        assertEquals(listOf("swsh", "base").filter { id -> cards.any { it.seriesId == id } }, index.series().map { it.id })
        // Only Base series cards when Base is chosen.
        assertTrue(index.search("charizard", seriesId = "base").all { it.seriesId == "base" })
        assertTrue(index.search("charizard", seriesId = "base").any { it.id == "base1-4" })
        assertTrue(index.search("charizard 4", seriesId = "swsh").none { it.id == "base1-4" })
    }
}
