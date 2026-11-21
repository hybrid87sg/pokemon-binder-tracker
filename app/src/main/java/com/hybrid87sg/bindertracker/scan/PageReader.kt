package com.hybrid87sg.bindertracker.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where the page guide (the grid drawn over the camera) was on screen,
 * in screen pixels. Used to cut the same area out of the photo.
 */
data class GuideFrame(
    val viewWidth: Int,
    val viewHeight: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Reads a photo of a binder page: cuts out the page, asks ML Kit (Google's
 * on-phone text reader) for every line of text, then works out which pocket
 * each line belongs to and what card name and number it shows.
 */
class PageReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(photo: Bitmap, rotationDegrees: Int, guide: GuideFrame, columns: Int, rows: Int): List<ReadCard> {
        val page = withContext(Dispatchers.Default) { cropToGuide(upright(photo, rotationDegrees), guide) }
        val result = recognizer.process(InputImage.fromBitmap(page, 0)).await()
        val lines = result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                TextLine(line.text, box.left, box.top, box.right, box.bottom)
            }
        }
        val perPocket = CardTextParser.splitIntoPockets(lines, page.width, page.height, columns, rows)
        val pocketHeight = page.height / rows
        return perPocket.mapIndexed { position, pocketLines ->
            val row = position / columns
            CardTextParser.readPocket(position, pocketLines, pocketTop = row * pocketHeight, pocketHeight = pocketHeight)
        }
    }

    fun close() = recognizer.close()

    /** Phones store photos sideways; this turns it the right way up. */
    private fun upright(photo: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return photo
        val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(photo, 0, 0, photo.width, photo.height, m, true)
    }

    /**
     * The camera preview fills the screen by zooming in on the middle of the
     * photo ("centre crop"). So we work out how big the photo is compared to
     * the screen, then cut out the part of the photo that was inside the guide.
     */
    private fun cropToGuide(photo: Bitmap, guide: GuideFrame): Bitmap {
        if (guide.viewWidth <= 0 || guide.viewHeight <= 0) return photo
        val scale = max(guide.viewWidth.toFloat() / photo.width, guide.viewHeight.toFloat() / photo.height)
        val hiddenX = (photo.width - guide.viewWidth / scale) / 2f
        val hiddenY = (photo.height - guide.viewHeight / scale) / 2f
        val left = (guide.left / scale + hiddenX).roundToInt().coerceIn(0, photo.width - 1)
        val top = (guide.top / scale + hiddenY).roundToInt().coerceIn(0, photo.height - 1)
        val right = (guide.right / scale + hiddenX).roundToInt().coerceIn(left + 1, photo.width)
        val bottom = (guide.bottom / scale + hiddenY).roundToInt().coerceIn(top + 1, photo.height)
        return Bitmap.createBitmap(photo, left, top, right - left, bottom - top)
    }
}
