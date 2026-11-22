package com.hybrid87sg.bindertracker.tcgdex

import com.hybrid87sg.bindertracker.data.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Talks to TCGdex (https://tcgdex.dev), a free, open card database.
 * No account or API key is needed.
 */
object Tcgdex {
    private const val BASE = "https://api.tcgdex.net/v2/en"

    /** Downloads every English card (about 24,000) with its set and series names. */
    suspend fun downloadCardIndex(): List<Card> = withContext(Dispatchers.IO) {
        val sets = parseSetNames(get("$BASE/sets"))
        // Which series each set belongs to: one small download per series (about 20).
        val seriesOfSet = mutableMapOf<String, SeriesInfo>()
        parseSeriesList(get("$BASE/series")).forEachIndexed { seriesIndex, (id, name) ->
            runCatching { parseSeriesSets(get("$BASE/series/${URLEncoder.encode(id, "UTF-8")}")) }
                .getOrDefault(emptyList())
                .forEachIndexed { setIndex, setId -> seriesOfSet[setId] = SeriesInfo(id, name, releaseOrder(seriesIndex, setIndex)) }
        }
        parseCardList(get("$BASE/cards"), sets, seriesOfSet)
    }

    /** A series, e.g. "swsh" / "Sword & Shield"; [order] counts up from the oldest (see [releaseOrder]). */
    data class SeriesInfo(val id: String, val name: String, val order: Int)

    /**
     * One number that sorts both series and the expansions inside them by release:
     * series 3, expansion 7 -> 3007. TCGdex lists both oldest first, so a bigger
     * number is newer. A series never has anywhere near 1,000 expansions.
     */
    fun releaseOrder(seriesIndex: Int, setIndex: Int): Int = seriesIndex * 1000 + setIndex

    /** [{"id":"base","name":"Base"},...] -> [("base", "Base"), ...] in TCGdex's order (oldest first). */
    fun parseSeriesList(json: String): List<Pair<String, String>> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i -> array.getJSONObject(i).let { it.getString("id") to it.getString("name") } }
    }

    /** {"id":"swsh","sets":[{"id":"swsh1"},...]} -> ["swsh1", ...] */
    fun parseSeriesSets(json: String): List<String> {
        val sets = JSONObject(json).optJSONArray("sets") ?: return emptyList()
        return (0 until sets.length()).map { sets.getJSONObject(it).getString("id") }
    }

    /** Full details for one card, including market prices. */
    suspend fun cardDetails(id: String): CardDetails = withContext(Dispatchers.IO) {
        parseCardDetails(get("$BASE/cards/${URLEncoder.encode(id, "UTF-8")}"))
    }

    // ---- Reading TCGdex's replies (kept separate so tests can check them) ----

    /** [{"id":"base1","name":"Base Set",...}] -> {"base1": "Base Set"} */
    fun parseSetNames(json: String): Map<String, String> {
        val array = JSONArray(json)
        return (0 until array.length()).associate { i ->
            val set = array.getJSONObject(i)
            set.getString("id") to set.getString("name")
        }
    }

    /** [{"id":"swsh3-136","localId":"136","name":"Furret","image":"https://..."}] -> cards */
    fun parseCardList(json: String, setNames: Map<String, String>, seriesOfSet: Map<String, SeriesInfo> = emptyMap()): List<Card> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val c = array.getJSONObject(i)
            val id = c.getString("id")
            val localId = c.getString("localId")
            // A card's id is always "<set id>-<number>", so the set id is what's before the number.
            val setId = id.removeSuffix("-$localId")
            Card(
                id = id,
                localId = localId,
                name = c.getString("name"),
                setId = setId,
                setName = setNames[setId] ?: setId,
                imageUrl = c.optString("image").ifEmpty { null },
                seriesId = seriesOfSet[setId]?.id.orEmpty(),
                seriesName = seriesOfSet[setId]?.name.orEmpty(),
                seriesOrder = seriesOfSet[setId]?.order ?: 0,
            )
        }
    }

    fun parseCardDetails(json: String): CardDetails {
        val c = JSONObject(json)
        val prices = mutableListOf<MarketPrice>()
        val pricing = c.optJSONObject("pricing")

        // TCGplayer (United States), in US dollars. One price per print variant.
        pricing?.optJSONObject("tcgplayer")?.let { tp ->
            val currency = tp.optString("unit", "USD")
            val updated = tp.optString("updated").ifEmpty { null }
            VARIANT_NAMES.forEach { (key, label) ->
                val variant = tp.optJSONObject(key) ?: return@forEach
                val market = variant.optDoubleOrNull("marketPrice") ?: variant.optDoubleOrNull("midPrice")
                if (market != null) {
                    prices += MarketPrice("TCGplayer", label, market, currency, updated)
                }
            }
        }

        // Cardmarket (Europe), in euros. "trend" is their market price.
        pricing?.optJSONObject("cardmarket")?.let { cm ->
            val currency = cm.optString("unit", "EUR")
            val updated = cm.optString("updated").ifEmpty { null }
            cm.optDoubleOrNull("trend")?.let { prices += MarketPrice("Cardmarket", "Normal", it, currency, updated) }
            cm.optDoubleOrNull("trend-holo")?.let { prices += MarketPrice("Cardmarket", "Holo", it, currency, updated) }
        }

        return CardDetails(
            id = c.getString("id"),
            name = c.getString("name"),
            rarity = c.optString("rarity").ifEmpty { null },
            setName = c.optJSONObject("set")?.optString("name"),
            illustrator = c.optString("illustrator").ifEmpty { null },
            prices = prices,
        )
    }

    // TCGdex's names for print variants, and how we show them.
    private val VARIANT_NAMES = listOf(
        "normal" to "Normal",
        "holofoil" to "Holo",
        "reverse-holofoil" to "Reverse holo",
        "1st-edition" to "1st edition",
        "1st-edition-holofoil" to "1st edition holo",
        "unlimited" to "Unlimited",
        "unlimited-holofoil" to "Unlimited holo",
    )

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("TCGdex replied with code $code")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}

/** Everything the card screen shows. */
data class CardDetails(
    val id: String,
    val name: String,
    val rarity: String?,
    val setName: String?,
    val illustrator: String?,
    val prices: List<MarketPrice>,
)

/**
 * A market price: what the card typically sells for on a marketplace.
 * Always called "market price", never "sold price".
 */
data class MarketPrice(
    val source: String,   // "TCGplayer" or "Cardmarket"
    val variant: String,  // "Normal", "Holo", "Reverse holo"...
    val amount: Double,
    val currency: String, // "USD" or "EUR"
    val updated: String?, // when the source last updated it, e.g. "2026-11-20T22:16:31Z"
)
