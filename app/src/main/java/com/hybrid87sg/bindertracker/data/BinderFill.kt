package com.hybrid87sg.bindertracker.data

/**
 * How full one binder is: [filled] pockets hold a card, out of [total]
 * pockets across its [pages] pages. Used for the shelf's fill bars and totals.
 */
data class BinderFill(
    val binderId: String,
    val pages: Int,
    val filled: Int,
    val total: Int,
) {
    val fraction: Float get() = if (total == 0) 0f else filled.toFloat() / total
}
