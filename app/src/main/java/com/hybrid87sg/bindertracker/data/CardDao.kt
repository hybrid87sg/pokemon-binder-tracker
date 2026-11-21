package com.hybrid87sg.bindertracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Questions and changes for the card index. */
@Dao
interface CardDao {
    @Query("SELECT COUNT(*) FROM cards")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: String): Card?

    // Names that START with the search come first, then names that contain it.
    @Query(
        """
        SELECT * FROM cards
        WHERE name LIKE '%' || :name || '%'
        ORDER BY CASE WHEN name LIKE :name || '%' THEN 0 ELSE 1 END, name, setName
        LIMIT :limit
        """,
    )
    suspend fun searchByName(name: String, limit: Int = 100): List<Card>

    // "025" and "25" both match card number 25, so leading zeros are ignored.
    @Query(
        """
        SELECT * FROM cards
        WHERE (localId = :number OR ltrim(localId, '0') = ltrim(:number, '0'))
          AND name LIKE '%' || :name || '%'
        ORDER BY name, setName
        LIMIT :limit
        """,
    )
    suspend fun searchByNumber(number: String, name: String, limit: Int = 100): List<Card>

    // Every card with this number, for the camera scanner. No limit, so the
    // right card is never cut off just because its name sorts late.
    @Query("SELECT * FROM cards WHERE localId = :number OR ltrim(localId, '0') = ltrim(:number, '0')")
    suspend fun allWithNumber(number: String): List<Card>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<Card>)

    @Query("DELETE FROM cards")
    suspend fun deleteAll()

    /** Swaps in a freshly downloaded card list in one go. */
    @Transaction
    suspend fun replaceAll(cards: List<Card>) {
        deleteAll()
        cards.chunked(500).forEach { insertAll(it) }
    }
}
