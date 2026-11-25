package com.hybrid87sg.bindertracker.ui.screens

import com.hybrid87sg.bindertracker.data.CardFact

/**
 * What the Filter on the binder page can sort by, and how ticks combine.
 * Kept free of screen code so tests can check it.
 *
 * The rule: inside one list, any tick counts (Pikachu or Charizard). Across
 * lists, everything must match (Pikachu AND holo). Quick picks work like
 * separate lists, except Collected + Missing together simply means both.
 */
object FilterLogic {

    /** The quick picks at the top of the menu. */
    enum class Quick(val label: String) {
        Holo("✦ Holo & shiny"),
        Collected("Collected"),
        Missing("Missing"),
        Duplicates("Duplicates"),
    }

    /** The lists you can tick several things in. */
    enum class Section(val title: String, val searchable: Boolean = false) {
        Pokemon("Pokémon", searchable = true),
        Artist("Artist", searchable = true),
        Rarity("Rarity"),
        Type("Type"),
        Kind("Card kind"),
        Expansion("Expansion"),
        Price("Price"),
    }

    /** One card the binder shows: a card in a pocket, or a grey missing card in a free pocket. */
    data class Item(
        val pageId: String,
        val position: Int,
        val cardId: String,
        val name: String,
        val rarity: String,
        val setName: String,
        val owned: Boolean,
    )

    /** What's ticked. Saved as a short list of words so it survives the phone unfolding. */
    data class Choice(
        val quick: Set<Quick> = emptySet(),
        val picks: Map<Section, Set<String>> = emptyMap(),
    ) {
        val count: Int get() = quick.size + picks.values.sumOf { it.size }
        val active: Boolean get() = count > 0
        fun picked(s: Section): Set<String> = picks[s].orEmpty()
        fun toggle(q: Quick) = copy(quick = if (q in quick) quick - q else quick + q)
        fun toggle(s: Section, v: String): Choice {
            val now = picked(s).let { if (v in it) it - v else it + v }
            return copy(picks = if (now.isEmpty()) picks - s else picks + (s to now))
        }

        fun save(): List<String> = quick.map { "q:${it.name}" } + picks.flatMap { (s, vs) -> vs.map { "s:${s.name}:$it" } }

        companion object {
            fun restore(saved: List<String>): Choice = saved.fold(Choice()) { c, w ->
                val parts = w.split(":", limit = 3)
                when {
                    parts[0] == "q" && parts.size == 2 -> Quick.entries.firstOrNull { it.name == parts[1] }?.let { c.toggle(it) } ?: c
                    parts[0] == "s" && parts.size == 3 -> Section.entries.firstOrNull { it.name == parts[1] }?.let { c.toggle(it, parts[2]) } ?: c
                    else -> c
                }
            }
        }
    }

    /** The energy types in the order printed on cards, with their colours (ARGB) and letters. */
    val Types: List<Triple<String, Long, String>> = listOf(
        Triple("Grass", 0xFF4FA84A, "G"),
        Triple("Fire", 0xFFE5532D, "R"),
        Triple("Water", 0xFF3A8EE0, "W"),
        Triple("Lightning", 0xFFE8B91E, "L"),
        Triple("Psychic", 0xFFA55BC9, "P"),
        Triple("Fighting", 0xFFB96A33, "F"),
        Triple("Darkness", 0xFF2F3A48, "D"),
        Triple("Metal", 0xFF8A97A6, "M"),
        Triple("Fairy", 0xFFE07AB3, "Y"),
        Triple("Dragon", 0xFFB39A2C, "N"),
        Triple("Colorless", 0xFFB9B2A6, "C"),
    )

    val PriceBands = listOf("Under S$5", "S$5 – 20", "S$20 – 50", "S$50 and up")
    fun priceBand(sgd: Double): String = when {
        sgd < 5 -> PriceBands[0]
        sgd < 20 -> PriceBands[1]
        sgd < 50 -> PriceBands[2]
        else -> PriceBands[3]
    }

    private val Suffix = Regex("""\s+(ex|EX|GX|V|VMAX|VSTAR|V-UNION|BREAK|LV\.X|Prime|LEGEND|δ|☆|◇|Star)$""")

    /** "Charizard ex" -> "Charizard", so every Charizard is one tick. */
    fun pokemonName(name: String): String = Suffix.replace(name.trim(), "").trim().ifEmpty { name }

    fun kindLabel(category: String?): String? = when (category?.lowercase()) {
        "pokemon" -> "Pokémon"
        "trainer" -> "Trainer"
        "energy" -> "Energy"
        else -> null
    }

    /**
     * The values a card has in a list (a card can have two types). Empty =
     * it has none, or we don't know yet. [sgd] turns a fact's price into S$.
     */
    fun values(s: Section, item: Item, fact: CardFact?, sgd: (CardFact) -> Double?): List<String> = when (s) {
        // Until we know a card's kind, its name is listed (most binder cards are Pokémon).
        Section.Pokemon -> if (fact?.category == null || fact.category.equals("Pokemon", true)) listOf(pokemonName(item.name)) else emptyList()
        Section.Artist -> listOfNotNull(fact?.artist)
        Section.Rarity -> listOfNotNull(item.rarity.ifBlank { null }?.takeUnless { it.equals("None", true) })
        Section.Type -> fact?.types.orEmpty()
        Section.Kind -> listOfNotNull(kindLabel(fact?.category))
        Section.Expansion -> listOfNotNull(item.setName.ifBlank { null })
        Section.Price -> listOfNotNull(fact?.let(sgd)?.let { priceBand(it) })
    }

    fun matches(
        choice: Choice,
        item: Item,
        fact: CardFact?,
        duplicates: Set<String>,
        isShiny: (String) -> Boolean,
        sgd: (CardFact) -> Double?,
    ): Boolean {
        val q = choice.quick
        val own = Quick.Collected in q
        val miss = Quick.Missing in q
        if (own != miss && (if (own) !item.owned else item.owned)) return false
        if (Quick.Holo in q && !isShiny(item.rarity)) return false
        if (Quick.Duplicates in q && (!item.owned || item.cardId !in duplicates)) return false
        for ((s, picked) in choice.picks) {
            if (picked.isEmpty()) continue
            if (values(s, item, fact, sgd).none { it in picked }) return false
        }
        return true
    }

    /** Cards that are in the binder more than once. */
    fun duplicates(items: List<Item>): Set<String> =
        items.filter { it.owned }.groupingBy { it.cardId }.eachCount().filterValues { it > 1 }.keys

    /** Each list's choices with how many cards have them, in a sensible order. */
    fun options(s: Section, items: List<Item>, facts: Map<String, CardFact>, sgd: (CardFact) -> Double?, rarityRank: (String) -> Int): List<Pair<String, Int>> {
        val counts = linkedMapOf<String, Int>()
        items.forEach { item -> values(s, item, facts[item.cardId], sgd).distinct().forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        val list = counts.toList()
        return when (s) {
            Section.Rarity -> list.sortedBy { rarityRank(it.first) }
            Section.Type -> list.sortedBy { (t, _) -> Types.indexOfFirst { it.first == t }.let { if (it < 0) 99 else it } }
            Section.Kind -> list.sortedBy { listOf("Pokémon", "Trainer", "Energy").indexOf(it.first) }
            Section.Price -> list.sortedBy { PriceBands.indexOf(it.first) }
            else -> list.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
        }
    }
}
