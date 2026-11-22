package com.hybrid87sg.bindertracker.scan

import com.hybrid87sg.bindertracker.data.Card

/** The checks live scanning makes, in order. It stops at the first one that is sure. */
enum class LiveStep { Number, Name, Moves, Artwork }

/** What one look through the camera found. */
sealed interface LiveCheck {
    /** Nothing readable in the frame: most likely no card there yet. */
    data object NoCard : LiveCheck

    /** One card fits, found at [step]. */
    data class Sure(val card: Card, val step: LiveStep, val sameName: Int = 0) : LiveCheck

    /**
     * Not sure yet. [shortlist] is the cards it could still be (by name, then
     * narrowed by the moves read), for the artwork to choose between.
     * [sameName] is how many cards have the name that was read, and
     * [movesNarrowed] is true when the moves cut that list down.
     */
    data class Unsure(
        val name: String?,
        val shortlist: List<Card>,
        val sameName: Int,
        val movesNarrowed: Boolean,
        val numberFound: Boolean,
    ) : LiveCheck
}
