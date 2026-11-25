package com.hybrid87sg.bindertracker.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import com.hybrid87sg.bindertracker.tcgdex.CardDetails
import com.hybrid87sg.bindertracker.tcgdex.MarketPrice
import com.hybrid87sg.bindertracker.tcgdex.Tcgdex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The extra details the binder page's Filter needs that the card list on the
 * phone doesn't have: who drew the card, what kind of card it is, its energy
 * types and a market price.
 *
 * In plain words: the first time you open Filter, the app asks TCGdex about
 * each card in the binder once (a few at a time) and writes the answers to
 * card-facts.json in the app's own storage. After that, Filter works with no
 * internet. Prices are asked for again after a week.
 */
data class CardFact(
    val artist: String?,
    // "Pokemon", "Trainer" or "Energy" (null = unknown).
    val category: String?,
    val types: List<String>,
    // A typical market price and its currency ("USD" or "EUR"); null = no price.
    val price: Double?,
    val currency: String?,
    val fetchedAt: Long,
)

object CardFacts {
    private const val FILE = "card-facts.json"
    private const val STALE_AFTER_MS = 7L * 24 * 60 * 60 * 1000

    /** Every card's facts we know, by card ID. The screen redraws when this changes. */
    val known = mutableStateMapOf<String, CardFact>()

    /** How many cards are still being looked up right now. */
    var loading by mutableIntStateOf(0)
        private set

    private var loaded = false
    private val lock = Mutex()

    /** Reads the saved facts (once). */
    suspend fun load(context: Context) {
        if (loaded) return
        val saved = withContext(Dispatchers.IO) {
            runCatching { decode(File(context.filesDir, FILE).readText()) }.getOrDefault(emptyMap())
        }
        saved.forEach { (id, f) -> if (id !in known) known[id] = f }
        loaded = true
    }

    /** Looks up the cards we don't know yet (or whose price is old), then saves. */
    suspend fun fetch(context: Context, ids: Collection<String>) = lock.withLock {
        load(context)
        val now = System.currentTimeMillis()
        val todo = ids.filter { id -> known[id]?.let { now - it.fetchedAt > STALE_AFTER_MS } ?: true }.distinct()
        if (todo.isEmpty()) return@withLock
        loading = todo.size
        val gate = Semaphore(4)
        var changed = false
        try { coroutineScope {
            todo.chunked(12).forEach { chunk ->
                val found = chunk.map { id ->
                    async(Dispatchers.IO) { gate.withPermit { runCatching { id to factOf(Tcgdex.cardDetails(id), now) }.getOrNull() } }
                }.awaitAll().filterNotNull()
                found.forEach { (id, f) -> known[id] = f }
                if (found.isNotEmpty()) changed = true
                loading = (loading - chunk.size).coerceAtLeast(0)
            }
        } } finally {
            // (Also when the screen closes half way: what was found so far is kept.)
            loading = 0
            if (changed) {
                val json = encode(known.toMap())
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { runCatching { File(context.filesDir, FILE).writeText(json) } }
            }
        }
    }

    fun factOf(d: CardDetails, now: Long): CardFact {
        val price = typicalPrice(d.prices)
        return CardFact(d.illustrator, d.category, d.types, price?.amount, price?.currency, now)
    }

    /** The one price Filter uses: TCGplayer's (normal, then any), else Cardmarket's. */
    fun typicalPrice(prices: List<MarketPrice>): MarketPrice? {
        val tcg = prices.filter { it.source == "TCGplayer" }
        return tcg.firstOrNull { it.variant == "Normal" } ?: tcg.firstOrNull() ?: prices.firstOrNull()
    }

    fun encode(facts: Map<String, CardFact>): String {
        val o = JSONObject()
        facts.forEach { (id, f) ->
            o.put(id, JSONObject().apply {
                f.artist?.let { put("a", it) }
                f.category?.let { put("c", it) }
                put("t", JSONArray(f.types))
                f.price?.let { put("p", it) }
                f.currency?.let { put("u", it) }
                put("at", f.fetchedAt)
            })
        }
        return o.toString()
    }

    fun decode(json: String): Map<String, CardFact> {
        val o = JSONObject(json)
        return o.keys().asSequence().associateWith { id ->
            val f = o.getJSONObject(id)
            val t = f.optJSONArray("t")
            CardFact(
                artist = f.optString("a").ifEmpty { null },
                category = f.optString("c").ifEmpty { null },
                types = if (t == null) emptyList() else (0 until t.length()).map { t.getString(it) },
                price = if (f.has("p")) f.getDouble("p") else null,
                currency = f.optString("u").ifEmpty { null },
                fetchedAt = f.optLong("at", 0L),
            )
        }
    }
}
