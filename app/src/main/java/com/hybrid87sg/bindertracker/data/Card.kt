package com.hybrid87sg.bindertracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.net.URLDecoder

/**
 * One card in the card index (downloaded from TCGdex, kept on the phone).
 * Example: id "swsh3-136", number "136", name "Furret", set "Darkness Ablaze".
 */
@Entity(tableName = "cards", indices = [Index("name"), Index("localId")])
data class Card(
    @PrimaryKey val id: String,
    // The number printed on the card, e.g. "136" (TCGdex calls it "localId").
    val localId: String,
    val name: String,
    val setId: String,
    val setName: String,
    // Base address of the card's picture, or null if TCGdex has none.
    val imageUrl: String?,
) {
    /** Small picture for lists. */
    val thumbnailUrl: String? get() = imageUrl?.let { "$it/low.webp" }

    /** Big picture for the card screen. */
    val largeImageUrl: String? get() = imageUrl?.let { "$it/high.webp" }

    /** The card number for people to read (a few are stored web-encoded, like "%3F" for "?"). */
    val number: String get() = runCatching { URLDecoder.decode(localId, "UTF-8") }.getOrDefault(localId)
}
