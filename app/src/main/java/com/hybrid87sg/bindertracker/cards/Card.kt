package com.hybrid87sg.bindertracker.cards

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.Normalizer

/**
 * One Pokémon card from the TCGdex card database, for example
 * "Pikachu, Sword & Shield, 049/202".
 *
 * These live in their own database file (see [CardIndexDatabase]), apart
 * from your binders, because the whole list can be downloaded again at any
 * time. Each property below becomes a column in the "cards" table.
 */
@Entity(tableName = "cards", indices = [Index("numberKey"), Index("searchName")])
data class Card(
    // TCGdex's ID for the card: the set's ID, a dash, then the number.
    // For example "swsh1-49".
    @PrimaryKey val id: String,
    val name: String,
    // The number printed on the card, before the slash: "049", "TG05"...
    val number: String,
    val setId: String,
    val setName: String,
    // The number printed after the slash, e.g. the 202 in "049/202".
    // Null for sets that don't print one (like most promo cards).
    val setTotal: Int?,
    // Bigger = newer set. Used to list newer cards first.
    val setOrder: Int,
    // Start of the picture's web address. Add "/low.webp" or "/high.webp".
    // Null when TCGdex has no picture of this card yet.
    val imageBase: String?,
    // The three below are "search helpers": simplified copies of the text
    // so that "pokemon" finds "Pokémon" and "025" finds "25".
    val numberKey: String = CardText.numberKey(number),
    val searchName: String = CardText.normalize(name),
    val searchSet: String = CardText.normalize(setName),
) {
    /** A small picture, good for lists. */
    val smallImageUrl: String? get() = imageBase?.let { "$it/low.webp" }

    /** A big, sharp picture, good for a full-screen view. */
    val largeImageUrl: String? get() = imageBase?.let { "$it/high.webp" }

    /** The number as printed on the card, e.g. "049/202". */
    val numberLabel: String get() = if (setTotal != null) "$number/$setTotal" else number
}

/** Helpers that simplify text so searching ignores accents, capitals and zeros. */
object CardText {
    private val accentMarks = Regex("\\p{Mn}+")
    private val dropped = Regex("['’.]")
    private val notLetterOrDigit = Regex("[^a-z0-9]+")
    private val numberParts = Regex("^([a-z]*)0*(\\d+)(.*)$")

    /** "Pokémon Farfetch'd" -> "pokemon farfetchd". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(accentMarks, "")
            .lowercase()
            .replace(dropped, "")
            .replace(notLetterOrDigit, " ")
            .trim()

    /** "025" -> "25", "TG05" -> "tg5", "SWSH050" -> "swsh50". */
    fun numberKey(number: String): String {
        val simple = number.lowercase().filter { it.isLetterOrDigit() }
        val parts = numberParts.matchEntire(simple) ?: return simple
        return parts.groupValues[1] + parts.groupValues[2] + parts.groupValues[3]
    }
}
