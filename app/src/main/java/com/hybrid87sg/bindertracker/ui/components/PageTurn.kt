package com.hybrid87sg.bindertracker.ui.components

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The maths for a binder sheet curling as it turns. Kept apart from the
 * drawing so it can be tested on a computer.
 *
 * Picture the sheet lying flat, hinged at the rings on its left edge (x = 0).
 * A fold travels from the right edge towards the rings. Left of the fold the
 * sheet still lies flat. At the fold it bends round a small roll of radius
 * [radius]. Past the roll it lies flat again, face down, on top of the page,
 * pointing back towards the rings. [progress] 0 = not turned, 1 = turned over.
 */
object PageCurl {
    /** Where the fold is, measured from the rings, for a sheet [width] wide. */
    fun fold(progress: Float, width: Float, radius: Float): Float =
        width - progress * (width + PI.toFloat() * radius)

    /** How far (in radians, 0 to pi) the sheet has turned at distance [s] along it. */
    fun angle(s: Float, fold: Float, radius: Float): Float =
        ((s - fold) / radius).coerceIn(0f, PI.toFloat())

    /** Where the point [s] along the sheet ends up: x across the screen, z lifted towards you. */
    fun position(s: Float, fold: Float, radius: Float): Pair<Float, Float> {
        val roll = PI.toFloat() * radius
        return when {
            s <= fold -> s to 0f
            s >= fold + roll -> (fold - (s - fold - roll)) to 2f * radius
            else -> {
                val a = (s - fold) / radius
                (fold + radius * sin(a)) to radius * (1f - cos(a))
            }
        }
    }

    /** The points along the sheet where it is cut into flat strips for drawing. */
    fun cuts(width: Float, fold: Float, radius: Float, steps: Int = 14): List<Float> {
        val roll = PI.toFloat() * radius
        val list = mutableListOf(0f, width)
        for (k in 0..steps) list += fold + roll * k / steps
        return list.filter { it in 0f..width }.sorted().distinct()
    }
}

/** Remembers whether a page is being turned, and which way. */
class PageTurnState {
    /** 1 = turning to the next page, -1 = back to the previous one, 0 = still. */
    var direction by mutableIntStateOf(0)
        internal set
    internal var progress by mutableFloatStateOf(0f)
    internal var picture by mutableStateOf<ImageBitmap?>(null)
    internal var request by mutableIntStateOf(0)
    internal var requestCount by mutableIntStateOf(0)
    internal var generation = 0

    /** Turn the page with an animation, as if flicked (used by the arrow buttons). */
    fun turn(direction: Int) {
        if (this.direction != 0) return
        request = direction
        requestCount++
    }
}

@Composable
fun rememberPageTurnState(): PageTurnState = remember { PageTurnState() }

/**
 * Shows one binder sheet, and turns it like a real binder page when dragged
 * sideways (or when [state].turn is called).
 *
 * [sheet] draws a page: 0 = this page, 1 = the next one, -1 = the previous one.
 * When a turn finishes, [onTurned] is called with 1 or -1, and the caller then
 * shows that page as page 0.
 */
