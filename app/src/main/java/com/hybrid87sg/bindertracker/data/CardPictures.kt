package com.hybrid87sg.bindertracker.data

/**
 * A backup place to get card pictures from.
 *
 * TCGdex (where the app gets its cards) has no picture for some cards, mostly
 * older Sun & Moon era cards and promos, like Solgaleo GX. When that happens
 * the app tries pokemontcg.io instead, another free card-picture site.
 *
 * Both sites name cards "<set>-<number>" ("sm1-89" is Sun & Moon #89), and
 * mostly use the same set codes. The few that differ are translated below.
 * If a guess is wrong the picture just fails to load and the blank card shows,
 * same as before.
 */
object CardPictures {
    private const val BASE = "https://images.pokemontcg.io"

    /** Backup picture for a TCGdex card id, e.g. "sm1-89". Null if it can't be worked out. */
    fun backupUrl(cardId: String?, large: Boolean): String? {
        if (cardId.isNullOrBlank()) return null
        val dash = cardId.lastIndexOf('-')
        if (dash <= 0 || dash == cardId.length - 1) return null
        val set = backupSetCode(cardId.substring(0, dash))
        val number = cardId.substring(dash + 1).let { n -> if (n.all(Char::isDigit)) n.trimStart('0').ifEmpty { "0" } else n }
        return "$BASE/$set/$number${if (large) "_hires" else ""}.png"
    }

    /** TCGdex set code -> pokemontcg.io set code. */
    fun backupSetCode(tcgdexSet: String): String {
        // Scarlet & Violet: "sv01" -> "sv1", "sv03.5" -> "sv3pt5".
        Regex("""sv0*(\d+)(\.5)?""").matchEntire(tcgdexSet)?.let { m ->
            return "sv${m.groupValues[1]}${if (m.groupValues[2].isNotEmpty()) "pt5" else ""}"
        }
        if (tcgdexSet == "swsh12.5") return "swsh12pt5"
        // Half sets like "sm3.5" or "swsh4.5" -> "sm35", "swsh45".
        return tcgdexSet.replace(".", "")
    }
}
