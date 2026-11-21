package com.hybrid87sg.bindertracker.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.hybrid87sg.bindertracker.data.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns pictures into fingerprints (see PictureMatcher): the pocket in your
 * photo, and the official card pictures from TCGdex.
 */
object CardPictures {

    // Fingerprints of official pictures already downloaded, by card id, so
    // each picture is only downloaded once while the app is open.
    private val official = ConcurrentHashMap<String, Long>()

    // Download a few pictures at a time, not hundreds at once.
    private val downloads = Semaphore(6)

    /**
     * Fingerprint of a card picture. [inset] trims a little off every edge
     * first, so the sleeve's edge and the card's border matter less.
     */
    fun fingerprint(bitmap: Bitmap, inset: Float = 0.05f): Long {
        val dx = (bitmap.width * inset).toInt()
        val dy = (bitmap.height * inset).toInt()
        val inner = Bitmap.createBitmap(bitmap, dx, dy, (bitmap.width - 2 * dx).coerceAtLeast(1), (bitmap.height - 2 * dy).coerceAtLeast(1))
        val small = Bitmap.createScaledBitmap(inner, PictureMatcher.SIZE, PictureMatcher.SIZE, true)
        val pixels = IntArray(PictureMatcher.SIZE * PictureMatcher.SIZE)
        small.getPixels(pixels, 0, PictureMatcher.SIZE, 0, 0, PictureMatcher.SIZE, PictureMatcher.SIZE)
        // Colour -> grey, weighted the way eyes see brightness.
        val gray = IntArray(pixels.size) { i ->
            val p = pixels[i]
            (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        }
        return PictureMatcher.fingerprint(gray)
    }

    /**
     * Fingerprints of the official pictures of [cards], downloading any not
     * seen yet. Cards with no picture, or whose download fails, are left out.
     */
    suspend fun officialFingerprints(cards: List<Card>): List<Pair<Card, Long>> = coroutineScope {
        cards.map { card ->
            async(Dispatchers.IO) {
                official[card.id]?.let { return@async card to it }
                val url = card.thumbnailUrl ?: return@async null
                val print = downloads.withPermit { download(url) }?.let { fingerprint(it) } ?: return@async null
                official[card.id] = print
                card to print
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun download(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            try {
                if (conn.responseCode != 200) null
                else conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
