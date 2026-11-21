package com.hybrid87sg.bindertracker.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.exifinterface.media.ExifInterface
import com.hybrid87sg.bindertracker.GuideRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Loads a page photo in the background, shrunk to a sensible size and turned upright. */
@Composable
fun rememberPagePhoto(path: String?): State<ImageBitmap?> = produceState<ImageBitmap?>(null, path) {
    value = path?.let { withContext(Dispatchers.IO) { loadUpright(it, maxSide = 2000) } }
}

private fun loadUpright(path: String, maxSide: Int): ImageBitmap? {
    if (!File(path).exists()) return null
    // First read only the size, then load at a fraction of it to save memory.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null

    // Cameras save photos sideways and note the turn needed in the "EXIF" data.
    val degrees = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val upright = if (degrees == 0f) bitmap else
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    return upright.asImageBitmap()
}

/**
 * Shows one pocket's part of the page photo. The frame ([guide]) is split
 * into [columns] × [rows] equal boxes; [position] counts left to right, top to bottom.
 */
@Composable
fun PocketCrop(
    photo: ImageBitmap,
    guide: GuideRect,
    columns: Int,
    rows: Int,
    position: Int,
    modifier: Modifier = Modifier,
) {
    val w = photo.width
    val h = photo.height
    val col = position % columns
    val row = position / columns
    val frameLeft = guide.left * w
    val frameTop = guide.top * h
    val cellW = (guide.right - guide.left) * w / columns
    val cellH = (guide.bottom - guide.top) * h / rows
    val x = (frameLeft + col * cellW).toInt().coerceIn(0, w - 1)
    val y = (frameTop + row * cellH).toInt().coerceIn(0, h - 1)
    val size = IntSize(cellW.toInt().coerceIn(1, w - x), cellH.toInt().coerceIn(1, h - y))
    Image(
        painter = BitmapPainter(photo, IntOffset(x, y), size),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
