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
            ?: sameNumber.firstOrNull { simplify(it.name).let { c -> c.startsWith(name) || name.startsWith(c) } }
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
