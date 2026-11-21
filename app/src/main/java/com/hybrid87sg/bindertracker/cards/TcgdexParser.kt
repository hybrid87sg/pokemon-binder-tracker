package com.hybrid87sg.bindertracker.cards

import org.json.JSONArray
import org.json.JSONObject

/** One card set, e.g. "Sword & Shield" with 202 numbered cards. */
data class CardSet(val id: String, val name: String, val officialCount: Int?, val order: Int = 0)

/**
 * Reads the text TCGdex sends back (JSON) and turns it into [CardSet]s and
 * [Card]s. Kept apart from the downloading so it can be tested without
 * the internet.
 *
 * TCGdex is a free, open Pokémon card database: https://tcgdex.dev
 */
object TcgdexParser {

    /** From /series: the IDs of every series, e.g. ["base", "gym", ..., "sv"]. */
    fun seriesIds(text: String): List<String> {
        val array = JSONArray(text)
        return (0 until array.length()).map { array.getJSONObject(it).getString("id") }
    }

    /** From /series/{id}: the sets in that series, oldest first. */
    fun setsInSeries(text: String): List<CardSet> {
        val sets = JSONObject(text).optJSONArray("sets") ?: return emptyList()
        return (0 until sets.length()).map { i ->
            val set = sets.getJSONObject(i)
            val official = set.optJSONObject("cardCount")?.optInt("official", 0) ?: 0
            CardSet(
                id = set.getString("id"),
                name = set.getString("name"),
                officialCount = official.takeIf { it > 0 },
            )
        }
    }

    /**
     * From /cards: every card, as {"id":"swsh1-49","localId":"049","name":"Pikachu","image":"https://..."}.
     * The set is found from the start of the card's ID. Cards from sets not
     * in [sets] (like the phone-only Pocket game) are left out.
     */
    fun cards(text: String, sets: Map<String, CardSet>): List<Card> {
        val array = JSONArray(text)
        val result = ArrayList<Card>(array.length())
        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            val id = json.getString("id")
            val set = findSet(id, sets) ?: continue
            val number = json.optString("localId").ifEmpty { id.removePrefix(set.id + "-") }
            val image = json.optString("image").takeIf { it.startsWith("http") }
            result += Card(
                id = id,
                name = json.getString("name"),
                number = number,
                setId = set.id,
                setName = set.name,
                setTotal = set.officialCount,
                setOrder = set.order,
                imageBase = image,
            )
        }
        return result
    }

    private fun findSet(cardId: String, sets: Map<String, CardSet>): CardSet? =
        sets[cardId.substringBeforeLast('-')]
            ?: sets.values.filter { cardId.startsWith(it.id + "-") }.maxByOrNull { it.id.length }
}
