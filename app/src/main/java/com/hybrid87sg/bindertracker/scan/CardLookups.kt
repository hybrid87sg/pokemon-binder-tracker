package com.hybrid87sg.bindertracker.scan

import android.content.Context
import com.hybrid87sg.bindertracker.cards.Card
import com.hybrid87sg.bindertracker.cards.CardIndex

/**
 * The one place that decides which card index the scanner asks.
 *
 * This is an "adapter": the scanner speaks [CardLookup], the card index
 * speaks [CardIndex], and this class translates between them, like a travel
 * plug adapter between two kinds of socket.
 */
object CardLookups {
    fun create(context: Context): CardLookup = IndexLookup(CardIndex.get(context))

    private class IndexLookup(private val index: CardIndex) : CardLookup {
        override suspend fun find(name: String?, number: String?, setTotal: Int?): List<CardCandidate> =
            index.lookup(name, number, setTotal).map(::toCandidate)

        override suspend fun search(query: String): List<CardCandidate> =
            index.search(query).map(::toCandidate)

        private fun toCandidate(card: Card) = CardCandidate(
            cardId = card.id,
            name = card.name,
            setName = card.setName,
            number = card.numberLabel,
            imageUrl = card.smallImageUrl,
        )
    }
}
