package com.hybrid87sg.bindertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's database file on the phone. Each table is listed in "entities".
 * When tables are added or changed, "version" goes up by one and a
 * "migration" explains how to upgrade an older phone's database without
 * losing anything already saved.
 */
@Database(
    entities = [Binder::class, BinderPage::class, Pocket::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun binderDao(): BinderDao
    abstract fun pageDao(): PageDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Version 1 → 2: adds the tables for scanned pages and their pockets.
         * Binders already on the phone stay exactly as they were.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `binder_pages` (" +
                        "`id` TEXT NOT NULL, `binderId` TEXT NOT NULL, `pageNumber` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`binderId`) REFERENCES `binders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_binder_pages_binderId` ON `binder_pages` (`binderId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pockets` (" +
                        "`id` TEXT NOT NULL, `pageId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                        "`cardId` TEXT, `name` TEXT, `setName` TEXT, `number` TEXT, `readText` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`pageId`) REFERENCES `binder_pages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pockets_pageId` ON `pockets` (`pageId`)")
            }
        }

        /** Opens the database once and reuses it everywhere after that. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "binder-tracker.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
