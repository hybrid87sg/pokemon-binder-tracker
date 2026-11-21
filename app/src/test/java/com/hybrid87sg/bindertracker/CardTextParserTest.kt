package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.scan.CardCandidate
import com.hybrid87sg.bindertracker.scan.CardTextParser
import com.hybrid87sg.bindertracker.scan.PocketDraft
import com.hybrid87sg.bindertracker.scan.ReadCard
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

    @Test fun ranksSameNumberAndNameFirst() {
        val read = ReadCard(0, "Pikachu", "025", 165, "Pikachu\n025/165")
        val wrong = CardCandidate("a", "Pikachu", "Base Set", "58/102")
        val right = CardCandidate("b", "Pikachu", "151", "025/165")
        val draft = PocketDraft.from(read, listOf(wrong, right))
        assertEquals(right, draft.chosen)
    }

    @Test fun emptyPocketStaysEmpty() {
        val draft = PocketDraft.from(ReadCard(3, null, null, null, ""), emptyList())
        assertTrue(draft.empty)
        assertNull(draft.displayName)
    }
}
