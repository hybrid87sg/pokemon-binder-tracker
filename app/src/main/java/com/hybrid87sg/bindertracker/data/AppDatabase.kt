package com.hybrid87sg.bindertracker.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's database file on the phone.
 *
 * Whenever the tables change, "version" goes up by one and an AutoMigration
 * is listed, so Room upgrades the database already on people's phones
 * instead of wiping their binders. Version history:
 * 1: binders
 * 2: + cards (card index), pages, pockets
 * 3: + cards.seriesId / seriesName / seriesOrder (series filter)
 * 4: + binders.role / ownerName / memberCount (shared binders)
 */
@Database(
    entities = [Binder::class, Card::class, Page::class, Pocket::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun binderDao(): BinderDao
    abstract fun cardDao(): CardDao
    abstract fun pageDao(): PageDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Opens the database once and reuses it everywhere after that. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "binder-tracker.db",
                ).build().also { instance = it }
            }
    }
}