@Composable
fun PageTurnStage(
    state: PageTurnState,
    canPrev: Boolean,
    canNext: Boolean,
    onTurned: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sheet: @Composable (which: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val turned by rememberUpdatedState(onTurned)
    val okPrev by rememberUpdatedState(canPrev)
    val okNext by rememberUpdatedState(canNext)

    fun allowed(d: Int) = (d > 0 && okNext) || (d < 0 && okPrev)

    // Start a turn: show the right pages, then take a picture of the sheet
    // that will curl (a picture bends smoothly; live buttons would not).
    suspend fun begin(d: Int) {
        val gen = ++state.generation
        state.picture = null
        state.progress = if (d > 0) 0f else 1f
        state.direction = d
        withFrameNanos { }
        withFrameNanos { }
        if (state.generation == gen && state.direction == d) {
            val shot = runCatching { layer.toImageBitmap() }.getOrNull()
            if (state.generation == gen && state.direction == d) state.picture = shot
        }
    }

    suspend fun finish(done: Boolean) {
        val d = state.direction
        if (d == 0) return
        val target = if (done == (d > 0)) 1f else 0f
        val distance = abs(target - state.progress)
        animate(
            initialValue = state.progress,
            targetValue = target,
            animationSpec = tween((950 * distance).toInt().coerceAtLeast(160), easing = FastOutSlowInEasing),
        ) { value, _ -> state.progress = value }
        if (done) turned(d)
        state.generation++
        state.direction = 0
        state.picture = null
        state.progress = 0f
    }

    // The arrow buttons.
    LaunchedEffect(state.requestCount) {
        val d = state.request
        if (d == 0 || state.direction != 0) return@LaunchedEffect
        if (!allowed(d)) return@LaunchedEffect
        begin(d)
        finish(true)
    }

    val tracker = remember { VelocityTracker() }
    Box(
        modifier.pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f; tracker.resetTracking() },
                onDragEnd = {
                    if (state.direction != 0) {
                        val v = tracker.calculateVelocity().x
                        val forward = state.direction > 0
                        val done = if (forward) state.progress > 0.4f || v < -900f else state.progress < 0.6f || v > 900f
                        scope.launch { finish(done) }
                    }
                },
                onDragCancel = { if (state.direction != 0) scope.launch { finish(false) } },
                onHorizontalDrag = { change, dx ->
                    total += dx
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (state.direction == 0) {
                        val d = if (total < 0) 1 else -1
                        if (allowed(d)) {
                            // Set the direction straight away, so a quick flick can't be missed.
                            state.direction = d
                            state.progress = if (d > 0) 0f else 1f
                            scope.launch { begin(d) }
                        }
                    }
                    if (state.direction != 0) {
                        val k = (abs(total) / (size.width * 1.05f)).coerceIn(0f, 1f)
                        state.progress = if (state.direction > 0) {
                            if (total < 0) k else 0f
                        } else {
                            if (total > 0) 1f - k else 1f
                        }
                        change.consume()
                    }
                },
            )
        },
    ) {
        val d = state.direction
        // Underneath: the page that is revealed (or simply this page when still).
        val under = when {
            d > 0 -> 1
            else -> 0
        }
        key(under) { sheet(under) }

        if (d != 0) {
            val curling = if (d > 0) 0 else -1
            // The sheet that curls is drawn into a hidden layer; we draw it ourselves, bent.
            Box(Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() } }) {
                key(curling) { sheet(curling) }
            }
            val sheetColor = AppColors.Surface
            val backPocket = AppColors.CardBack
            val holeColor = AppColors.Paper
            Canvas(Modifier.matchParentSize()) {
                val picture = state.picture
                if (picture == null) {
                    // Picture not taken yet (a frame or two): show the sheet flat.
                    if (d > 0) drawLayer(layer)
                } else {
                    drawCurl(picture, state.progress, sheetColor, backPocket, holeColor)
                }
            }
        }
        // The binder rings, on top of everything.
        Canvas(Modifier.matchParentSize()) { drawRings() }
    }
}

/** Where the three ring holes sit down the sheet, as fractions of its height. */
val RingSpots = listOf(0.17f, 0.5f, 0.83f)

private fun DrawScope.drawRings() {
    val w = 16.dp.toPx()
    val h = 18.dp.toPx()
    val left = -6.dp.toPx()
    RingSpots.forEach { f ->
        val top = size.height * f - h / 2
        drawRoundRect(
            color = Color(0xFF8D90A0),
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(h / 2),
            style = Stroke(3.dp.toPx()),
        )
        drawRoundRect(
            color = Color(0xFFD5D7E0),
            topLeft = Offset(left + 4.dp.toPx(), top + 1.dp.toPx()),
            size = Size(w - 6.dp.toPx(), h - 2.dp.toPx()),
            cornerRadius = CornerRadius(h / 2),
            style = Stroke(1.dp.toPx()),
        )
    }
}

