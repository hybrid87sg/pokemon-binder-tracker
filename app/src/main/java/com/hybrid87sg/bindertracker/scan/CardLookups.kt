package com.hybrid87sg.bindertracker.scan

import android.content.Context

/**
 * The one place that decides which card index the scanner asks.
 *
 * For now there is no card index in the app yet, so the scanner keeps the
 * name and number it read from the photo. When the card index (TCGdex) is
 * added, this returns a small adapter that passes questions on to it.
 */
object CardLookups {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): CardLookup = CardLookup.None
}
