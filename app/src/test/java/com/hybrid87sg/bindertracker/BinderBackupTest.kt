package com.hybrid87sg.bindertracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hybrid87sg.bindertracker.backup.BackupFile
import com.hybrid87sg.bindertracker.backup.BinderBackup
import com.hybrid87sg.bindertracker.data.AppDatabase
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.Page
import com.hybrid87sg.bindertracker.data.Pocket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Backs up a binder from one "phone" (database) and restores it on another,
 * then checks everything came back: the binder, its page, the photo and
 * which card is in each pocket.
 */
@RunWith(RobolectricTestRunner::class)
class BinderBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun newPhone(name: String): Pair<AppDatabase, BinderBackup> {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val photos = File(context.filesDir, "$name/pages").apply { deleteRecursively(); mkdirs() }
        return db to BinderBackup(db.binderDao(), db.pageDao(), photos)
    }

    @Test
    fun backupAndRestoreKeepsEverything() = runTest {
        val (oldDb, oldBackup) = newPhone("old")
        val binder = Binder(id = "b1", ownerId = "local", name = "Vintage", colorHex = "#1F3A5F", columns = 3, rows = 3, createdAt = 1000)
        oldDb.binderDao().insert(binder)
        val photo = File(context.filesDir, "old/pages/p1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        oldDb.pageDao().createPage(Page(id = "p1", binderId = "b1", photoPath = photo.absolutePath, guideLeft = 0.1f, createdAt = 2000), 9)
        oldDb.pageDao().upsertPocket(Pocket("p1", 0, cardId = "base1-4"))
        oldDb.pageDao().upsertPocket(Pocket("p1", 8, isEmpty = true))

        val file = ByteArrayOutputStream()
        val saved = oldBackup.write("local", file)
        assertEquals(1, saved.binders)
        assertEquals(1, saved.pages)

        // A new phone, signed in as someone else: the binder becomes theirs.
        val (newDb, newBackup) = newPhone("new")
        val restored = newBackup.restore("user-2", ByteArrayInputStream(file.toByteArray()))
        assertEquals(1, restored.binders)
        assertEquals(1, restored.pages)

        val back = newDb.binderDao().bindersOf("user-2").single()
        assertEquals("Vintage", back.name)
        assertEquals("#1F3A5F", back.colorHex)
        assertEquals(3, back.columns)

        val page = newDb.pageDao().pagesOf("b1").single()
        assertEquals(0.1f, page.guideLeft)
        assertEquals(listOf<Byte>(1, 2, 3, 4), File(page.photoPath!!).readBytes().toList())

        val pockets = newDb.binderDao().pocketsOf(page.id)
        assertEquals(9, pockets.size)
        assertEquals("base1-4", pockets[0].cardId)
        assertTrue(pockets[8].isEmpty)
        assertNull(pockets[4].cardId)
        assertEquals(1, newDb.pageDao().observePages("b1").first().single().identified)

        // Restoring the same file again doesn't make copies.
        val again = newBackup.restore("user-2", ByteArrayInputStream(file.toByteArray()))
        assertEquals(0, again.binders)
        assertEquals(1, again.skipped)
        assertEquals(1, newDb.pageDao().pagesOf("b1").size)
    }

    @Test
    fun aFileThatIsNotABackupIsRefused() = runTest {
        val (_, backup) = newPhone("junk")
        try {
            backup.restore("local", ByteArrayInputStream("hello".toByteArray()))
            fail("should have refused the file")
        } catch (e: BackupFile.NotABackup) {
            // expected
        }
    }
}
