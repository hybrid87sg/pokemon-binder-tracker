package com.hybrid87sg.bindertracker.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hybrid87sg.bindertracker.GuideRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Reads a saved page photo: cuts out the frame, asks ML Kit (Google's
 * on-phone text reader, works offline) for every line of text, then works out
 * which pocket each line is in and which card name and number it shows.
 * It also keeps a small copy of each pocket's picture, for recognising by picture.
 */
object PageReader {

    // Height of the pocket pictures kept for recognising (the AI model
    // looks at 224 x 224, so more detail than this isn't needed).
    private const val PICTURE_HEIGHT = 336

    suspend fun read(photoPath: String, guide: GuideRect, columns: Int, rows: Int): List<ScannedPocket> {
        val page = withContext(Dispatchers.IO) { loadFrame(photoPath, guide) } ?: return emptyList()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(InputImage.fromBitmap(page, 0)).await()
            val lines = result.textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    TextLine(line.text, box.left, box.top, box.right, box.bottom)
                }
            }
            val perPocket = CardTextParser.splitIntoPockets(lines, page.width, page.height, columns, rows)
            val pocketWidth = page.width / columns
            val pocketHeight = page.height / rows
            return perPocket.mapIndexed { position, pocketLines ->
                val read = CardTextParser.readPocket(position, pocketLines, pocketTop = (position / columns) * pocketHeight, pocketHeight = pocketHeight)
                // The pocket's own bit of the photo, shrunk, for recognising by picture.
                val picture = runCatching {
                    val pocket = Bitmap.createBitmap(page, (position % columns) * pocketWidth, (position / columns) * pocketHeight, pocketWidth, pocketHeight)
                    val scale = PICTURE_HEIGHT.toFloat() / pocket.height
                    if (scale >= 1f) pocket else Bitmap.createScaledBitmap(pocket, (pocket.width * scale).toInt().coerceAtLeast(1), PICTURE_HEIGHT, true)
                }.getOrNull()
                ScannedPocket(read, picture)
            }
        } finally {
            recognizer.close()
        }
    }

    /**
     * Loads the photo the right way up and keeps only the part inside the
     * frame. Small print (the card number) needs detail, so it's loaded big,
     * but shrunk enough to fit in the phone's memory.
     */
    private fun loadFrame(path: String, guide: GuideRect, maxSide: Int = 4000): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val degrees = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val photo = if (degrees == 0f) raw else
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
        val left = (guide.left * photo.width).toInt().coerceIn(0, photo.width - 1)
        val top = (guide.top * photo.height).toInt().coerceIn(0, photo.height - 1)
        val right = (guide.right * photo.width).toInt().coerceIn(left + 1, photo.width)
        val bottom = (guide.bottom * photo.height).toInt().coerceIn(top + 1, photo.height)
        return Bitmap.createBitmap(photo, left, top, right - left, bottom - top)
    }
}

/** What was found in one pocket: the text read, and its picture (null if cutting it out failed). */
data class ScannedPocket(val read: ReadCard, val picture: Bitmap?)
