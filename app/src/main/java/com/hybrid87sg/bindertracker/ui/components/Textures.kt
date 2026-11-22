package com.hybrid87sg.bindertracker.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.util.Random

/**
 * Small drawn textures that make the app feel like real things: binder
 * covers, pocket sleeves and the shelf. All made in code, no pictures.
 */
object Textures {

    /**
     * A small square of soft grey speckles. Tiled over a binder cover (with
     * "multiply", which only darkens) it looks like leatherette grain.
     * Made once, the same every time.
     */
    val leather: ImageBitmap by lazy {
        val n = 128
        val rnd = Random(4)
        val raw = FloatArray(n * n) { rnd.nextFloat() }
        val pixels = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            // Blur the random dots a little so they clump into a grain.
            var sum = 0f
            for (dy in -1..1) for (dx in -1..1) sum += raw[((y + dy + n) % n) * n + (x + dx + n) % n]
            val v = 0.82f + (sum / 9f - 0.5f) * 0.9f + (raw[y * n + x] - 0.5f) * 0.18f
            val g = (v.coerceIn(0f, 1f) * 255).toInt()
            pixels[y * n + x] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/**
 * Binder cover texture, drawn over the cover colour and under its label:
 * leatherette grain, a rounded sheen (darker edges, a soft highlight) and a
 * stitched edge. [pale] covers get darker stitching so it still shows.
 */
fun Modifier.leatherCover(pale: Boolean): Modifier = drawBehind {
    drawRect(ShaderBrush(ImageShader(Textures.leather, TileMode.Repeated, TileMode.Repeated)), alpha = 0.55f, blendMode = BlendMode.Multiply)
    // The cover is padded, so it catches the light in the middle and darkens at the edges.
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.28f),
            0.14f to Color.Transparent,
            0.30f to Color.White.copy(alpha = 0.22f),
            0.46f to Color.Transparent,
            0.80f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.30f),
        ),
    )
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.12f))))
    // Stitching round the edge.
    val inset = 4.dp.toPx()
    drawRoundRect(
        color = if (pale) Color(0x593C2800) else Color.White.copy(alpha = 0.55f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(4.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** Fine wood grain lines and a soft top light, over the shelf plank's colour. */
fun Modifier.woodGrain(): Modifier = drawBehind {
    val step = 9.dp.toPx()
    val line = 1.dp.toPx()
    var y = step / 2
    var k = 0
    while (y < size.height) {
        // Each line runs a little uphill or downhill, like real grain.
        val tilt = if (k % 2 == 0) line else -line
        drawLine(Color.Black.copy(alpha = 0.07f), Offset(0f, y), Offset(size.width, y + tilt), strokeWidth = line)
        y += step * 0.5f
        k++
    }
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.08f))))
}
