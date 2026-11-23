package com.hybrid87sg.bindertracker.data

/**
 * The maths behind the Missing cards page, kept apart from the screen so
 * tests can check it.
 *
 * A binder "collects" one or more expansions. Every card in those
 * expansions is either in the binder somewhere (owned) or missing.
 */
object Checklist {
    /** One card on the checklist. */
    data class Entry(val card: Card, val owned: Boolean, val secret: Boolean)

    /** Where a missing card should go: page [pageNumber] (1 = first), pocket [position] (0 = first). */
    data class Spot(val pageId: String, val pageNumber: Int, val position: Int, val after: Card?)

    /**
     * True for a secret rare: a card numbered past the set's size, like
     * 180/165, or one with letters in its number (e.g. "TG05" gallery cards).
     * Sets with an unknown size have no secret rares.
     */
    fun isSecret(card: Card): Boolean {
        if (card.setOfficial <= 0) return false
        val number = card.number
        val digits = number.toIntOrNull() ?: return true
        return digits > card.setOfficial
    }

    /** Sorts card numbers like a person would: 2 before 10, then numbers with letters (TG01...) after. */
    val numberOrder: Comparator<Card> = compareBy<Card>({ it.number.toIntOrNull() == null }, { it.number.toIntOrNull() ?: 0 }, { it.number })

    /**
     * Every card of the [followed] expansions, in that order, each sorted by
     * number. [owned] = the card IDs in the binder. Secret rares are left out
     * unless [countSecret] ("master set").
     */
    fun entries(cards: List<Card>, followed: List<String>, owned: Set<String>, countSecret: Boolean): List<Entry> {
        val bySet = cards.groupBy { it.setId }
        return followed.flatMap { setId ->
            bySet[setId].orEmpty().sortedWith(numberOrder).map { Entry(it, it.id in owned, isSecret(it)) }
        }.filter { countSecret || !it.secret }
    }

    /** Rarities from most common to rarest, so groups are listed in a sensible order. Unknown ones go last. */
    private val RARITY_ORDER = listOf(
        "common", "uncommon", "rare", "rare holo", "holo rare", "double rare", "rare holo ex", "rare holo gx",
        "rare holo v", "rare holo vmax", "rare holo vstar", "ultra rare", "illustration rare",
        "special illustration rare", "rare secret", "secret rare", "hyper rare", "rare rainbow", "shiny rare",
        "shiny ultra rare", "ace spec rare", "amazing rare", "radiant rare", "rare prime", "legend", "promo",
    )

    fun rarityRank(rarity: String): Int = RARITY_ORDER.indexOf(rarity.lowercase()).let { if (it < 0) RARITY_ORDER.size else it }

    /** The rarity groups closest to finished: (rarity, cards left), fewest left first, up to [max]. */
    fun almostThere(entries: List<Entry>, maxLeft: Int = 6, max: Int = 4): List<Pair<String, Int>> =
        entries.filter { it.card.rarity.isNotEmpty() }
            .groupBy { it.card.rarity }
            .mapValues { (_, list) -> list.count { !it.owned } }
            .filter { it.value in 1..maxLeft }
            .toList()
            .sortedWith(compareBy({ it.second }, { rarityRank(it.first) }))
            .take(max)

    /** Rarity groups that are complete (every card owned). */
    fun completeRarities(entries: List<Entry>): List<String> =
        entries.filter { it.card.rarity.isNotEmpty() }.groupBy { it.card.rarity }
            .filter { (_, list) -> list.all { it.owned } }
            .keys.sortedBy(::rarityRank)

    /**
     * Which pocket a missing [card] should go in. If the binder has the card
     * just before it (by number, same expansion) and the pocket after that one
     * is free, it goes there, so the binder stays in number order. Otherwise
     * the first free pocket. Null when every pocket is full.
     */
    fun suggestSpot(card: Card, entries: List<Entry>, pockets: List<BinderPocket>): Spot? {
        if (pockets.isEmpty()) return null
        val pageNumbers = pockets.map { it.pageId }.distinct().withIndex().associate { it.value to it.index + 1 }
        fun spot(i: Int, after: Card?) = pockets[i].let { Spot(it.pageId, pageNumbers.getValue(it.pageId), it.position, after) }

        val sameSet = entries.filter { it.card.setId == card.setId }
        val index = sameSet.indexOfFirst { it.card.id == card.id }
        val before = (index - 1 downTo 0).map { sameSet[it] }.firstOrNull { it.owned }?.card
        if (before != null) {
            val at = pockets.indexOfFirst { it.cardId == before.id }
            if (at >= 0 && at + 1 < pockets.size && pockets[at + 1].cardId == null) return spot(at + 1, before)
        }
        val free = pockets.indexOfFirst { it.cardId == null }
        return if (free >= 0) spot(free, null) else null
    }

    /** The list as text to paste into a message, grouped by expansion. */
    fun copyText(binderName: String, missing: List<Entry>, setNames: Map<String, String>): String = buildString {
        append("Missing from my $binderName binder (${missing.size} ${if (missing.size == 1) "card" else "cards"})\n")
        missing.groupBy { it.card.setId }.forEach { (setId, list) ->
            append("\n").append(setNames[setId] ?: setId).append("\n")
            list.forEach { e ->
                val size = e.card.setOfficial.takeIf { it > 0 }?.let { "/$it" }.orEmpty()
                append("#${e.card.number}$size ${e.card.name}\n")
            }
        }
    }.trimEnd()

    /** 25, 50, 75 or 100 when going from [before] to [after] owned (out of [total]) passes that mark; else null. */
    fun milestone(before: Int, after: Int, total: Int): Int? {
        if (total <= 0 || after <= before) return null
        return listOf(100, 75, 50, 25).firstOrNull { mark -> before * 100 < mark * total && after * 100 >= mark * total }
    }
}

/** What the Missing cards page shows for one binder. [owned] = card IDs in its pockets. */
data class ChecklistData(
    val binder: Binder,
    val cards: List<Card>,
    val pockets: List<BinderPocket>,
    val owned: Set<String>,
)

/** How far along a collecting binder is: [missing] of [total] cards still to find. */
data class ChecklistProgress(val missing: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else (total - missing).toFloat() / total
}

/** One collecting binder on the Missing tab: the cards it still needs, and how far along it is. */
data class BinderMissing(val binder: Binder, val entries: List<Checklist.Entry>, val progress: ChecklistProgress)
