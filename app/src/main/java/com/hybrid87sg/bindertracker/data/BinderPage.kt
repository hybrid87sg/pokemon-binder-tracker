package com.hybrid87sg.bindertracker.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

/**
 * One page inside a binder, made by scanning it with the camera.
 *
 * The "foreignKeys" part links each page to its binder. "CASCADE" means:
 * when a binder is deleted, its pages are deleted with it automatically.
 */
@Entity(
    tableName = "binder_pages",
    foreignKeys = [
        ForeignKey(
            entity = Binder::class,
            parentColumns = ["id"],
            childColumns = ["binderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("binderId")],
)
data class BinderPage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val binderId: String,
    // 1 for the first page, 2 for the second, and so on.
    val pageNumber: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One pocket (card slot) on a page. Position 0 is the top-left pocket,
 * then it counts left to right, row by row, like reading a book.
 *
 * An empty pocket has no cardId and no name.
 */
@Entity(
    tableName = "pockets",
    foreignKeys = [
        ForeignKey(
            entity = BinderPage::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pageId")],
)
data class Pocket(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val pageId: String,
    val position: Int,
    // The card's ID in the card index (e.g. "swsh3-136"), when it was matched.
    val cardId: String? = null,
    // What we show: the matched card's name, or the name read from the photo.
    val name: String? = null,
    val setName: String? = null,
    // Collector number as printed, e.g. "136/189".
    val number: String? = null,
    // Everything the camera read in this pocket. Kept to help fix mistakes later.
    val readText: String = "",
) {
    val isEmpty: Boolean get() = cardId == null && name == null
}

/** A page together with its pockets, read from the database in one go. */
data class PageWithPockets(
    @Embedded val page: BinderPage,
    @Relation(parentColumn = "id", entityColumn = "pageId")
    val pockets: List<Pocket>,
) {
    /** Pockets in reading order (top-left first). */
    val ordered: List<Pocket> get() = pockets.sortedBy { it.position }
}
