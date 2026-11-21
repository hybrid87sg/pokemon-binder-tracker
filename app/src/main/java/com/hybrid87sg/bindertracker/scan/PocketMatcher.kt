package com.hybrid87sg.bindertracker.scan

import com.hybrid87sg.bindertracker.data.Card
import java.text.Normalizer

/**
 * Decides which card from the card index is in a pocket, from what the
 * camera read. It only answers when it's fairly sure: the number printed on
 * the card AND its name must both match. Otherwise the pocket is left for
 * the person to choose, which is better than filling in a wrong card.
 */
object PocketMatcher {

    fun pick(read: ReadCard, candidates: List<Card>): Card? {
        val number = read.number?.let(::numberKey) ?: return null
        val name = read.name?.let(::simplify)?.takeIf { it.length >= 3 } ?: return null
        val sameNumber = candidates.filter { numberKey(it.number) == number }
        // Exact name first ("Pikachu" = "Pikachu"), then a close one
        // (the camera read "Charizard" but the card is "Charizard ex").
        return sameNumber.firstOrNull { simplify(it.name) == name }
            ?: sameNumber.firstOrNull { sameApartFromSuffix(simplify(it.name), name) }
    }

    /**
     * The cards whose name matches what was read (exactly, or apart from an
     * ending like "ex"). The picture then only has to choose between these.
     */
    fun sameName(readName: String, candidates: List<Card>): List<Card> {
        val name = simplify(readName).takeIf { it.length >= 3 } ?: return emptyList()
        return candidates.filter { simplify(it.name).let { n -> n == name || sameApartFromSuffix(n, name) } }
    }

    // Endings printed after a Pokémon's name, like "Charizard ex" or "Pikachu VMAX".
    // Only these count as "close", so "Mew" and "Mewtwo" never get mixed up.
    private val suffixes = setOf("ex", "gx", "v", "vmax", "vstar", "vunion", "break", "lvx", "prime", "star")

    /** True when one name is the other plus a known ending ("charizard" / "charizardex"). */
    fun sameApartFromSuffix(a: String, b: String): Boolean {
        val (short, long) = if (a.length <= b.length) a to b else b to a
        return long.startsWith(short) && long.removePrefix(short) in suffixes
    }

    /** "025" and "25" are the same number; letters like "TG" stay. */
    fun numberKey(number: String): String = number.uppercase().trimStart('0').ifEmpty { "0" }

    /** "Pokémon Farfetch'd" -> "pokemonfarfetchd": ignores accents, capitals and spaces. */
    fun simplify(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .filter { it.isLetterOrDigit() }
}
