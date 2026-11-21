package com.hybrid87sg.bindertracker.scan

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Recognises a card from its picture, on the phone, with a small AI picture
 * model (MobileNet, run by Google's MediaPipe).
 *
 * The model turns a picture into 1024 numbers. The numbers for every
 * official card picture were worked out ahead of time on GitHub and are
 * downloaded once (about 20 MB) into the app's storage. After that,
 * recognising works offline.
 */
class CardRecognizer private constructor(private val context: Context) {

    private val file = File(context.filesDir, "card-embeddings.bin")
    private val lock = Mutex()
    private var index: EmbeddingIndex? = null
    private var embedder: ImageEmbedder? = null

    /** True once the card pictures list is on the phone. */
    val isDownloaded: Boolean get() = file.exists()

    /**
     * Downloads the card pictures list if it isn't on the phone yet (or
     * [force] to get a newer one). Returns false if the download failed.
     */
    suspend fun download(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            if (file.exists() && !force) return@withLock true
            runCatching {
                val partial = File(context.filesDir, "card-embeddings.bin.part")
                var conn = URL(LIST_URL).openConnection() as HttpURLConnection
                // GitHub sends release files from another address; follow it.
                var hops = 0
                while (conn.responseCode in 301..308 && hops++ < 5) {
                    val next = conn.getHeaderField("Location")
                    conn.disconnect()
                    conn = URL(next).openConnection() as HttpURLConnection
                }
                try {
                    check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                    conn.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
                } finally {
                    conn.disconnect()
                }
                // Check it reads before swapping it in.
                partial.inputStream().use { EmbeddingIndex.read(it) }
                partial.renameTo(file).also { index = null }
            }.getOrDefault(false)
        }
    }

    /**
     * The best few guesses for a picture of one card (cut out so the card
     * fills it), best first. Empty if the list isn't downloaded.
     */
    suspend fun guesses(card: Bitmap, count: Int = 5): List<EmbeddingIndex.Guess> = withContext(Dispatchers.Default) {
        val index = lock.withLock { loadIndex() } ?: return@withContext emptyList()
        val numbers = lock.withLock { describe(card) } ?: return@withContext emptyList()
        if (numbers.size != index.dim) return@withContext emptyList()
        index.nearest(numbers, count)
    }

    private fun loadIndex(): EmbeddingIndex? {
        index?.let { return it }
        if (!file.exists()) return null
        return runCatching { file.inputStream().use { EmbeddingIndex.read(it) } }.getOrNull()?.also { index = it }
    }

    private fun describe(card: Bitmap): FloatArray? = runCatching {
        val e = embedder ?: ImageEmbedder.createFromOptions(
            context,
            ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
                .setL2Normalize(true)
                .build(),
        ).also { embedder = it }
        val soft = if (card.config == Bitmap.Config.HARDWARE) card.copy(Bitmap.Config.ARGB_8888, false) else card
        e.embed(BitmapImageBuilder(soft).build()).embeddingResult().embeddings().first().floatEmbedding()
    }.getOrNull()

    companion object {
        // Must be the same model the GitHub job used (scripts/build_card_embeddings.py).
        private const val MODEL = "card_embedder.tflite"
        private const val LIST_URL =
            "https://github.com/hybrid87sg/pokemon-binder-tracker/releases/download/card-pictures/card-embeddings.bin"

        @Volatile private var instance: CardRecognizer? = null

        fun get(context: Context): CardRecognizer =
            instance ?: synchronized(this) { instance ?: CardRecognizer(context.applicationContext).also { instance = it } }
    }
}
