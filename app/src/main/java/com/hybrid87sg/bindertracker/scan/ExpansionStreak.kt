package com.hybrid87sg.bindertracker.scan

/**
 * Remembers which expansion the last few cards put in each binder came from.
 *
 * When two or more cards in a row come from the same expansion, that's a
 * "streak": the person is probably filling a page from that set, so the
 * "Choose card from list" search starts filtered to it, and unsure scans
 * show that expansion's guesses first.
 *
 * Kept only while the app is open (it's a hint, not something to save).
 */
class ExpansionStreak {
    /** The expansion (set ID) and how many cards in a row came from it. */
    data class Streak(val setId: String, val count: Int)

    private val lastByBinder = mutableMapOf<String, Streak>()

    /** Call each time a card is put into a pocket of [binderId]. */
    @Synchronized
    fun record(binderId: String, setId: String) {
        if (setId.isEmpty()) return
        val last = lastByBinder[binderId]
        lastByBinder[binderId] = if (last?.setId == setId) last.copy(count = last.count + 1) else Streak(setId, 1)
    }

    /** The binder's streak, or null if the last two cards weren't from the same expansion. */
    @Synchronized
    fun current(binderId: String): Streak? = lastByBinder[binderId]?.takeIf { it.count >= MIN_IN_A_ROW }

    companion object {
        const val MIN_IN_A_ROW = 2

        /**
         * Moves cards from the streak's expansion to the front of [guesses],
         * keeping the order otherwise.
         */
        fun <T> preferSet(guesses: List<T>, setId: String?, setOf: (T) -> String): List<T> {
            if (setId == null) return guesses
            val (inSet, others) = guesses.partition { setOf(it) == setId }
            return inSet + others
        }
    }
}
