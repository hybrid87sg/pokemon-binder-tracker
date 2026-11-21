package com.hybrid87sg.bindertracker.scan

/**
 * The short message shown at the top of a page after the camera reads it,
 * e.g. "Recognised 6 of 9 cards". Kept separate so it can be tested.
 *
 * @param total pockets on the page.
 * @param recognised pockets where the camera found a card in the index.
 * @param changed pockets that were filled in or changed by this reading.
 * @param noNumber pockets with text where the card number couldn't be read.
 * @param rescan true when this was a new photo of an existing page.
 */
data class ReadingSummary(
    val total: Int,
    val recognised: Int,
    val changed: Int,
    val noNumber: Int,
    val rescan: Boolean,
) {
    fun message(): String {
        val tip = if (noNumber > 0) " Couldn't read the number on $noNumber ${cards(noNumber)}: try a closer, sharper photo with less glare." else ""
        return when {
            rescan && recognised == 0 -> "Couldn't recognise any cards in the new photo, so nothing changed.$tip"
            rescan && changed == 0 -> "Recognised $recognised of $total cards. Nothing new on this page.$tip"
            rescan -> "Updated $changed ${pockets(changed)} with new cards (recognised $recognised of $total).$tip"
            recognised == 0 -> "Couldn't recognise any cards automatically. Tap a pocket to choose its card.$tip"
            recognised == total -> "Recognised all $total cards. Tap one to check or change it."
            else -> "Recognised $recognised of $total cards. Tap the others to choose them.$tip"
        }
    }

    private fun cards(n: Int) = if (n == 1) "card" else "cards"
    private fun pockets(n: Int) = if (n == 1) "pocket" else "pockets"
}