/** Draws the turning sheet bent round its fold, plus the shadow it casts on the page below. */
private fun DrawScope.drawCurl(picture: ImageBitmap, progress: Float, sheetColor: Color, backPocket: Color, holeColor: Color) {
    val width = size.width
    val height = size.height
    val radius = 22.dp.toPx()
    val fold = PageCurl.fold(progress, width, radius)
    val camera = width * 6f
    val cx = width / 2f
    val cy = height / 2f
    fun project(x: Float, y: Float, z: Float): Offset {
        val scale = camera / (camera - z)
        return Offset(cx + (x - cx) * scale, cy + (y - cy) * scale)
    }

    // Soft shadow on the page underneath, just past the roll.
    val edge = fold + radius
    val strength = min(1f, min(progress * 6f, (1f - progress) * 6f)).coerceAtLeast(0f)
    if (strength > 0f && edge < width) {
        val shadowWidth = 40.dp.toPx()
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f * strength), Color.Transparent), startX = edge, endX = edge + shadowWidth),
            topLeft = Offset(edge.coerceAtLeast(0f), 0f),
            size = Size(min(shadowWidth, width - edge.coerceAtLeast(0f)), height),
        )
    }

    val bitmap = picture.asAndroidBitmap()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val matrix = Matrix()
    val cuts = PageCurl.cuts(width, fold, radius)
    drawIntoCanvas { c ->
        val canvas = c.nativeCanvas
        for (i in 0 until cuts.size - 1) {
            val s0 = cuts[i]
            val s1 = cuts[i + 1]
            if (s1 - s0 < 0.5f) continue
            val (x0, z0) = PageCurl.position(s0, fold, radius)
            val (x1, z1) = PageCurl.position(s1, fold, radius)
            val a0 = project(x0, 0f, z0)
            val a1 = project(x1, 0f, z1)
            val b1 = project(x1, height, z1)
            val b0 = project(x0, height, z0)
            val dst = floatArrayOf(a0.x, a0.y, a1.x, a1.y, b1.x, b1.y, b0.x, b0.y)
            val mid = PageCurl.angle((s0 + s1) / 2f, fold, radius)
            val front = mid < PI / 2
            // On the back the picture is mirrored: point s is at width - s.
            val u0 = if (front) s0 else width - s0
            val u1 = if (front) s1 else width - s1
            matrix.setPolyToPoly(floatArrayOf(u0, 0f, u1, 0f, u1, height, u0, height), 0, dst, 0, 4)
            val clip = RectF(min(u0, u1) - 0.6f, 0f, maxOf(u0, u1) + 0.6f, height)
            canvas.save()
            canvas.concat(matrix)
            canvas.clipRect(clip)
            if (front) {
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            } else {
                drawSheetBack(canvas, width, height, fill, sheetColor, backPocket, holeColor)
            }
            // Darker where the sheet is tilted away from the light.
            val shade = sin(mid) * if (front) 0.42f else 0.3f
            if (shade > 0.01f) {
                fill.color = Color.Black.copy(alpha = shade).toArgb()
                canvas.drawRect(clip, fill)
            }
            canvas.restore()
        }
    }
}

/** The back of a sheet: the reverse pockets seen through the plastic, holes on the right. */
private fun DrawScope.drawSheetBack(
    canvas: android.graphics.Canvas,
    width: Float,
    height: Float,
    fill: Paint,
    sheetColor: Color,
    pocket: Color,
    hole: Color,
) {
    fill.color = sheetColor.toArgb()
    canvas.drawRoundRect(RectF(0f, 0f, width, height), 14.dp.toPx(), 14.dp.toPx(), fill)
    val pad = 10.dp.toPx()
    val holeSide = 26.dp.toPx()
    val gap = 8.dp.toPx()
    val cols = 3
    val cellW = (width - pad - holeSide - gap * (cols - 1)) / cols
    val cellH = cellW * 88f / 63f
    val rows = ((height - pad) / (cellH + gap)).toInt().coerceAtLeast(1)
    fill.color = pocket.copy(alpha = 0.45f).toArgb()
    for (r in 0 until rows) for (col in 0 until cols) {
        val left = pad + col * (cellW + gap)
        val top = pad + r * (cellH + gap)
        if (top + cellH > height) continue
        canvas.drawRoundRect(RectF(left, top, left + cellW, top + cellH), 6.dp.toPx(), 6.dp.toPx(), fill)
    }
    fill.color = hole.toArgb()
    RingSpots.forEach { f -> canvas.drawCircle(width - 12.dp.toPx(), height * f, 4.5.dp.toPx(), fill) }
}
