package com.hybrid87sg.bindertracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One scanned binder page: a photo, plus where the page sat in the photo.
 *
 * The guide numbers are fractions (0.0–1.0) of the photo's width and height,
 * marking the frame the person lined the page up with. The app cuts that
 * area into pockets (e.g. 3 × 3) to show each card on its own.
 */
@Entity(tableName = "pages", indices = [Index("binderId")])
data class Page(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val binderId: String,
    val photoPath: String?,
    val guideLeft: Float = 0f,
    val guideTop: Float = 0f,
    val guideRight: Float = 1f,
    val guideBottom: Float = 1f,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * What one pocket on a page holds. Position counts left to right, top to
 * bottom, starting at 0. [cardId] is null until the card is identified.
 */
@Entity(tableName = "pockets", primaryKeys = ["pageId", "position"])
data class Pocket(
    val pageId: String,
    val position: Int,
    val cardId: String? = null,
    val isEmpty: Boolean = false,
)

/** A page plus how many of its pockets have a card identified (for lists). */
data class PageSummary(
    val id: String,
    val binderId: String,
    val photoPath: String?,
    val createdAt: Long,
    val identified: Int,
    val empty: Int,
)

/** One pocket joined with its card (if identified), for showing a page. */
data class PocketWithCard(
    val position: Int,
    val isEmpty: Boolean,
    val cardId: String?,
    val cardName: String?,
    val cardNumber: String?,
    val setName: String?,
    val imageUrl: String?,
    // For the page's look: holo shine on shiny cards, and the expansion's pattern.
    val rarity: String? = null,
    val setId: String? = null,
    val seriesId: String? = null,
)

/** One pocket and its card, plus which page it is on (for the page picker). */
data class PagePocket(
    val pageId: String,
    val position: Int,
    val isEmpty: Boolean,
    val cardId: String?,
    val cardName: String?,
    val cardNumber: String?,
    val setName: String?,
    val imageUrl: String?,
    val rarity: String? = null,
    val setId: String? = null,
    val seriesId: String? = null,
) {
    fun withoutPage() = PocketWithCard(position, isEmpty, cardId, cardName, cardNumber, setName, imageUrl, rarity, setId, seriesId)
}
