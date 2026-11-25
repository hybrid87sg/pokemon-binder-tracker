package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.ui.three.PageLanding
import com.hybrid87sg.bindertracker.ui.three.SheetLook
import com.hybrid87sg.bindertracker.ui.three.SheetPocket
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLandingTest {
    private val left = listOf(SheetPocket(0, SheetPocket.Kind.Card, "left-card"))
    private val empty = listOf(SheetPocket(0, SheetPocket.Kind.Empty))
    private val firstLook = PageLanding.lookOf(1, 1, listOf(0 to "left-card"))
    private val secondLook = PageLanding.lookOf(1, 1, listOf(0 to "E"))
    private val sheet = SheetLook("left", 1, 1, left, secondPageLook = secondLook)

    @Test
    fun unfoldedShotBecomesStaleWhenEitherVisiblePageChanges() {
        val shot = shot(true, PageLanding.spreadLookOf(firstLook, secondLook))
        val rightWithMissingCard = PageLanding.lookOf(1, 1, listOf(0 to "gright-card"))

        assertFalse(PageLanding.isOutOfDate(shot, sheet))
        assertTrue(PageLanding.isOutOfDate(shot, sheet.copy(pockets = empty)))
        assertTrue(PageLanding.isOutOfDate(shot, sheet.copy(secondPageLook = rightWithMissingCard)))
        assertTrue(PageLanding.isOutOfDate(shot, sheet.copy(secondPageLook = null)))
    }

    @Test
    fun unfoldedShotCanShowJustOnePageUntilAnotherPageIsAdded() {
        val shot = shot(true, PageLanding.spreadLookOf(firstLook, null))

        assertFalse(PageLanding.isOutOfDate(shot, sheet.copy(secondPageLook = null)))
        assertTrue(PageLanding.isOutOfDate(shot, sheet))
    }

    @Test
    fun foldedShotKeepsItsOriginalFingerprintAndLegacyShotsWithoutOneStillOpen() {
        val folded = shot(false, firstLook)

        assertFalse(PageLanding.isOutOfDate(folded, sheet.copy(secondPageLook = null)))
        assertTrue(PageLanding.isOutOfDate(folded, sheet.copy(pockets = empty)))
        for (spread in listOf(false, true)) {
            assertFalse(PageLanding.isOutOfDate(shot(spread, null), sheet))
        }
    }

    private fun shot(spread: Boolean, look: Int?) = PageLanding.Shot(File("unused.png"), emptyList(), spread, 0L, look)
}
