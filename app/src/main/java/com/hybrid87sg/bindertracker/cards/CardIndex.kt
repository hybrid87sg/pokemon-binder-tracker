package com.hybrid87sg.bindertracker.cards

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The list of every Pokémon card, kept on the phone so searching is
 * instant and works without internet.
 *
 * [download] fetches the list from TCGdex (a few MB, takes about a minute).
 * After that, [search] answers what people type in the search box, and
 * [lookup] answers the camera scanner ("what card is number 049/202?").
 */
class CardIndex private constructor(context: Context) {

    private val db = CardIndexDatabase.get(context)
    private val dao = db.cardDao()
    private val prefs = context.applicationContext.getSharedPreferences("card-index", Context.MODE_PRIVATE)

    /** How many cards are on the phone. 0 = not downloaded yet. */
    val cardCount: Flow<Int> = dao.observeCount()

    /** When the list was last downloaded (milliseconds since 1970), or null if never. */
    val lastUpdated: Long?
        get() = prefs.getLong(KEY_UPDATED, 0L).takeIf { it > 0 }

    /**
     * Free-text search, e.g. "pikachu", "charizard base", "049/202" or
     * "pikachu 49". Returns at most [limit] cards, best matches first.
     */
    suspend fun search(text: String, limit: Int = SEARCH_LIMIT): List<Card> {
        val query = CardQuery.parse(text)
        if (query.isEmpty) return emptyList()
        val (sql, args) = query.toSql(limit)
        return dao.search(SimpleSQLiteQuery(sql, args.toTypedArray()))
    }

    /**
     * For the camera scanner: finds cards from what was read off a card.
     * [number] can be "049", "49" or "049/202". [setTotal] is the 202 part.
     * Any of them may be null. Best matches first, at most 20.
     */
    suspend fun lookup(name: String?, number: String?, setTotal: Int?): List<Card> {
        val total = setTotal ?: number?.substringAfter('/', "")?.filter { it.isDigit() }?.toIntOrNull()
        val key = number?.substringBefore('/')?.let { CardText.numberKey(it) }?.takeIf { it.isNotEmpty() }
        var candidates = if (key != null) dao.byNumber(key) else emptyList()
        if (candidates.isEmpty() && !name.isNullOrBlank()) candidates = search(name, limit = 200)
        return ScanMatcher.rank(candidates, name, total).take(20)
    }

    suspend fun card(id: String): Card? = dao.byId(id)

    /**
     * Downloads the whole card list from TCGdex and replaces the one on the
     * phone. [onProgress] gets a number from 0.0 to 1.0 as it goes.
     * Throws an exception if the internet connection fails; the old list
     * is kept in that case.
     */
    suspend fun download(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        onProgress(0f)
        // 1. Every series (Base, Neo, ..., Scarlet & Violet), then the sets in each.
        val seriesIds = TcgdexParser.seriesIds(fetch("$API/series")).filter { it !in SKIPPED_SERIES }
        val sets = LinkedHashMap<String, CardSet>()
        seriesIds.forEachIndexed { i, seriesId ->
            val url = "$API/series/" + URLEncoder.encode(seriesId, "UTF-8")
            TcgdexParser.setsInSeries(fetch(url)).forEach { set ->
                sets[set.id] = set.copy(order = sets.size)
            }
            onProgress(0.6f * (i + 1) / seriesIds.size)
        }
        // 2. Every card in one go (the biggest download).
        val cards = TcgdexParser.cards(fetch("$API/cards"), sets)
        onProgress(0.85f)
        if (cards.isEmpty()) throw IOException("TCGdex sent an empty card list")
        // 3. Swap the old list for the new one in one step ("transaction"),
        //    so a search never sees a half-saved list.
        db.withTransaction {
            dao.deleteAll()
            dao.insertAll(cards)
        }
        prefs.edit { putLong(KEY_UPDATED, System.currentTimeMillis()) }
        onProgress(1f)
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("TCGdex replied with code $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val SEARCH_LIMIT = 60
        private const val API = "https://api.tcgdex.net/v2/en"
        private const val KEY_UPDATED = "updatedAt"

        // "tcgp" is Pokémon TCG Pocket, the phone game. Its cards aren't
        // printed, so they can't be in a binder.
        private val SKIPPED_SERIES = setOf("tcgp")

        @Volatile
        private var instance: CardIndex? = null

        /** The one shared card index. Use this from anywhere in the app. */
        fun get(context: Context): CardIndex =
            instance ?: synchronized(this) {
                instance ?: CardIndex(context.applicationContext).also { instance = it }
            }
    }
}
