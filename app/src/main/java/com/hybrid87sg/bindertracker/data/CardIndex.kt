package com.hybrid87sg.bindertracker.data

import android.content.Context
import androidx.core.content.edit
import com.hybrid87sg.bindertracker.tcgdex.Tcgdex

/**
 * The searchable list of all cards, kept on the phone.
 *
 * It's downloaded from TCGdex the first time the app runs, then refreshed
 * weekly (new sets come out every few months). Searching never needs the
 * internet once it's downloaded.
 */
class CardIndex(context: Context, private val dao: CardDao) {
    private val prefs = context.getSharedPreferences("card_index", Context.MODE_PRIVATE)

    val countFlow = dao.observeCount()

    /** When the list was last downloaded (milliseconds), or 0 if never. */
    val lastUpdated: Long get() = prefs.getLong(KEY_UPDATED, 0L)

    suspend fun needsRefresh(): Boolean =
        dao.count() == 0 || System.currentTimeMillis() - lastUpdated > REFRESH_AFTER_MS ||
            // Downloaded before series were stored: get it again so the series filter works.
            prefs.getInt(KEY_FORMAT, 1) < FORMAT

    /** Downloads the latest list and replaces the old one. */
    suspend fun refresh() {
        val cards = Tcgdex.downloadCardIndex()
        dao.replaceAll(cards)
        prefs.edit { putLong(KEY_UPDATED, System.currentTimeMillis()); putInt(KEY_FORMAT, FORMAT) }
    }

    /**
     * Finds cards by name, number, or both. Examples:
     * "pikachu", "025/165", "#25", "charizard 4".
     */
    suspend fun search(query: String, seriesId: String = ""): List<Card> {
        val parsed = parseQuery(query) ?: return emptyList()
        return if (parsed.number != null) {
            dao.searchByNumber(parsed.number, parsed.name, seriesId = seriesId)
        } else {
            dao.searchByName(parsed.name, seriesId = seriesId)
        }
    }

    /** Every series, newest first, for the series filter. */
    suspend fun series(): List<SeriesOption> = dao.series()

    suspend fun get(id: String): Card? = dao.get(id)

    companion object {
        private const val KEY_UPDATED = "updated_at"
        private const val KEY_FORMAT = "format"
        // 2 = cards include their series.
        private const val FORMAT = 2
        private const val REFRESH_AFTER_MS = 7L * 24 * 60 * 60 * 1000 // one week

        /** What the person typed, split into a name part and a card number. */
        data class Query(val name: String, val number: String?)

        /**
         * "Pikachu 025/165" -> name "Pikachu", number "025".
         * The part after "/" is the set size, which we don't need.
         */
        fun parseQuery(text: String): Query? {
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            var number: String? = null
            val nameWords = mutableListOf<String>()
            for (word in words) {
                val cleaned = word.removePrefix("#").substringBefore("/")
                if (number == null && cleaned.isNotEmpty() && cleaned.any { it.isDigit() } &&
                    cleaned.all { it.isLetterOrDigit() } && cleaned.first().isDigit()
                ) {
                    number = cleaned
                } else {
                    nameWords += word
                }
            }
            return Query(name = nameWords.joinToString(" "), number = number)
        }
    }
}
