package com.hybrid87sg.bindertracker.cards

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/** The questions and changes we can send to the cards table. */
@Dao
interface CardDao {
    @Query("SELECT COUNT(*) FROM cards")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun byId(id: String): Card?

    @Query("SELECT * FROM cards WHERE numberKey = :numberKey")
    suspend fun byNumber(numberKey: String): List<Card>

    /** Runs a search built by [CardQuery.toSql]. */
    @RawQuery
    suspend fun search(query: SupportSQLiteQuery): List<Card>

    @Query("DELETE FROM cards")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<Card>)
}

/**
 * The card index's own database file, "card-index.db", separate from your
 * binders. If its layout ever changes in an update, the old file is simply
 * thrown away and the card list is downloaded again; your binders are safe
 * because they live in a different file.
 */
@Database(entities = [Card::class], version = 1, exportSchema = false)
abstract class CardIndexDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var instance: CardIndexDatabase? = null

        fun get(context: Context): CardIndexDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CardIndexDatabase::class.java,
                    "card-index.db",
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
            }
    }
}
