package com.hybrid87sg.bindertracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where a cover picture sits on the cover. [x] and [y] go from 0 (picture
 * pushed to the left/top edge) to 1 (right/bottom edge), 0.5 = centred.
 * [zoom] 1 means the picture just fills the cover; up to 3 is allowed.
 */
data class CoverPlacement(val x: Float = 0.5f, val y: Float = 0.5f, val zoom: Float = 1f) {
    fun clamped() = CoverPlacement(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), zoom.coerceIn(1f, MAX_ZOOM))

    companion object {
        const val MAX_ZOOM = 3f
    }
}

/** A binder's own cover picture: the file on this phone and where it sits. */
data class CoverPicture(val path: String, val placement: CoverPlacement, val version: Long)

/**
 * Your own pictures on binder covers. They stay on this phone (in the app's
 * private "cover-photos" folder, with the placement in the "covers" settings
 * file). Sending pictures to friends would need Firebase Storage, which is
 * only on Google's paid plan, so shared friends see the plain leather cover.
 */
class CoverPictures(private val context: Context) {
    private val dir = File(context.filesDir, "cover-photos")
    private val prefs = context.getSharedPreferences("covers", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(load())

    /** Binder ID -> its cover picture. Binders without one aren't listed. */
    val all: StateFlow<Map<String, CoverPicture>> = _all.asStateFlow()

    /**
     * Copies a photo you picked into a temporary file, turned the right way up
     * and shrunk so it isn't huge. Returns the file's path, or null if the photo
     * couldn't be read. Nothing is kept until [save].
     */
    suspend fun importDraft(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val raw = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            // Phone cameras save photos sideways and note the turn needed in the "EXIF" data.
            val degrees = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val big = maxOf(raw.width, raw.height)
            val shrink = if (big > MAX_SIDE) MAX_SIDE.toFloat() / big else 1f
            val m = Matrix().apply { postScale(shrink, shrink); postRotate(degrees) }
            val upright = if (degrees == 0f && shrink == 1f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            val out = File(context.cacheDir, "cover-draft-${System.currentTimeMillis()}.jpg")
            out.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            out.absolutePath
        }.getOrNull()
    }

    /**
     * Saves [binderId]'s cover. [path] is a picture from [importDraft], or the
     * binder's current picture (then only the placement changes).
     */
    suspend fun save(binderId: String, path: String, placement: CoverPlacement) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = fileFor(binderId)
        if (File(path).absolutePath != target.absolutePath) {
            File(path).copyTo(target, overwrite = true)
            File(path).delete()
        }
        val p = placement.clamped()
        val version = System.currentTimeMillis()
        prefs.edit()
            .putFloat(binderId + X, p.x).putFloat(binderId + Y, p.y).putFloat(binderId + ZOOM, p.zoom)
            .putLong(binderId + VERSION, version)
            .apply()
        _all.value = _all.value + (binderId to CoverPicture(target.absolutePath, p, version))
    }

    /** Takes the picture off [binderId]'s cover (back to plain leather). */
    suspend fun remove(binderId: String) = withContext(Dispatchers.IO) {
        fileFor(binderId).delete()
        prefs.edit().remove(binderId + X).remove(binderId + Y).remove(binderId + ZOOM).remove(binderId + VERSION).apply()
        _all.value = _all.value - binderId
    }

    /** Throws away a picture from [importDraft] that wasn't saved. */
    fun discardDraft(path: String?) {
        if (path != null && path.startsWith(context.cacheDir.absolutePath)) File(path).delete()
    }

    private fun fileFor(binderId: String) = File(dir, binderId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".jpg")

    private fun load(): Map<String, CoverPicture> = prefs.all.keys
        .filter { it.endsWith(VERSION) }
        .map { it.removeSuffix(VERSION) }
        .mapNotNull { id ->
            val file = fileFor(id)
            if (!file.exists()) return@mapNotNull null
            id to CoverPicture(
                path = file.absolutePath,
                placement = CoverPlacement(prefs.getFloat(id + X, 0.5f), prefs.getFloat(id + Y, 0.5f), prefs.getFloat(id + ZOOM, 1f)).clamped(),
                version = prefs.getLong(id + VERSION, 0L),
            )
        }.toMap()

    private companion object {
        const val MAX_SIDE = 1200
        const val X = ".x"
        const val Y = ".y"
        const val ZOOM = ".zoom"
        const val VERSION = ".version"
    }
}
