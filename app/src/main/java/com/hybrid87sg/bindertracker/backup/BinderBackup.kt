package com.hybrid87sg.bindertracker.backup

import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.BinderDao
import com.hybrid87sg.bindertracker.data.Page
import com.hybrid87sg.bindertracker.data.PageDao
import com.hybrid87sg.bindertracker.data.Pocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Saves every binder into one backup file, and puts them back from one.
 *
 * The app never decides where the file goes: Android's own "Save to" and
 * "Open" screens do. That is how the person can pick Google Drive, the
 * phone's Downloads folder, or anywhere else, without the app needing a
 * Google Drive login of its own.
 *
 * [photoFolder] is where restored page photos are saved (the same folder
 * the camera saves new pages into).
 */
class BinderBackup(
    private val binderDao: BinderDao,
    private val pageDao: PageDao,
    private val photoFolder: File,
) {
    /** How a backup went, for the message on screen. */
    data class Result(val binders: Int, val pages: Int, val skipped: Int = 0)

    /** Writes all of [ownerId]'s own binders, pages and photos as a zip into [out]. */
    suspend fun write(ownerId: String, out: OutputStream): Result = withContext(Dispatchers.IO) {
        // Only your own binders. Ones friends shared with you live in their account.
        val binders = binderDao.bindersOf(ownerId).filter { it.isOwner }
        val photos = mutableMapOf<String, File>() // zip name -> photo on the phone
        val data = binders.map { binder ->
            val pages = pageDao.pagesOf(binder.id).sortedBy { it.createdAt }.map { page ->
                val photoFile = page.photoPath?.let(::File)?.takeIf { it.isFile }
                val photoName = photoFile?.let { BackupFile.photoName(page.id) }
                if (photoName != null) photos[photoName] = photoFile
                BackupFile.PageData(
                    id = page.id,
                    photo = photoName,
                    guideLeft = page.guideLeft,
                    guideTop = page.guideTop,
                    guideRight = page.guideRight,
                    guideBottom = page.guideBottom,
                    createdAt = page.createdAt,
                    pockets = binderDao.pocketsOf(page.id).map { BackupFile.PocketData(it.position, it.cardId, it.isEmpty) },
                )
            }
            BackupFile.BinderData(
                binder.id, binder.name, binder.colorHex, binder.columns, binder.rows, binder.createdAt, pages,
                followedSets = binder.followedSets,
                countSecretRares = binder.countSecretRares,
            )
        }

        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFile.JSON_NAME))
            zip.write(BackupFile.toJson(data).toByteArray())
            zip.closeEntry()
            for ((name, file) in photos) {
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        Result(binders = data.size, pages = data.sumOf { it.pages.size })
    }

    /**
     * Reads a backup zip from [input] and adds its binders to [ownerId]'s
     * shelf. Binders that are already on this phone are left exactly as they
     * are (so restoring the same file twice never makes copies).
     *
     * Throws [BackupFile.NotABackup] if the file isn't a backup.
     */
    suspend fun restore(ownerId: String, input: InputStream): Result = withContext(Dispatchers.IO) {
        photoFolder.mkdirs()
        var json: String? = null
        val photos = mutableMapOf<String, File>() // zip name -> photo saved on the phone
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        entry.isDirectory -> Unit
                        name == BackupFile.JSON_NAME -> json = zip.readLimited(MAX_JSON_BYTES).decodeToString()
                        // Photos get a fresh random file name. The name inside
                        // the zip is only used to look them up, never as a path,
                        // so a strange zip can't write files anywhere else.
                        name.startsWith(BackupFile.PHOTO_FOLDER) && name !in photos -> {
                            val file = File(photoFolder, "${UUID.randomUUID()}.jpg")
                            file.outputStream().use { zip.copyTo(it) }
                            photos[name] = file
                        }
                    }
                }
            }
        } catch (e: Exception) {
            photos.values.forEach { it.delete() }
            if (e is BackupFile.NotABackup) throw e
            throw BackupFile.NotABackup("That file isn't a binder backup, or it is damaged.")
        }

        val used = mutableSetOf<String>()
        try {
            val binders = BackupFile.fromJson(json ?: throw BackupFile.NotABackup("That file isn't a binder backup."))
            var restored = 0
            var restoredPages = 0
            var skipped = 0
            for (b in binders) {
                if (binderDao.binderCount(b.id) > 0) {
                    skipped++
                    continue
                }
                val binder = Binder(
                    id = b.id,
                    ownerId = ownerId,
                    name = b.name,
                    colorHex = b.colorHex,
                    columns = b.columns,
                    rows = b.rows,
                    createdAt = b.createdAt,
                    followedSets = b.followedSets,
                    countSecretRares = b.countSecretRares,
                )
                val pages = mutableListOf<Page>()
                val pockets = mutableListOf<Pocket>()
                for (p in b.pages) {
                    // New page ids, so they can never clash with pages already here.
                    val pageId = UUID.randomUUID().toString()
                    val photo = p.photo?.let { photos[it] }
                    pages += Page(
                        id = pageId,
                        binderId = binder.id,
                        photoPath = photo?.absolutePath,
                        guideLeft = p.guideLeft,
                        guideTop = p.guideTop,
                        guideRight = p.guideRight,
                        guideBottom = p.guideBottom,
                        createdAt = p.createdAt,
                    )
                    // Every pocket the binder size has, filled from the backup where it says so.
                    val saved = p.pockets.associateBy { it.position }
                    for (position in 0 until binder.pocketsPerPage) {
                        val s = saved[position]
                        pockets += Pocket(pageId, position, cardId = s?.cardId, isEmpty = s?.isEmpty ?: false)
                    }
                }
                binderDao.restoreBinder(binder, pages, pockets)
                pages.mapNotNullTo(used) { it.photoPath }
                restored++
                restoredPages += pages.size
            }
            Result(binders = restored, pages = restoredPages, skipped = skipped)
        } finally {
            // Tidy up photos that didn't end up on a page (e.g. their binder was already here).
            photos.values.filter { it.absolutePath !in used }.forEach { it.delete() }
        }
    }

    private fun InputStream.readLimited(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > max) throw BackupFile.NotABackup("That backup is too big to read.")
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_JSON_BYTES = 20 * 1024 * 1024
    }
}
