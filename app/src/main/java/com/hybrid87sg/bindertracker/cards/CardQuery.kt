package com.hybrid87sg.bindertracker.cards

/**
 * What someone typed in the search box, split into parts.
 *
 * "pikachu 049/202" becomes words = ["pikachu"], numberKey = "49", setTotal = 202.
 * Words can match the card's name or its set's name, so "charizard base"
 * finds Charizard from the Base set.
 */
data class CardQuery(
    val words: List<String>,
    val numberKey: String?,
    val setTotal: Int?,
) {
    val isEmpty: Boolean get() = words.isEmpty() && numberKey == null

    /**
     * Turns the query into a database question (SQL) plus the values that
     * fill its "?" blanks. Filling blanks, instead of pasting the typed
     * text into the SQL, keeps odd characters from breaking the question.
     */
    fun toSql(limit: Int): Pair<String, List<Any>> {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()
        for (word in words) {
            where += "(searchName LIKE ? OR searchSet LIKE ?)"
            args += "%$word%"
            args += "%$word%"
        }
        if (numberKey != null) {
            where += "numberKey = ?"
            args += numberKey
        }

        // Best matches first: the right set size, then the exact name, then
        // names that start with the words, then newest sets.
        val order = mutableListOf<String>()
        if (setTotal != null) {
            order += "(setTotal = ?) DESC"
            args += setTotal
        }
        val phrase = words.joinToString(" ")
        if (phrase.isNotEmpty()) {
            order += "(searchName = ?) DESC"
            args += phrase
            order += "(searchName LIKE ?) DESC"
            args += "$phrase%"
        }
        order += "setOrder DESC"
        order += "length(number)"
        order += "number"

        val whereSql = if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")
        val sql = "SELECT * FROM cards$whereSql ORDER BY ${order.joinToString(", ")} LIMIT $limit"
        return sql to args
    }

    companion object {
        // "049/202", "TG05/TG30", "#25/102"
        private val numberWithTotal = Regex("^#?([a-z]*\\d+[a-z]*)/[a-z]*(\\d+)$")
        // "25", "#025", "tg05", "swsh050"
        private val numberAlone = Regex("^#?(\\d+|[a-z]{1,4}\\d+)$")
        private val spaces = Regex("\\s+")

        fun parse(text: String): CardQuery {
            var numberKey: String? = null
            var setTotal: Int? = null
            val words = mutableListOf<String>()
            for (part in text.lowercase().split(spaces)) {
                if (part.isBlank()) continue
                if (numberKey == null) {
                    val withTotal = numberWithTotal.matchEntire(part)
                    if (withTotal != null) {
                        numberKey = CardText.numberKey(withTotal.groupValues[1])
                        setTotal = withTotal.groupValues[2].toIntOrNull()
                        continue
                    }
                    if (numberAlone.matches(part)) {
                        numberKey = CardText.numberKey(part.removePrefix("#"))
                        continue
                    }
                }
                words += CardText.normalize(part).split(" ").filter { it.isNotBlank() }
            }
            return CardQuery(words, numberKey, setTotal)
        }
    }
}

/**
 * Puts scan results in order: a card whose number, set size and name all
 * match the photo comes first.
 */
object ScanMatcher {
    fun rank(candidates: List<Card>, name: String?, setTotal: Int?): List<Card> {
        val wanted = name?.let { CardText.normalize(it) }.orEmpty()
        return candidates.sortedWith(
            compareByDescending<Card> { score(it, wanted, setTotal) }.thenByDescending { it.setOrder },
        )
    }

    /** Points for how well a card fits: set size +4, name +1 to +3. */
    fun score(card: Card, wantedName: String, setTotal: Int?): Int {
        var points = 0
        if (setTotal != null && card.setTotal == setTotal) points += 4
        if (wantedName.isNotEmpty() && card.searchName.isNotEmpty()) {
            points += when {
                card.searchName == wantedName -> 3
                card.searchName.contains(wantedName) || wantedName.contains(card.searchName) -> 2
                card.searchName.split(" ").any { it.length > 2 && wantedName.contains(it) } -> 1
                else -> 0
            }
        }
        return points
    }
}
