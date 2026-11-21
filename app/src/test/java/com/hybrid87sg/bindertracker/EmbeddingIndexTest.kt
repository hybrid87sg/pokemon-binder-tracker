package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.scan.EmbeddingIndex
import com.hybrid87sg.bindertracker.scan.GuessPicker
import com.hybrid87sg.bindertracker.scan.ReadCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.random.Random

/**
 * Checks reading the card pictures file and finding the closest cards,
 * with made-up numbers instead of real pictures.
 */
class EmbeddingIndexTest {
    private val dim = 16
    private val random = Random(42)
    private val cards = List(50) { i -> "set-$i" to FloatArray(dim) { random.nextFloat() * 2 - 1 } }

    // Writes the same file layout as scripts/build_card_embeddings.py.
    private fun file(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            write("CEMB".toByteArray())
            writeInt(1); writeInt(dim); writeInt(cards.size)
            cards.forEach { (id, _) -> val raw = id.toByteArray(); writeShort(raw.size); write(raw) }
            cards.forEach { (_, v) ->
                val peak = v.maxOf { kotlin.math.abs(it) }
                v.forEach { writeByte(Math.round(it / peak * 127)) }
            }
        }
        return bytes.toByteArray()
    }

    private fun noisy(v: FloatArray) = FloatArray(v.size) { v[it] + (random.nextFloat() - 0.5f) * 0.3f }

    @Test fun findsTheClosestCard() {
        val index = EmbeddingIndex.read(ByteArrayInputStream(file()))
        assertEquals(50, index.ids.size)
        val guesses = index.nearest(noisy(cards[17].second), count = 5)
        assertEquals("set-17", guesses.first().cardId)
        assertEquals(5, guesses.size)
        // Best first.
        assertEquals(guesses.sortedByDescending { it.score }, guesses)
    }

    private fun read(name: String?, number: String?) = ReadCard(0, name, number, null, name.orEmpty())
    private fun guess(id: String, score: Float, name: String, number: String) = EmbeddingIndex.Guess(id, score) to (name to number)

    @Test fun textThatAgreesPicksTheGuess() {
        val guesses = listOf(guess("a", 0.80f, "Pikachu", "58"), guess("b", 0.79f, "Pikachu", "25"))
        // Too close to call on picture alone...
        assertNull(GuessPicker.choose(read(null, null), guesses))
        // ...but the number read settles it.
        assertEquals("b", GuessPicker.choose(read("Pikachu", "025"), guesses))
    }

    @Test fun clearLeaderIsUsedWithoutText() {
        val guesses = listOf(guess("a", 0.85f, "Charizard", "4"), guess("b", 0.70f, "Charmeleon", "24"))
        assertEquals("a", GuessPicker.choose(read(null, null), guesses))
    }

    @Test fun weakGuessIsNotUsed() {
        val guesses = listOf(guess("a", 0.40f, "Charizard", "4"), guess("b", 0.20f, "Charmeleon", "24"))
        assertNull(GuessPicker.choose(read(null, null), guesses))
    }

    @Test fun ranksOnlyTheShortlist() {
        val index = EmbeddingIndex.read(ByteArrayInputStream(file()))
        val ranked = index.rank(noisy(cards[8].second), listOf("set-3", "set-8", "set-40", "not-a-card"))
        assertEquals(listOf("set-8"), ranked.take(1).map { it.cardId })
        assertEquals(3, ranked.size)
    }

    @Test fun sameNameChoice() {
        assertEquals("a", GuessPicker.chooseAmongSameName(listOf(EmbeddingIndex.Guess("a", 0.8f))))
        assertEquals("a", GuessPicker.chooseAmongSameName(listOf(EmbeddingIndex.Guess("a", 0.85f), EmbeddingIndex.Guess("b", 0.80f))))
        assertNull(GuessPicker.chooseAmongSameName(listOf(EmbeddingIndex.Guess("a", 0.81f), EmbeddingIndex.Guess("b", 0.80f))))
    }
}
