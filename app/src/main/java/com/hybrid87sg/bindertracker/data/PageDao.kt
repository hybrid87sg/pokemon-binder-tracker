package com.hybrid87sg.bindertracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Questions and changes for binder pages and their pockets. */
@Dao
interface PageDao {
    // "@Transaction" makes Room read the pages and their pockets together,
    // so the screen never sees a page with half its pockets.
    @Transaction
    @Query("SELECT * FROM binder_pages WHERE binderId = :binderId ORDER BY pageNumber")
    fun observePages(binderId: String): Flow<List<PageWithPockets>>

    @Query("SELECT COALESCE(MAX(pageNumber), 0) FROM binder_pages WHERE binderId = :binderId")
    suspend fun lastPageNumber(binderId: String): Int

    @Insert
    suspend fun insertPage(page: BinderPage)

    @Insert
    suspend fun insertPockets(pockets: List<Pocket>)

    /** Saves a new page and all its pockets at once (all or nothing). */
    @Transaction
    suspend fun savePage(page: BinderPage, pockets: List<Pocket>) {
        insertPage(page)
        insertPockets(pockets)
    }
}
