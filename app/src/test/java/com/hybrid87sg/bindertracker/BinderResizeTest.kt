package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.BinderResize
import com.hybrid87sg.bindertracker.data.Pocket
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks the "binder made smaller" rules: extra cards move, in order, to new pages at the back. */
class BinderResizeTest {
    private fun page(id: String, vararg cards: Pair<Int, String?>) =
        cards.map { (pos, card) -> Pocket(id, pos, cardId = card) }

    @Test fun shrinking3x4To3x3MovesOnlyPockets9To11InOrder() {
        val pages = listOf(
            page("p1", 0 to "a", 8 to "b", 11 to "d", 9 to "c", 10 to null),
            page("p2", 2 to "e", 10 to "f"),
        )
        assertEquals(listOf("c", "d", "f"), BinderResize.cardsThatDontFit(pages, 9))
    }

    @Test fun nothingMovesWhenTheLostPocketsAreEmpty() {
        val pages = listOf(page("p1", 0 to "a", 9 to null, 10 to null))
        assertEquals(emptyList<String>(), BinderResize.cardsThatDontFit(pages, 9))
    }

    @Test fun movedCardsAreSplitIntoFullPagesThenTheRest() {
        assertEquals(listOf(listOf("a", "b", "c", "d"), listOf("e")), BinderResize.newPages(listOf("a", "b", "c", "d", "e"), 4))
        assertEquals(2, BinderResize.pagesNeeded(5, 4))
        assertEquals(1, BinderResize.pagesNeeded(4, 4))
        assertEquals(0, BinderResize.pagesNeeded(0, 4))
    }
}
