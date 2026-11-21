package com.hybrid87sg.bindertracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One binder on the shelf.
 *
 * "@Entity" tells Room (the phone's database library) to store these in a
 * table called "binders". Each property below becomes a column.
 */
@Entity(tableName = "binders")
data class Binder(
    // A random ID such as "3f2a...". We use random text instead of 1, 2, 3
    // so that binders made on two different phones never clash when we
    // later sync them online with Firebase.
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    // Which signed-in person owns this binder (their Firebase user ID),
    // or "local" when the app is used without signing in.
    val ownerId: String,
    val name: String,
    // Spine colour stored as text, for example "#8C2F2F".
    val colorHex: String,
    // Binder size, e.g. 3 columns x 3 rows = 9 pockets per page.
    val columns: Int,
    val rows: Int,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val pocketsPerPage: Int get() = columns * rows
    val sizeLabel: String get() = "$columns × $rows"
}

/** The three binder sizes the app offers (checklist item #35). */
enum class BinderSize(val columns: Int, val rows: Int) {
    TwoByTwo(2, 2),
    ThreeByThree(3, 3),
    ThreeByFour(3, 4);

    val label: String get() = "$columns × $rows"
    val pockets: Int get() = columns * rows

    companion object {
        /** The size matching a saved binder, or 3 × 3 if it's somehow none of these. */
        fun of(columns: Int, rows: Int): BinderSize =
            entries.firstOrNull { it.columns == columns && it.rows == rows } ?: ThreeByThree
    }
}

/** Spine colours, the same six as the prototype. */
enum class SpineColor(val label: String, val hex: String) {
    Red("Red", "#8C2F2F"),
    Navy("Navy", "#1F3A5F"),
    Green("Green", "#2F5D3A"),
    Plum("Plum", "#5B3A7A"),
    Charcoal("Charcoal", "#3A3A38"),
    Brown("Brown", "#9A5B12"),
}
