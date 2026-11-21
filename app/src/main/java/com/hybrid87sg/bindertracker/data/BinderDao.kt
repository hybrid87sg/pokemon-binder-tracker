package com.hybrid87sg.bindertracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
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
}
