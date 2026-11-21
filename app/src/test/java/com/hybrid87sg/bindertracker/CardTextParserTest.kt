package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.scan.CardTextParser
import com.hybrid87sg.bindertracker.scan.PocketMatcher
import com.hybrid87sg.bindertracker.scan.ReadCard
import com.hybrid87sg.bindertracker.scan.ReadingSummary
import com.hybrid87sg.bindertracker.scan.TextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that we pick the right name and number out of the text the camera
 * reads. These run on the computer, no phone or camera needed.
 */
class CardTextParserTest {
    private fun line(text: String, top: Int, left: Int = 10) = TextLine(text, left, top, left + 200, top + 20)

    @Test fun readsNameAndNumberFromACard() {
        val lines = listOf(
            line("BASIC", top = 5),
            line("Pikachu HP 60", top = 30),
            line("Gnaw 10", top = 500),
            line("Illus. Mitsuhiro Arita", top = 880),
            line("025/165", top = 900),
        )
        val card = CardTextParser.readPocket(0, lines, pocketTop = 0, pocketHeight = 1000)
        assertEquals("Pikachu", card.name)
        assertEquals("025", card.number)
        assertEquals(165, card.setTotal)
        assertEquals("025/165", card.printedNumber)
    }

    @Test fun stripsStageWordsAndHp() {
        assertEquals("Charizard ex", CardTextParser.cleanName("STAGE 2 Charizard ex 330HP"))
        assertEquals("Ivysaur", CardTextParser.cleanName("STAGE1 Ivysaur HP 90"))
        assertEquals("Porygon2", CardTextParser.cleanName("Porygon2"))
        assertNull(CardTextParser.cleanName("Evolves from Charmeleon"))
        assertNull(CardTextParser.cleanName("V"))
        // "Pokémon" alone is a label, but it can start a real card name.
        assertNull(CardTextParser.cleanName("Pokémon"))
        assertEquals("Pokémon Catcher", CardTextParser.cleanName("Pokémon Catcher"))
    }

    @Test fun readsLetterNumbersAndFixesLookAlikes() {
        assertEquals("TG05" to 30, CardTextParser.findNumber(listOf(line("TG05/TG30", top = 0))))
        assertEquals("010" to 102, CardTextParser.findNumber(listOf(line("Ol0/1O2", top = 0))))
        assertNull(CardTextParser.findNumber(listOf(line("Pikachu", top = 0))))
    }

    @Test fun ignoresNameLinesLowDownOnTheCard() {
        // Only text in the top part of the pocket can be the name.
        val lines = listOf(line("Thunder Shock 20", top = 600))
        assertNull(CardTextParser.findName(lines, pocketTop = 0, pocketHeight = 1000))
    }

    @Test fun sortsLinesIntoPockets() {
        // A 3 × 3 page that is 300 × 300 pixels: each pocket is 100 × 100.
        val lines = listOf(
            TextLine("top-left", 10, 10, 40, 20),
            TextLine("middle", 120, 140, 180, 150),
            TextLine("bottom-right", 250, 280, 290, 290),
        )
        val pockets = CardTextParser.splitIntoPockets(lines, 300, 300, columns = 3, rows = 3)
        assertEquals(9, pockets.size)
        assertEquals("top-left", pockets[0].single().text)
        assertEquals("middle", pockets[4].single().text)
        assertEquals("bottom-right", pockets[8].single().text)
        assertTrue(pockets[1].isEmpty())
    }

    private fun card(id: String, number: String, name: String, set: String = "Set") =
        Card(id = id, localId = number, name = name, setId = id.substringBefore('-'), setName = set, imageUrl = null)

    @Test fun matcherNeedsNumberAndName() {
        val read = ReadCard(0, "Pikachu", "025", 165, "Pikachu\n025/165")
        val pikachu = card("sv3pt5-25", "25", "Pikachu", "151")
        val notPikachu = card("base1-25", "25", "Dewgong", "Base Set")
        assertEquals(pikachu, PocketMatcher.pick(read, listOf(notPikachu, pikachu)))
        // Number matches but the name doesn't: leave it for the person.
        assertNull(PocketMatcher.pick(read, listOf(notPikachu)))
        // No number read: not sure enough.
        assertNull(PocketMatcher.pick(read.copy(number = null), listOf(pikachu)))
    }

    @Test fun matcherAcceptsCloseNames() {
        val read = ReadCard(0, "Charizard", "006", 165, "")
        val card = card("sv3pt5-6", "006", "Charizard ex")
        assertEquals(card, PocketMatcher.pick(read, listOf(card)))
        assertEquals(PocketMatcher.simplify("Flabébé"), PocketMatcher.simplify("FLABEBE"))
        // Same number, but Mew is not Mewtwo.
        val mew = card("x-25", "25", "Mew")
        assertNull(PocketMatcher.pick(ReadCard(0, "Mewtwo", "025", null, ""), listOf(mew)))
    }

    @Test fun readingMessages() {
        assertEquals("Recognised all 9 cards. Tap one to check or change it.", ReadingSummary(9, 9, 9, 0, rescan = false).message())
        assertEquals(
            "Updated 2 pockets with new cards (recognised 7 of 9).",
            ReadingSummary(9, 7, 2, 0, rescan = true).message(),
        )
        assertTrue(ReadingSummary(9, 0, 0, 5, rescan = false).message().contains("number on 5 cards"))
        assertTrue(ReadingSummary(9, 6, 0, 0, rescan = true).message().contains("Nothing new"))
    }
}
