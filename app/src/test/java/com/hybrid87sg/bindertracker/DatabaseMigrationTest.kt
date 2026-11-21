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
            .allowMainThreadQueries()
            .build()

        // 3. The binder survived, and the new tables work.
        val binders = db.binderDao().observeBinders("local").first()
        assertEquals(listOf("Vintage"), binders.map { it.name })
        assertEquals(0, db.cardDao().count())
        assertEquals(emptyList<Any>(), db.pageDao().observePages("b1").first())
        db.close()
    }
}
