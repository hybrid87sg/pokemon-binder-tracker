package com.hybrid87sg.bindertracker.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import com.hybrid87sg.bindertracker.data.CoverPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.data.CoverPlacement
import com.hybrid87sg.bindertracker.ui.theme.DisplayFont
import kotlin.math.max
import kotlin.math.roundToInt

/** What goes on a binder's cover: its colour, name, and your own picture if it has one. */
class CoverLook(
    val color: Color,
    val name: String,
    val picture: ImageBitmap? = null,
    val placement: CoverPlacement = CoverPlacement(),
)

/**
 * Shows a binder spine turned by [turn], with its cover folding into view
 * beside it. The spine itself (everything this modifier wraps) is drawn
 * normally into a hidden layer, then stretched onto its turned shape; the cover
 * is painted straight onto its turned shape.
 *
 * On the shelf the binder only takes up the width of its turned spine, so the
 * next binder stands in front of this one's cover, like on a real shelf.
 * Null [turn] (a straight shelf) changes nothing.
 */
@Composable
fun Modifier.turnedBinder(turn: BinderTurn?, cover: CoverLook): Modifier {
    if (turn == null) return this
    val layer = rememberGraphicsLayer()
    val measurer = rememberTextMeasurer()
    val matrix = remember { android.graphics.Matrix() }
    return this
        .layout { measurable, _ ->
            val w = turn.width.roundToInt()
            val h = turn.height.roundToInt()
            val placeable = measurable.measure(Constraints.fixed(w, h))
            val left = turn.spineLeft.roundToInt()
            layout(turn.spineWidth.roundToInt().coerceAtLeast(1), h) { placeable.place(-left, 0) }
        }
        .drawWithContent {
            // 1. The spine, drawn flat into the hidden layer.
            layer.record { this@drawWithContent.drawContent() }

            // 2. A soft shadow on the shelf under the binder and its cover.
            val far = turn.coverX(turn.depth)
            val from = minOf(turn.spineLeft, far)
            val to = maxOf(turn.spineRight, far)
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f), Color.Transparent),
                    startY = turn.height - 5.dp.toPx(),
                    endY = turn.height + 7.dp.toPx(),
                ),
                topLeft = Offset(from, turn.height - 5.dp.toPx()),
                size = Size(to - from, 12.dp.toPx()),
            )

            // 3. The cover, behind the spine.
            val src = floatArrayOf(0f, 0f, turn.depth, 0f, turn.depth, turn.height, 0f, turn.height)
            matrix.setPolyToPoly(src, 0, turn.coverCorners(), 0, 4)
            drawIntoCanvas { it.nativeCanvas.save(); it.nativeCanvas.concat(matrix) }
            drawCover(Size(turn.depth, turn.height), cover, hingeOnLeft = turn.coverOnRight, measurer = measurer, shade = turn.coverShade)
            drawIntoCanvas { it.nativeCanvas.restore() }

            // 4. The spine on top, stretched onto its turned shape and shaded.
            val spine = turn.spineCorners()
            matrix.setPolyToPoly(floatArrayOf(0f, 0f, turn.width, 0f, turn.width, turn.height, 0f, turn.height), 0, spine, 0, 4)
            drawIntoCanvas { it.nativeCanvas.save(); it.nativeCanvas.concat(matrix) }
            drawLayer(layer)
            drawIntoCanvas { it.nativeCanvas.restore() }
            if (turn.spineShade > 0.005f) {
                val path = Path().apply {
                    moveTo(spine[0], spine[1]); lineTo(spine[2], spine[3]); lineTo(spine[4], spine[5]); lineTo(spine[6], spine[7]); close()
                }
                drawPath(path, Color.Black.copy(alpha = turn.spineShade))
            }
        }
}

/**
 * Paints a binder cover filling [size]: the colour (or your picture), leather
 * grain, a padded sheen, stitching, a stamped name and an elastic strap.
 * [hingeOnLeft] is the side joined to the spine (the crease is there and the
 * strap is on the far edge). [shade] darkens it, for covers turned from the light.
 */
