package com.hybrid87sg.bindertracker.prices

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Today's rates for turning US dollars and euros into Singapore dollars.
 * [toSgd] maps a currency code to "how many S$ one of it is worth",
 * e.g. "USD" -> 1.294. [date] is the day the rates are from ("2026-11-21").
 */
data class SgdRates(val toSgd: Map<String, Double>, val date: String) {
    /** The amount in Singapore dollars, or null if we have no rate for that currency. */
    fun convert(amount: Double, currency: String): Double? =
        if (currency == "SGD") amount else toSgd[currency]?.let { amount * it }
}

/**
 * Gets exchange rates from Frankfurter (https://frankfurter.dev): free, open
 * source, no account needed. Its rates come from the European Central Bank,
 * which publishes them once every working day.
 *
 * The rates are saved on the phone and fetched again at most twice a day,
 * so card prices still show in S$ when there's no internet.
 */
class ExchangeRates(context: Context) {
    private val prefs = context.getSharedPreferences("exchange_rates", Context.MODE_PRIVATE)

    /** Rates to use now: the saved ones if fresh, otherwise new ones, otherwise old saved ones. */
    suspend fun get(): SgdRates? {
        val saved = saved()
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0)
        if (saved != null && System.currentTimeMillis() - fetchedAt < REFRESH_AFTER_MS) return saved
        val fresh = runCatching { download() }.getOrNull() ?: return saved
        prefs.edit {
            putString(KEY_JSON, fresh.first)
            putLong(KEY_FETCHED_AT, System.currentTimeMillis())
        }
        return fresh.second
    }

    private fun saved(): SgdRates? = prefs.getString(KEY_JSON, null)?.let { runCatching { parse(it) }.getOrNull() }

    /** Downloads the rates; gives the reply (to save) and what it says. */
    private suspend fun download(): Pair<String, SgdRates> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (url in URLS) {
            try {
                val json = get(url)
                return@withContext json to parse(json)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("No exchange rate site answered")
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("Frankfurter replied with code $code")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    companion object {
        private const val KEY_JSON = "json"
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val REFRESH_AFTER_MS = 12L * 60 * 60 * 1000 // twelve hours

        // Euros as the base, asking for S$ and US$. The second address is
        // Frankfurter's older one, in case the first stops working.
        private val URLS = listOf(
            "https://api.frankfurter.dev/v1/latest?base=EUR&symbols=SGD,USD",
            "https://api.frankfurter.app/latest?from=EUR&to=SGD,USD",
        )

        /**
         * Reads Frankfurter's reply:
         * {"amount":1.0,"base":"EUR","date":"2026-11-21","rates":{"SGD":1.42,"USD":1.0975}}
         * €1 = S$1.42 and €1 = US$1.0975, so US$1 = S$(1.42 / 1.0975).
         */
        fun parse(json: String): SgdRates {
            val root = JSONObject(json)
            val base = root.optString("base", "EUR")
            val rates = root.getJSONObject("rates")
            val sgdPerBase = rates.getDouble("SGD")
            val toSgd = mutableMapOf(base to sgdPerBase)
            val names = rates.keys()
            while (names.hasNext()) {
                val code = names.next()
                val perBase = rates.getDouble(code)
                if (code != "SGD" && perBase > 0) toSgd[code] = sgdPerBase / perBase
            }
            return SgdRates(toSgd, root.optString("date"))
        }
    }
}
