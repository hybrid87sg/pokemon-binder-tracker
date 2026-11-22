package com.hybrid87sg.bindertracker.data

/**
 * What a card search found. [cards] has the cards already in your binders
 * first, then the rest. [places] says where each of your cards is, by card
 * ID, e.g. "Kanto Binder · page 1". [moreOthers] is true when there were more
 * matches than we show.
 */
data class CardSearch(
    val cards: List<Card>,
    val places: Map<String, String> = emptyMap(),
    val moreOthers: Boolean = false,
)

/** Puts the cards you already have at the top of the search results. */
object OwnedFirst {
    /**
     * [found] is every match, in the usual order. Your cards keep that order
     * among themselves and come first; then up to [limit] other cards.
     */
    fun arrange(found: List<Card>, places: List<CardPlace>, limit: Int = 100): CardSearch {
        val where = places.groupBy { it.cardId }
        val (mine, others) = found.partition { it.id in where }
        val labels = mine.associate { card -> card.id to label(where.getValue(card.id)) }
        return CardSearch(mine + others.take(limit), labels, moreOthers = others.size > limit)
    }

    /** "Kanto Binder · page 1", or "Kanto Binder · page 1 + 2 more" if it's in several places. */
    fun label(places: List<CardPlace>): String {
        val distinct = places.distinctBy { it.binderName to it.pageNumber }
        val first = distinct.first()
        val text = "${first.binderName} · page ${first.pageNumber}"
        return if (distinct.size > 1) "$text + ${distinct.size - 1} more" else text
    }
}
