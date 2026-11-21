package com.hybrid87sg.bindertracker.scan

import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.sqrt

/**
 * The "picture description" of every card, from card-embeddings.bin (built
 * on GitHub by scripts/build_card_embeddings.py).
 *
 * The AI picture model turns any picture into 1024 numbers. Pictures that
 * look alike get similar numbers, so to recognise a scanned card we find the
 * cards whose numbers are closest to the scan's.
 *
 * Plain Kotlin, no phone needed, so it can be tested on a computer.
 */
class EmbeddingIndex(
    val ids: List<String>,
    val dim: Int,
    // ids.size * dim small whole numbers (-127..127), one card after another.
    private val values: ByteArray,
) {
    private val lengths = FloatArray(ids.size) { i ->
        var sum = 0f
        for (j in 0 until dim) { val v = values[i * dim + j].toFloat(); sum += v * v }
        sqrt(sum).coerceAtLeast(1e-6f)
    }

    /** One guess: the card and how alike it looks, from about 0 (not alike) to 1 (identical). */
    data class Guess(val cardId: String, val score: Float)

    private val rowOf: Map<String, Int> by lazy { ids.withIndex().associate { (i, id) -> id to i } }

    /**
     * How alike [query] looks to each of [cardIds] (a shortlist, e.g. every
     * card with the name that was read), best first. Ids not in the file are left out.
     */
    fun rank(query: FloatArray, cardIds: Collection<String>): List<Guess> {
        require(query.size == dim) { "Expected $dim numbers, got ${query.size}" }
        val qLen = sqrt(query.fold(0f) { acc, v -> acc + v * v }).coerceAtLeast(1e-6f)
        return cardIds.mapNotNull { id ->
            val i = rowOf[id] ?: return@mapNotNull null
            var dot = 0f
            for (j in 0 until dim) dot += query[j] * values[i * dim + j]
            Guess(id, dot / (qLen * lengths[i]))
        }.sortedByDescending { it.score }
    }

    /** The [count] cards that look most like [query] (the scan's 1024 numbers), best first. */
    fun nearest(query: FloatArray, count: Int = 5): List<Guess> {
        require(query.size == dim) { "Expected $dim numbers, got ${query.size}" }
        var qLen = 0f
        for (v in query) qLen += v * v
        qLen = sqrt(qLen).coerceAtLeast(1e-6f)
        // Keep the best few as we go, rather than sorting all ~20,000.
        val best = ArrayList<Guess>(count + 1)
        for (i in ids.indices) {
            var dot = 0f
            val start = i * dim
            for (j in 0 until dim) dot += query[j] * values[start + j]
            val score = dot / (qLen * lengths[i])
            if (best.size < count || score > best.last().score) {
                val at = best.indexOfFirst { score > it.score }.let { if (it < 0) best.size else it }
                best.add(at, Guess(ids[i], score))
                if (best.size > count) best.removeAt(best.size - 1)
            }
        }
        return best
    }

    companion object {
        /** Reads the file format written by scripts/build_card_embeddings.py. */
        fun read(input: InputStream): EmbeddingIndex {
            val data = DataInputStream(input.buffered())
            val magic = ByteArray(4).also { data.readFully(it) }
            require(String(magic, Charsets.US_ASCII) == "CEMB") { "Not a card pictures file" }
            val version = data.readInt()
            require(version == 1) { "Unknown card pictures file version $version" }
            val dim = data.readInt()
            val count = data.readInt()
            val ids = List(count) {
                val bytes = ByteArray(data.readUnsignedShort())
                data.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
            val values = ByteArray(count * dim)
            data.readFully(values)
            return EmbeddingIndex(ids, dim, values)
        }
    }
}

/**
 * Decides what to do with the picture guesses for one card.
 *
 * - If the camera also read the name (and maybe number) and one of the top
 *   guesses agrees, that guess is used: picture and text agree.
 * - Otherwise the top guess is used only if it's clearly ahead of the next.
 * - Otherwise nothing is filled in and the guesses are offered to pick from.
 */
object GuessPicker {
    // How far ahead the top guess must be to use it without the text agreeing.
    // A first guess, to be tuned on real scans.
    const val CLEAR_LEAD = 0.06f

    // Below this the top guess is too unlike the scan to use on its own.
    // (Even unrelated pictures score around 0.6-0.7 with this model.)
    const val MIN_SCORE = 0.7f

    /**
     * Choosing among cards that all have the name that was read (so they
     * mostly differ by set): use the best-looking one if it's the only
     * one, or clearly ahead of the next.
     */
    fun chooseAmongSameName(ranked: List<EmbeddingIndex.Guess>): String? {
        val top = ranked.firstOrNull() ?: return null
        val next = ranked.getOrNull(1) ?: return top.cardId
        return if (top.score - next.score >= SAME_NAME_LEAD) top.cardId else null
    }

    // Prints of one Pokémon can share artwork, so a smaller lead is enough
    // here, but not none. A first guess, to be tuned on real scans.
    const val SAME_NAME_LEAD = 0.03f

    /**
     * @param guesses best first, with each guess's card name and number.
     * @return the chosen card id, or null to ask the person.
     */
    fun choose(read: ReadCard, guesses: List<Pair<EmbeddingIndex.Guess, Pair<String, String>>>): String? {
        if (guesses.isEmpty()) return null
        val readName = read.name?.let(PocketMatcher::simplify)?.takeIf { it.length >= 3 }
        val readNumber = read.number?.let(PocketMatcher::numberKey)
        if (readName != null) {
            val agrees = guesses.firstOrNull { (_, card) ->
                val name = PocketMatcher.simplify(card.first)
                val nameOk = name == readName || PocketMatcher.sameApartFromSuffix(name, readName)
                nameOk && (readNumber == null || PocketMatcher.numberKey(card.second) == readNumber)
            }
            if (agrees != null) return agrees.first.cardId
        }
        val top = guesses[0].first
        val lead = top.score - (guesses.getOrNull(1)?.first?.score ?: 0f)
        return if (top.score >= MIN_SCORE && lead >= CLEAR_LEAD) top.cardId else null
    }
}
