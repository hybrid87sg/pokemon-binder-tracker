package com.hybrid87sg.bindertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's database file on the phone. When we add pages, pockets and the
 * card index later, their tables get listed here too and "version" goes up.
 */
@Database(entities = [Binder::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun binderDao(): BinderDao

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
