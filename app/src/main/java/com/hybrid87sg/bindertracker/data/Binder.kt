package com.hybrid87sg.bindertracker.data

import androidx.room.ColumnInfo
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
    // Whose shelf this binder is on (their Firebase user ID), or "local"
    // when the app is used without signing in. For a binder a friend shared
    // with you, this is you; the friend's name is in [ownerName].
    val ownerId: String,
    val name: String,
    // Spine colour stored as text, for example "#8C2F2F".
    val colorHex: String,
    // Binder size, e.g. 3 columns x 3 rows = 9 pockets per page.
    val columns: Int,
    val rows: Int,
    val createdAt: Long = System.currentTimeMillis(),
    // What you can do with this binder: "owner" (it's yours), "editor"
    // (shared with you, you can change cards) or "viewer" (look only).
    @ColumnInfo(defaultValue = ROLE_OWNER) val role: String = ROLE_OWNER,
    // For a shared binder: the name of the person who owns it.
    val ownerName: String? = null,
    // How many people use this binder, you included (1 = not shared).
    @ColumnInfo(defaultValue = "1") val memberCount: Int = 1,
    // The expansions this binder collects, as TCGdex set IDs separated by
    // commas, e.g. "base1,sv03.5". Empty = not collecting one. The Missing
    // cards page lists the cards from these that aren't in the binder yet.
    @ColumnInfo(defaultValue = "") val followedSets: String = "",
    // True = "master set": secret rares (numbered past the set size, like
    // 180/165) count too. False = "main set" (the default).
    @ColumnInfo(defaultValue = "0") val countSecretRares: Boolean = false,
) {
    val pocketsPerPage: Int get() = columns * rows
    val sizeLabel: String get() = "$columns × $rows"
    val isOwner: Boolean get() = role == ROLE_OWNER
    // Owners and editors can change everything in the binder (cards, pages,
    // name, colour, size, what it collects); viewers can only look.
    val canEdit: Boolean get() = role == ROLE_OWNER || role == ROLE_EDITOR
    val isShared: Boolean get() = memberCount > 1 || !isOwner

    /** [followedSets] as a list, e.g. ["base1", "sv03.5"]. */
    val followedSetIds: List<String> get() = splitSetIds(followedSets)

    companion object {
        const val ROLE_OWNER = "owner"
        const val ROLE_EDITOR = "editor"
        const val ROLE_VIEWER = "viewer"

        /** "base1,sv03.5" -> ["base1", "sv03.5"]; blank -> []. */
        fun splitSetIds(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        /** ["base1", "sv03.5"] -> "base1,sv03.5" (the form [followedSets] is saved in). */
        fun joinSetIds(ids: List<String>): String = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")
    }
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
