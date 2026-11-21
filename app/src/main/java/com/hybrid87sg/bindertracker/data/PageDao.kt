package com.hybrid87sg.bindertracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Questions and changes for binder pages and their pockets. */
@Dao
interface PageDao {
    @Query(
        """
        SELECT pages.id, pages.binderId, pages.photoPath, pages.createdAt,
          (SELECT COUNT(*) FROM pockets p WHERE p.pageId = pages.id AND p.cardId IS NOT NULL) AS identified,
          (SELECT COUNT(*) FROM pockets p WHERE p.pageId = pages.id AND p.isEmpty = 1) AS empty
        FROM pages WHERE binderId = :binderId ORDER BY createdAt
        """,
    )
    fun observePages(binderId: String): Flow<List<PageSummary>>

    @Query("SELECT * FROM pages WHERE id = :id")
    fun observePage(id: String): Flow<Page?>

    @Query("SELECT * FROM pages WHERE binderId = :binderId")
    suspend fun pagesOf(binderId: String): List<Page>

    @Query("SELECT COUNT(*) FROM pages WHERE binderId = :binderId AND createdAt <= :createdAt")
    suspend fun pageNumber(binderId: String, createdAt: Long): Int

    @Query(
        """
        SELECT pockets.position, pockets.isEmpty, pockets.cardId,
          cards.name AS cardName, cards.localId AS cardNumber, cards.setName, cards.imageUrl
        FROM pockets LEFT JOIN cards ON cards.id = pockets.cardId
        WHERE pockets.pageId = :pageId ORDER BY pockets.position
        """,
    )
    fun observePockets(pageId: String): Flow<List<PocketWithCard>>

    @Insert
    suspend fun insertPage(page: Page)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPocket(pocket: Pocket)

    @Insert
    suspend fun insertPockets(pockets: List<Pocket>)

    /** Saves a new page together with its empty pockets. */
    @Transaction
    suspend fun createPage(page: Page, pocketCount: Int) {
        insertPage(page)
        insertPockets(List(pocketCount) { Pocket(pageId = page.id, position = it) })
    }

    @Query("DELETE FROM pockets WHERE pageId = :pageId")
    suspend fun deletePockets(pageId: String)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePageRow(pageId: String)

    @Transaction
    suspend fun deletePage(pageId: String) {
        deletePockets(pageId)
        deletePageRow(pageId)
    }
}
