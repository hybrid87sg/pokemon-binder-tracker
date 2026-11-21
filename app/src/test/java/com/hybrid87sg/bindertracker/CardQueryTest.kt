package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.cards.Card
import com.hybrid87sg.bindertracker.cards.CardQuery
import com.hybrid87sg.bindertracker.cards.CardText
import com.hybrid87sg.bindertracker.cards.ScanMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks how typed searches are understood, without needing a phone. */
class CardQueryTest {
    @Test
    fun simplifiesText() {
        assertEquals("pokemon farfetchd", CardText.normalize("Pokémon Farfetch'd"))
        assertEquals("mr mime", CardText.normalize("Mr. Mime"))
        assertEquals("flabebe", CardText.normalize("Flabébé"))
    }

    @Test
    fun ignoresLeadingZerosInNumbers() {
        assertEquals("25", CardText.numberKey("025"))
        assertEquals("0", CardText.numberKey("000"))
        assertEquals("tg5", CardText.numberKey("TG05"))
        assertEquals("swsh50", CardText.numberKey("SWSH050"))
    }

    @Test
    fun readsNameOnly() {
        val q = CardQuery.parse("  Charizard  Base ")
        assertEquals(listOf("charizard", "base"), q.words)
        assertNull(q.numberKey)
        assertNull(q.setTotal)
    }

    @Test
    fun readsNumberWithSetTotal() {
        val q = CardQuery.parse("pikachu 049/202")
        assertEquals(listOf("pikachu"), q.words)
        assertEquals("49", q.numberKey)
        assertEquals(202, q.setTotal)
    }

    @Test
    fun readsTrainerGalleryNumber() {
        val q = CardQuery.parse("TG05/TG30")
        assertTrue(q.words.isEmpty())
        assertEquals("tg5", q.numberKey)
        assertEquals(30, q.setTotal)
    }

    @Test
    fun readsNumberAlone() {
        val q = CardQuery.parse("#25 pikachu")
        assertEquals("25", q.numberKey)
        assertEquals(listOf("pikachu"), q.words)
    }

    @Test
    fun buildsSqlWithBlanksForTypedText() {
        val (sql, args) = CardQuery.parse("pikachu 49/202").toSql(60)
        assertTrue(sql.startsWith("SELECT * FROM cards WHERE (searchName LIKE ? OR searchSet LIKE ?) AND numberKey = ?"))
        assertTrue(sql.endsWith("LIMIT 60"))
        // One value for each "?" in the SQL.
        assertEquals(sql.count { it == '?' }, args.size)
        assertEquals(listOf<Any>("%pikachu%", "%pikachu%", "49", 202, "pikachu", "pikachu%"), args)
    }

    @Test
    fun emptyQueryMatchesNothing() {
        assertTrue(CardQuery.parse("   ").isEmpty)
    }

    @Test
    fun scanPrefersMatchingSetSizeAndName() {
        fun card(id: String, name: String, total: Int?, order: Int) =
            Card(id = id, name = name, number = "049", setId = id.substringBefore('-'), setName = "Set", setTotal = total, setOrder = order, imageBase = null)
        val candidates = listOf(
            card("new-49", "Snorlax", 198, order = 9),
            card("old-49", "Pikachu", 102, order = 1),
            card("mid-49", "Pikachu", 202, order = 5),
        )
        val ranked = ScanMatcher.rank(candidates, name = "Pikachu", setTotal = 202)
        assertEquals(listOf("mid-49", "old-49", "new-49"), ranked.map { it.id })
    }
}
