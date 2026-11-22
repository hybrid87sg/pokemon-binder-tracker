package com.hybrid87sg.bindertracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO = "Data Access Object": the list of questions and changes we can send
 * to the binders table. Room writes the actual database code for us.
 *
 * A "Flow" is a live stream: whenever the table changes, the screen that is
 * watching it gets the new list automatically.
 */
@Dao
interface BinderDao {
    @Query("SELECT * FROM binders WHERE ownerId = :ownerId ORDER BY createdAt")
    fun observeBinders(ownerId: String): Flow<List<Binder>>

    @Query("SELECT * FROM binders WHERE id = :id")
    fun observeBinder(id: String): Flow<Binder?>

    // "suspend" means this runs in the background so the screen never freezes.
    @Insert
    suspend fun insert(binder: Binder)

    @Delete
    suspend fun delete(binder: Binder)

    // Changes just the spine colour of one binder, e.g. to "#F2C230".
    @Query("UPDATE binders SET colorHex = :colorHex WHERE id = :id")
    suspend fun updateColor(id: String, colorHex: String)

    /**
     * Gives every binder owned by [fromOwner] to [toOwner] instead, and
     * returns how many moved. Used when someone who made binders with
     * "Use on this phone only" signs in with Google later: their "local"
     * binders become their account's binders. Pages and cards belong to a
     * binder (not to a person), so they come along automatically.
     */
    @Query("UPDATE binders SET ownerId = :toOwner WHERE ownerId = :fromOwner")
    suspend fun moveBinders(fromOwner: String, toOwner: String): Int

    // ---- Backups ----

    @Query("SELECT * FROM binders WHERE ownerId = :ownerId ORDER BY createdAt")
    suspend fun bindersOf(ownerId: String): List<Binder>

    @Query("SELECT * FROM pockets WHERE pageId = :pageId ORDER BY position")
    suspend fun pocketsOf(pageId: String): List<Pocket>

    @Query("SELECT COUNT(*) FROM binders WHERE id = :id")
    suspend fun binderCount(id: String): Int

    @Insert
    suspend fun insertPageForBackup(page: Page)

    // REPLACE because the backup lists every pocket, including ones we may
    // already have created as blank.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPocketsForBackup(pockets: List<Pocket>)

    /**
     * Puts one binder from a backup back on the shelf, with all its pages
     * and pockets. "@Transaction" means it all goes in together, or not at
     * all if something fails halfway.
     */
    @Transaction
    suspend fun restoreBinder(binder: Binder, pages: List<Page>, pockets: List<Pocket>) {
        insert(binder)
        pages.forEach { insertPageForBackup(it) }
        insertPocketsForBackup(pockets)
    }

    // Saves every column of an existing binder (Edit binder: name, colour, size).
    @Update
    suspend fun update(binder: Binder)
}
