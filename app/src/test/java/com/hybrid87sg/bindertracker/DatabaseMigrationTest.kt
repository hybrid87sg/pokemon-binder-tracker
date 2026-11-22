package com.hybrid87sg.bindertracker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hybrid87sg.bindertracker.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Makes sure updating the app keeps people's binders.
 *
 * It builds a version-1 database exactly as the first app release did
 * (using the saved description in app/schemas/.../1.json), then opens it
 * with today's app code. Room upgrades it and checks every table matches
 * version 2; if anything were wrong, opening would fail.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun upgradeFrom1To2KeepsBinders() = runTest {
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }

        // 1. A version-1 database with one binder in it.
        val v1 = JSONObject(File("schemas/com.hybrid87sg.bindertracker.data.AppDatabase/1.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = v1.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = v1.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL(
                "INSERT INTO binders (id, ownerId, name, colorHex, columns, rows, createdAt) " +
                    "VALUES ('b1', 'local', 'Vintage', '#8C2F2F', 3, 3, 1000)",
            )
            db.version = 1
        }

        // 2. Open it with the current app code (version 2). Room upgrades it here.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        // 3. The binder survived, and the new tables work.
        val binders = db.binderDao().observeBinders("local").first()
        assertEquals(listOf("Vintage"), binders.map { it.name })
        assertEquals(0, db.cardDao().count())
        assertEquals(emptyList<Any>(), db.pageDao().observePages("b1").first())
        db.close()
    }

    /**
     * A phone on version 3 (before shared binders) updating straight to today:
     * the hand-written steps 3 -> 4 -> 5 must give exactly the tables Room expects.
     */
    @Test
    fun upgradeFrom3KeepsBindersAndAddsMissingCardsColumns() = runTest {
        val file = context.getDatabasePath("migration-test-3.db").apply { parentFile?.mkdirs(); delete() }
        val v3 = JSONObject(File("schemas/com.hybrid87sg.bindertracker.data.AppDatabase/3.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = v3.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
            }
            val setup = v3.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL(
                "INSERT INTO binders (id, ownerId, name, colorHex, columns, rows, createdAt) " +
                    "VALUES ('b1', 'local', 'Kanto', '#1F3A5F', 3, 3, 1000)",
            )
            db.version = 3
        }

        // Room checks every table against version 5 while opening; a mismatch would throw here.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        val binder = db.binderDao().observeBinders("local").first().single()
        assertEquals("Kanto", binder.name)
        assertEquals("owner", binder.role)
        assertEquals("", binder.followedSets)
        assertEquals(false, binder.countSecretRares)
        db.binderDao().update(binder.copy(followedSets = "base1,sv03.5"))
        assertEquals(listOf("base1", "sv03.5"), db.binderDao().get("b1")!!.followedSetIds)
        db.close()
    }
}
