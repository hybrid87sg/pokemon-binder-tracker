package com.hybrid87sg.bindertracker.scan

import android.content.Context
import com.hybrid87sg.bindertracker.data.Card
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
import java.io.File

/**
 * The moves (attacks and abilities) printed on cards, used as a clue when
 * scanning: if the camera can't read the number, the moves it read tell
 * apart cards that share a name (e.g. every "Charizard ex").
 *
 * The card list on the phone doesn't include moves, so they're looked up
 * on TCGdex one card at a time, only for the few cards that share the name
 * that was read, and remembered in a small file so it's only done once.
 */
class CardMoves private constructor(context: Context) {

    private val file = File(context.filesDir, "card-moves.txt")
    private val lock = Mutex()
    private var known: MutableMap<String, List<String>>? = null
    private val lookups = Semaphore(6)

    /**
     * The moves of each card (by card id). Cards that couldn't be looked up
     * (no internet) are left out.
     */
    suspend fun movesOf(cards: List<Card>): Map<String, List<String>> = coroutineScope {
        val have = lock.withLock { load() }
        val missing = cards.filter { it.id !in have }
        val fetched = missing.map { card ->
            async(Dispatchers.IO) {
                lookups.withPermit { runCatching { card.id to Tcgdex.cardDetails(card.id).moves }.getOrNull() }
            }
        }.awaitAll().filterNotNull()
        if (fetched.isNotEmpty()) {
            lock.withLock {
                val map = load()
                fetched.forEach { (id, moves) -> map[id] = moves }
                save(fetched)
            }
        }
        val all = lock.withLock { load() }
        cards.mapNotNull { c -> all[c.id]?.let { c.id to it } }.toMap()
    }

    // One line per card: "id<TAB>move one|move two".
    private suspend fun load(): MutableMap<String, List<String>> {
        known?.let { return it }
        val map = withContext(Dispatchers.IO) {
            runCatching {
                file.takeIf { it.exists() }?.readLines()?.mapNotNull { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1).split('|').filter { it.isNotBlank() }
                }?.toMap()
            }.getOrNull().orEmpty().toMutableMap()
        }
        known = map
        return map
    }

    private suspend fun save(added: List<Pair<String, List<String>>>) = withContext(Dispatchers.IO) {
        runCatching {
            file.appendText(added.joinToString("") { (id, moves) -> "$id\t${moves.joinToString("|") { it.replace("|", " ").replace("\n", " ") }}\n" })
        }
    }

    companion object {
        @Volatile private var instance: CardMoves? = null

        fun get(context: Context): CardMoves =
            instance ?: synchronized(this) { instance ?: CardMoves(context.applicationContext).also { instance = it } }
    }
}

/**
 * Compares the moves printed on cards with the text the camera read.
 * Plain Kotlin, so it can be tested on a computer (see MoveMatcherTest).
 */
object MoveMatcher {

    /** How many of [moves] appear in [text] (ignoring capitals, spaces and accents). */
    fun found(text: String, moves: List<String>): Int {
        val read = PocketMatcher.simplify(text)
        return moves.count { m -> PocketMatcher.simplify(m).let { it.length >= 3 && read.contains(it) } }
    }

    /**
     * The cards whose moves best match what was read. A card only counts if
     * at least one of its moves was read; the ones with the most moves read
     * win. Empty when no card's moves were read at all.
     */
    fun narrow(text: String, movesById: Map<String, List<String>>): List<String> {
        val scores = movesById.mapValues { (_, moves) -> found(text, moves) }.filterValues { it > 0 }
        val best = scores.values.maxOrNull() ?: return emptyList()
        return scores.filterValues { it == best }.keys.toList()
    }
}
