package com.hybrid87sg.bindertracker.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's database file on the phone.
 *
 * Whenever the tables change, "version" goes up by one and a migration is
 * listed, so Room upgrades the database already on people's phones instead
 * of wiping their binders. Version history:
 * 1: binders
 * 2: + cards (card index), pages, pockets
 * 3: + cards.seriesId / seriesName / seriesOrder (series filter)
 * 4: + binders.role / ownerName / memberCount (shared binders)
 * 5: + binders.followedSets / countSecretRares, cards.rarity / setOfficial (Missing cards)
 */
@Database(
    entities = [Binder::class, Card::class, Page::class, Pocket::class],
    version = 5,
    exportSchema = true,
    // 1->2 and 2->3 are worked out by Room from the saved descriptions in app/schemas.
    // 3->4 and 4->5 are written by hand below (the version 4 description was never saved).
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun binderDao(): BinderDao
    abstract fun cardDao(): CardDao
    abstract fun pageDao(): PageDao
    abstract fun checklistDao(): ChecklistDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Shared binders: who owns a binder, and how many people use it. Same as the old automatic step. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `binders` ADD COLUMN `role` TEXT NOT NULL DEFAULT 'owner'")
                db.execSQL("ALTER TABLE `binders` ADD COLUMN `ownerName` TEXT")
                db.execSQL("ALTER TABLE `binders` ADD COLUMN `memberCount` INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** Missing cards: which expansions a binder collects, and each card's rarity and set size. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `binders` ADD COLUMN `followedSets` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `binders` ADD COLUMN `countSecretRares` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `rarity` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `setOfficial` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_setId` ON `cards` (`setId`)")
            }
        }

        /** Opens the database once and reuses it everywhere after that. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "binder-tracker.db",
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
