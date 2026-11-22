package com.hybrid87sg.bindertracker.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Your own photo of a card, used as its picture when no website has one yet.
 *
 * Brand-new sets (like 30th Celebration Classic Collection) can take weeks
 * to get pictures on TCGdex or pokemontcg.io. When you scan a card, the app
 * keeps the straightened photo, so the card shows your photo instead of a
 * blank. Once an official picture exists, that one is shown instead.
 * Photos stay on this phone only.
 */
object OwnPhotos {
    private const val FOLDER = "card-photos"
    private const val MAX_HEIGHT = 700

    // Goes up each time a photo is saved, so card pictures on screen look again.
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Where a card's photo is kept, e.g. card-photos/me02.5-017.jpg. */
    fun file(context: Context, cardId: String): File =
        File(File(context.filesDir, FOLDER), fileName(cardId))

    /** The card's photo, or null if you haven't scanned it. */
    fun find(context: Context, cardId: String?): File? =
        cardId?.let { file(context, it) }?.takeIf { it.isFile }

    /** Keeps [photo] as [cardId]'s picture (replacing an older one). */
    fun save(context: Context, cardId: String, photo: Bitmap) {
        val scaled = if (photo.height > MAX_HEIGHT) {
            Bitmap.createScaledBitmap(photo, photo.width * MAX_HEIGHT / photo.height, MAX_HEIGHT, true)
        } else photo
        val target = file(context, cardId)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        temp.renameTo(target)
        _version.value++
    }

    /** Card ids can hold characters a file name can't, so keep only safe ones. */
    fun fileName(cardId: String): String = cardId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".jpg"
}
