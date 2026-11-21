package com.hybrid87sg.bindertracker.data

/**
 * The rules for changing a binder's size (Edit binder screen).
 *
 * Pockets are numbered 0, 1, 2... left to right, top to bottom. A 3 × 4
 * page has pockets 0–11; a 3 × 3 page only has 0–8. So when a binder gets
 * smaller, pockets 9, 10 and 11 on every page stop existing.
 *
 * Ril chose: cards in those pockets keep their order and move onto new
 * pages added at the back of the binder. Every other card stays put.
 * This file only does the planning (plain Kotlin, easy to test); the
 * ViewModel then saves the plan to the database.
 */
object BinderResize {

    /**
     * The cards that won't fit a page of [newPocketCount] pockets, in binder
     * order: page 1's extras first (by pocket), then page 2's, and so on.
     * [pages] must already be in page order.
     */
    fun cardsThatDontFit(pages: List<List<Pocket>>, newPocketCount: Int): List<String> =
        pages.flatMap { pockets ->
            pockets.filter { it.position >= newPocketCount }
                .sortedBy { it.position }
                .mapNotNull { it.cardId }
        }

    /**
     * Splits the moved cards into new pages of [newPocketCount] pockets.
     * Example: 5 cards into 2 × 2 pages gives [[a, b, c, d], [e]].
     */
    fun newPages(movedCards: List<String>, newPocketCount: Int): List<List<String>> =
        movedCards.chunked(newPocketCount)

    /** How many new pages [movedCount] cards need (used for the warning text). */
    fun pagesNeeded(movedCount: Int, newPocketCount: Int): Int =
        (movedCount + newPocketCount - 1) / newPocketCount
}
