package com.hybrid87sg.bindertracker.scan

/**
 * One pocket on the "check the scan" screen, before the page is saved.
 *
 * @param read what the camera read in this pocket.
 * @param candidates cards from the card index that might match, best first.
 * @param chosen the card we'll save. Starts as the best candidate; the person can change it.
 * @param empty true when the pocket will be saved as empty.
 */
data class PocketDraft(
    val read: ReadCard,
    val candidates: List<CardCandidate> = emptyList(),
    val chosen: CardCandidate? = null,
    val empty: Boolean = read.looksEmpty,
) {
    val position: Int get() = read.position

    /** The name to show and save: the chosen card's, otherwise what the camera read. */
    val displayName: String? get() = if (empty) null else chosen?.name ?: read.name

    val displayNumber: String? get() = if (empty) null else chosen?.number ?: read.printedNumber

    /** Something was in the pocket, but we couldn't work out its name. */
    val needsHelp: Boolean get() = !empty && displayName == null

    companion object {
        /** Builds a pocket from what was read and what the card index answered. */
        fun from(read: ReadCard, candidates: List<CardCandidate>): PocketDraft {
            val ranked = rank(read, candidates)
            return PocketDraft(read = read, candidates = ranked, chosen = ranked.firstOrNull())
        }

        /**
         * Puts the most likely card first: the same number counts most, then
         * the same name. The card index already sorts, this is a safety net.
         */
        fun rank(read: ReadCard, candidates: List<CardCandidate>): List<CardCandidate> {
            fun score(c: CardCandidate): Int {
                var s = 0
                val readNumber = read.number?.trimStart('0')
                val candNumber = c.number.substringBefore('/').trimStart('0')
                if (readNumber != null && readNumber.equals(candNumber, ignoreCase = true)) s += 4
                val readName = read.name?.let(::simplify)
                val candName = simplify(c.name)
                if (readName != null && readName.isNotEmpty()) {
                    if (readName == candName) s += 3
                    else if (candName.contains(readName) || readName.contains(candName)) s += 1
                }
                return s
            }
            // sortedByDescending keeps the card index's order for equal scores.
            return candidates.sortedByDescending(::score)
        }

        /** "Pikachu ex" and "PIKACHU EX " count as the same name. */
        private fun simplify(name: String): String =
            name.lowercase().replace('é', 'e').filter { it.isLetterOrDigit() }
    }
}