fun DrawScope.drawCover(
    size: Size,
    cover: CoverLook,
    hingeOnLeft: Boolean,
    measurer: TextMeasurer,
    shade: Float = 0f,
) {
    val w = size.width
    val h = size.height
    val small = 3.dp.toPx()
    val big = 8.dp.toPx()
    val hingeR = CornerRadius(small)
    val farR = CornerRadius(big)
    val shape = Path().apply {
        addRoundRect(
            if (hingeOnLeft) RoundRect(0f, 0f, w, h, hingeR, farR, farR, hingeR)
            else RoundRect(0f, 0f, w, h, farR, hingeR, hingeR, farR),
        )
    }
    // Left edge of something [width] wide that is [fromHinge] away from the hinge.
    fun left(fromHinge: Float, width: Float) = if (hingeOnLeft) fromHinge else w - fromHinge - width
    val pale = ColorMath.isLight(cover.color.toArgb())
    val picture = cover.picture
    clipPath(shape) {
        drawRect(cover.color, size = size)
        if (picture != null) drawCoverPicture(picture, cover.placement, size)

        // Leather grain (lighter over a picture, so the picture stays clear).
        drawRect(
            ShaderBrush(ImageShader(Textures.leather, TileMode.Repeated, TileMode.Repeated)),
            size = size,
            alpha = if (picture != null) 0.15f else 0.55f,
            blendMode = BlendMode.Multiply,
        )
        // Padded cover: a crease along the hinge, light in the middle, darker far edge.
        val f = { px: Float -> (px / w).coerceIn(0f, 1f) }
        val stops = arrayOf(
            0f to Color.Black.copy(alpha = 0.35f),
            f(8.dp.toPx()) to Color.Black.copy(alpha = 0.05f),
            f(14.dp.toPx()) to Color.White.copy(alpha = 0.18f),
            f(18.dp.toPx()) to Color.Black.copy(alpha = 0.18f),
            0.30f to Color.White.copy(alpha = 0.10f),
            0.60f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.22f),
        )
        drawRect(Brush.horizontalGradient(*stops, startX = if (hingeOnLeft) 0f else w, endX = if (hingeOnLeft) w else 0f), size = size)
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Black.copy(alpha = 0.14f))), size = size)

        // The binder's name stamped into the leather (not over a picture).
        if (picture == null) {
            val panelW = (w - 66.dp.toPx()).coerceAtLeast(0f)
            val panelH = 64.dp.toPx()
            val top = h * 0.34f
            val panelLeft = left(40.dp.toPx(), panelW)
            val r = CornerRadius(8.dp.toPx())
            drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(panelLeft, top), Size(panelW, panelH), r)
            drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(panelLeft, top), Size(panelW, panelH), r, style = Stroke(1.5.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = 0.16f), Offset(panelLeft, top + 1.5.dp.toPx()), Size(panelW, panelH), r, style = Stroke(1.dp.toPx()))
            val word = cover.name.trim().substringBefore(' ').uppercase()
            if (word.isNotEmpty() && panelW > 20.dp.toPx()) {
                val ink = if (pale) lerp(cover.color, Color(0xFF5A3A00), 0.5f) else lerp(cover.color, Color.Black, 0.45f)
                val text = measurer.measure(
                    word,
                    style = TextStyle(color = ink, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.4.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = (panelW - 12.dp.toPx()).roundToInt().coerceAtLeast(1)),
                )
                val tl = Offset(panelLeft + (panelW - text.size.width) / 2f, top + (panelH - text.size.height) / 2f)
                drawText(text, color = Color.White.copy(alpha = 0.22f), topLeft = tl + Offset(0f, 1.dp.toPx()))
                drawText(text, topLeft = tl)
            }
        }

        // Stitching round the edge (wider gap on the hinge side).
        val inset = 7.dp.toPx()
        val hingeInset = 22.dp.toPx()
        drawRoundRect(
            color = if (pale && picture == null) Color(0x593C2800) else Color.White.copy(alpha = 0.55f),
            topLeft = Offset(if (hingeOnLeft) hingeInset else inset, inset),
            size = Size(w - inset - hingeInset, h - inset * 2),
            cornerRadius = CornerRadius(5.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        )

        // The elastic strap near the far edge.
        val strapW = 12.dp.toPx()
        val strapLeft = left(w - 18.dp.toPx() - strapW, strapW)
        val band = lerp(cover.color, Color.Black, 0.6f)
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.45f),
                0.3f to band,
                0.7f to band,
                1f to Color.Black.copy(alpha = 0.45f),
                startX = strapLeft,
                endX = strapLeft + strapW,
            ),
            topLeft = Offset(strapLeft, 0f),
            size = Size(strapW, h),
        )

        if (shade > 0f) drawRect(Color.Black.copy(alpha = shade.coerceIn(0f, 1f)), size = size)
    }
}

/**
 * Draws [picture] so it covers all of [size] (cropping what hangs over), made
 * [CoverPlacement.zoom] times bigger and slid by [CoverPlacement.x]/[y].
 */
fun DrawScope.drawCoverPicture(picture: ImageBitmap, placement: CoverPlacement, size: Size) {
    val (dw, dh) = coverPictureSize(picture, placement.zoom, size)
    val left = -(dw - size.width) * placement.x
    val top = -(dh - size.height) * placement.y
    drawImage(
        picture,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(picture.width, picture.height),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.Medium,
    )
}

/** The drawn size of a cover picture that just covers [size], times [zoom]. */
fun coverPictureSize(picture: ImageBitmap, zoom: Float, size: Size): Pair<Float, Float> {
    val fit = max(size.width / picture.width, size.height / picture.height) * zoom
    return picture.width * fit to picture.height * fit
}

/** Loads cover pictures from the phone (off the main thread), by binder ID. */
@Composable
fun rememberCoverBitmaps(covers: Map<String, CoverPicture>): Map<String, ImageBitmap> {
    val loaded by produceState(emptyMap<String, ImageBitmap>(), covers) {
        value = withContext(Dispatchers.IO) {
            covers.mapNotNull { (id, c) -> loadCoverBitmap(c.path)?.let { id to it } }.toMap()
        }
    }
    return loaded
}

/** Reads a cover picture file (already shrunk when it was saved). */
fun loadCoverBitmap(path: String): ImageBitmap? =
    runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
