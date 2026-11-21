package com.hybrid87sg.bindertracker.scan

/**
 * A card the scanner thinks might be in a pocket.
 *
 * @param cardId the card's ID in the card index, e.g. "swsh3-136".
 * @param number the collector number as printed, e.g. "136/189".
 */
data class CardCandidate(
    val cardId: String,
    val name: String,
    val setName: String,
    val number: String,
    val imageUrl: String? = null,
)

/**
 * The scanner's way of asking the card index "which card is this?".
 *
 * The scanner only knows what it read on the card: the name, and the
 * collector number such as "025/198" (number "025", set total 198). The card
 * index answers with the most likely cards, best match first.
 *
 * Keeping this as a small "interface" (a promise of what a class can do,
 * without saying how) means the camera code doesn't need to know how the
 * card index stores its cards.
 */
interface CardLookup {
    /** Cards matching what was read on a card. Any of the three may be missing. */
    suspend fun find(name: String?, number: String?, setTotal: Int?): List<CardCandidate>

    /** Cards matching text the person typed, for fixing a wrong match by hand. */
    suspend fun search(query: String): List<CardCandidate>

    /** Used until a card index is available: finds nothing, so pockets keep the name read from the photo. */
    object None : CardLookup {
        override suspend fun find(name: String?, number: String?, setTotal: Int?) = emptyList<CardCandidate>()
        override suspend fun search(query: String) = emptyList<CardCandidate>()
    }
}
