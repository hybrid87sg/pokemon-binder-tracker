package com.hybrid87sg.bindertracker.ui.components

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/*
 * The open binder, for big screens such as an unfolded phone: two sheets side
 * by side with the rings down the middle. A whole spread turns at once: the
 * right sheet curls over the rings and lands on the left, showing the next
 * left page on its back (or the left sheet curls over to the right, going back).
 */

/** The gap down the middle of the open binder, where the rings sit. */
val SpineGap = 16.dp

/** Which side a sheet is on (0 = left, 1 = right), whether it is a real page, and whether it is kept out of sight. */
private data class SpreadSlot(val side: Int, val page: Boolean, val away: Boolean = false)

/**
 * Shows two binder sheets side by side and turns them a spread at a time when
 * dragged sideways (or when [state].turn is called, for the arrow buttons).
 *
 * [sheet] draws one sheet: [which] is 0 for this spread, 1 for the next one and
 * -1 for the previous one; [side] is 0 for the left sheet and 1 for the right.
 * [isPage] says whether that spot holds a real page (the others, such as the
 * empty space after the last page, are stretched to the height of the pages).
 * When a turn finishes, [onTurned] is called with 1 or -1.
 */
@Composable
fun SpreadTurnStage(
    state: PageTurnState,
    canPrev: Boolean,
    canNext: Boolean,
    onTurned: (Int) -> Unit,
    isPage: (which: Int, side: Int) -> Boolean,
    // A name for each sheet (its page ID), so a sheet is never rebuilt just
    // because it moved from the next spread to this one.
    keyOf: (which: Int, side: Int) -> Any,
    // The binder's own colour, peeking out round the pages and down the spine.
    coverColor: Color,
    modifier: Modifier = Modifier,
    // A sheet that has to stay alive although it was turned away: the page a
    // card is being dragged from (its finger is still being followed there).
    // Given as (which, side), like [sheet]; it is kept out of sight.
    keep: Pair<Int, Int>? = null,
    // Glows the right (1) or left (-1) edge: a dragged card is held there and the pages will turn.
    edgeGlow: Int = 0,
    sheet: @Composable (which: Int, side: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // The sheet that curls (its front) and the page printed on its back.
    val frontLayer = rememberGraphicsLayer()
    val backLayer = rememberGraphicsLayer()
    var backPicture by remember { mutableStateOf<ImageBitmap?>(null) }
    val turned by rememberUpdatedState(onTurned)
    val okPrev by rememberUpdatedState(canPrev)
    val okNext by rememberUpdatedState(canNext)

    fun allowed(d: Int) = (d > 0 && okNext) || (d < 0 && okPrev)
    // A turn is being finished: nothing else may move it or finish it again.
    var finishing by remember { mutableStateOf(false) }

    // Here progress always runs 0 (flat) to 1 (turned over), in both directions.
    suspend fun begin(d: Int) {
        val gen = ++state.generation
        state.picture = null
        backPicture = null
        state.direction = d
        withFrameNanos { }
        withFrameNanos { }
        if (state.generation == gen && state.direction == d) {
            val front = runCatching { frontLayer.toImageBitmap() }.getOrNull()
            val back = runCatching { backLayer.toImageBitmap() }.getOrNull()
            if (state.generation == gen && state.direction == d) {
                backPicture = back
                state.picture = front
            }
        }
    }

    suspend fun finish(done: Boolean) {
        val d = state.direction
        if (d == 0 || finishing) return
        finishing = true
        val target = if (done) 1f else 0f
        val distance = abs(target - state.progress)
        animate(
            initialValue = state.progress,
            targetValue = target,
            animationSpec = tween((1000 * distance).toInt().coerceAtLeast(160), easing = FastOutSlowInEasing),
        ) { value, _ -> state.progress = value }
        // Each turn is committed once, by the one finish that owns it.
        if (done) turned(d)
        state.generation++
        state.direction = 0
        state.picture = null
        backPicture = null
        state.progress = 0f
        finishing = false
    }

    // The arrow buttons.
    LaunchedEffect(state.requestCount) {
        val d = state.request
        if (d == 0 || state.direction != 0) return@LaunchedEffect
        if (!allowed(d)) return@LaunchedEffect
        state.progress = 0f
        begin(d)
        finish(true)
    }

    val tracker = remember { VelocityTracker() }
    Box(
        modifier
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f; tracker.resetTracking() },
                    onDragEnd = {
                        if (state.direction != 0 && !finishing) {
                            val v = tracker.calculateVelocity().x * -state.direction
                            scope.launch { finish(state.progress > 0.4f || v > 900f) }
                        }
                    },
                    onDragCancel = { if (state.direction != 0 && !finishing) scope.launch { finish(false) } },
                    onHorizontalDrag = { change, dx ->
                        total += dx
                        tracker.addPosition(change.uptimeMillis, change.position)
                        // A turn already on its way (e.g. from the arrows) isn't the finger's to move.
                        if (finishing) return@detectHorizontalDragGestures
                        if (state.direction == 0) {
                            // Drag left = forward (the right page turns over), drag right = back.
                            val d = if (total < 0) 1 else -1
                            if (allowed(d)) {
                                state.direction = d
                                state.progress = 0f
                                scope.launch { begin(d) }
                            }
                        }
                        if (state.direction != 0) {
                            val sheetWidth = (size.width - SpineGap.toPx()) / 2f
                            val along = -total * state.direction
                            state.progress = (along / (sheetWidth * 1.6f)).coerceIn(0f, 1f)
                            change.consume()
                        }
                    },
                )
            }
            .drawBehind {
                // The binder cover round the pages, darker at the spine.
                drawRoundRect(coverColor, cornerRadius = CornerRadius(14.dp.toPx()))
                val mid = size.width / 2f
                val spine = SpineGap.toPx() + 12.dp.toPx()
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.3f), Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.3f)),
                        startX = mid - spine / 2f, endX = mid + spine / 2f,
                    ),
                    topLeft = Offset(mid - spine / 2f, 0f),
                    size = Size(spine, size.height),
                )
            }
            .padding(6.dp),
    ) {
        val d = state.direction
        // Which sheets are drawn, and where. Every sheet goes through one keyed
        // loop, so a sheet you turn to carries on as it is (no rebuild, no cards
        // popping in again). Curling sheets are only recorded into a picture.
        val entries: List<Triple<Int, Int, Int>> = when {
            d > 0 -> listOf(Triple(0, 0, 0), Triple(1, 1, 0), Triple(0, 1, FRONT), Triple(1, 0, BACK))
            d < 0 -> listOf(Triple(0, 1, 0), Triple(-1, 0, 0), Triple(0, 0, FRONT), Triple(-1, 1, BACK))
            else -> listOf(Triple(0, 0, 0), Triple(0, 1, 0))
        }.let { list ->
            if (keep != null && list.none { it.first == keep.first && it.second == keep.second }) {
                list + Triple(keep.first, keep.second, AWAY)
            } else list
        }
        Layout(
            content = {
                entries.forEach { (which, side, role) ->
                    key(keyOf(which, side)) {
                        val record = when (role) {
                            FRONT -> Modifier.drawWithContent { frontLayer.record { this@drawWithContent.drawContent() } }
                            BACK -> Modifier.drawWithContent { backLayer.record { this@drawWithContent.drawContent() } }
                            AWAY -> Modifier.drawWithContent { }
                            else -> Modifier
                        }
                        Box(Modifier.layoutId(SpreadSlot(side, isPage(which, side), away = role == AWAY)).then(record)) {
                            sheet(which, side)
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val gap = SpineGap.roundToPx()
            val width = ((constraints.maxWidth - gap) / 2).coerceAtLeast(0)
            val placed = arrayOfNulls<Placeable>(measurables.size)
            val slots = measurables.map { it.layoutId as SpreadSlot }
            // Real pages first, so the other spots can match their height.
            measurables.forEachIndexed { i, m ->
                if (slots[i].page) placed[i] = m.measure(Constraints(minWidth = width, maxWidth = width))
            }
            val height = placed.filterIndexed { i, _ -> !slots[i].away }.maxOfOrNull { it?.height ?: 0 } ?: 0
            measurables.forEachIndexed { i, m ->
                if (!slots[i].page) placed[i] = m.measure(Constraints.fixed(width, height))
            }
            layout(constraints.maxWidth, height) {
                placed.forEachIndexed { i, p ->
                    // A sheet kept out of sight sits far off the screen, where it can't be touched.
                    val x = if (slots[i].away) -100_000 else if (slots[i].side == 0) 0 else width + gap
                    p?.place(x, 0)
                }
            }
        }

        if (d != 0) {
            val sheetColor = AppColors.Surface
            Canvas(Modifier.matchParentSize()) {
                val gap = SpineGap.toPx()
                val width = (size.width - gap) / 2f
                val front = state.picture
                if (front == null) {
                    // Pictures not taken yet (a frame or two): show the sheet flat where it is.
                    translate(left = if (d > 0) width + gap else 0f) { drawLayer(frontLayer) }
                } else {
                    withTransform({ if (d < 0) scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) }) {
                        drawSpreadCurl(front, backPicture, state.progress, width, gap, mirrored = d < 0, sheetColor = sheetColor)
                    }
                }
            }
        }
        // The rings, across the middle, on top of everything.
        Canvas(Modifier.matchParentSize()) { drawSpineRings() }
        if (edgeGlow != 0) {
            val pulse by rememberInfiniteTransition(label = "edge").animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                label = "edgePulse",
            )
            val glow = AppColors.AccentText
            Canvas(Modifier.matchParentSize()) {
                val w = 36.dp.toPx()
                val right = edgeGlow > 0
                val x0 = if (right) size.width - w else 0f
                drawRect(
                    Brush.horizontalGradient(
                        if (right) listOf(Color.Transparent, glow.copy(alpha = 0.5f * pulse)) else listOf(glow.copy(alpha = 0.5f * pulse), Color.Transparent),
                        startX = x0, endX = x0 + w,
                    ),
                    topLeft = Offset(x0, 0f),
                    size = Size(w, size.height),
                )
            }
        }
    }
}

private const val FRONT = 1
private const val BACK = 2
private const val AWAY = 3

/** Rings that run across the spine, from the holes in the left page to the holes in the right. */
private fun DrawScope.drawSpineRings() {
    val mid = size.width / 2f
    val w = SpineGap.toPx() + 30.dp.toPx()
    val h = 16.dp.toPx()
    RingSpots.forEach { f ->
        val top = size.height * f - h / 2
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.18f),
            topLeft = Offset(mid - w / 2, top + 2.dp.toPx()),
            size = Size(w, h),
            cornerRadius = CornerRadius(h / 2),
            style = Stroke(3.dp.toPx()),
        )
        drawRoundRect(
            color = Color(0xFF8D90A0),
            topLeft = Offset(mid - w / 2, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(h / 2),
            style = Stroke(3.dp.toPx()),
        )
        drawRoundRect(
            color = Color(0xFFD5D7E0),
            topLeft = Offset(mid - w / 2 + 3.dp.toPx(), top + 1.dp.toPx()),
            size = Size(w - 6.dp.toPx(), h - 2.dp.toPx()),
            cornerRadius = CornerRadius(h / 2),
            style = Stroke(1.dp.toPx()),
        )
    }
}

/**
 * Draws the right sheet (hinged at the spine) curling over to the left, with
 * [back] printed on its reverse. When [mirrored], the caller has flipped the
 * canvas so the left sheet can curl to the right with the same maths; the
 * pictures are then read from the other side so they still come out the right
 * way round. [progress] 0 = flat, 1 = lying exactly on the other page.
 */
private fun DrawScope.drawSpreadCurl(
    front: ImageBitmap,
    back: ImageBitmap?,
    progress: Float,
    width: Float,
    gap: Float,
    mirrored: Boolean,
    sheetColor: Color,
) {
    val height = size.height
    val hinge = width + gap
    // The roll tightens as the sheet comes down, so it lands flat.
    val radius = 20.dp.toPx() * (1f - 0.75f * progress)
    val roll = PI.toFloat() * radius
    // Where the fold ends up so the turned sheet lies exactly on the other page.
    val endFold = -(gap + roll) / 2f
    val fold = width - progress * (width - endFold)
    val camera = size.width * 4f
    val cx = size.width / 2f
    val cy = height / 2f
    fun project(x: Float, y: Float, z: Float): Offset {
        val scale = camera / (camera - z)
        return Offset(cx + (hinge + x - cx) * scale, cy + (y - cy) * scale)
    }

    // Soft shadow on the page underneath, just past the roll.
    val edge = fold + radius
    val strength = min(1f, min(progress * 6f, (1f - progress) * 6f)).coerceAtLeast(0f)
    if (strength > 0f && edge < width && edge > 0f) {
        val shadowWidth = 40.dp.toPx()
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f * strength), Color.Transparent), startX = hinge + edge, endX = hinge + edge + shadowWidth),
            topLeft = Offset(hinge + edge, 0f),
            size = Size(min(shadowWidth, width - edge), height),
        )
    }

    val frontBitmap = front.asAndroidBitmap()
    val backBitmap = back?.asAndroidBitmap()
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
            val onFront = mid < PI / 2
            // A sheet hinged on its left reads point s at x = s; one hinged on
            // its right reads it at x = width - s. The front is hinged on the
            // left and the back on the right (swapped when mirrored).
            val hingedLeft = onFront != mirrored
            val u0 = if (hingedLeft) s0 else width - s0
            val u1 = if (hingedLeft) s1 else width - s1
            matrix.setPolyToPoly(floatArrayOf(u0, 0f, u1, 0f, u1, height, u0, height), 0, dst, 0, 4)
            val clip = RectF(min(u0, u1) - 0.6f, 0f, maxOf(u0, u1) + 0.6f, height)
            canvas.save()
            canvas.concat(matrix)
            canvas.clipRect(clip)
            val bitmap = if (onFront) frontBitmap else backBitmap
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            } else {
                fill.color = sheetColor.toArgb()
                canvas.drawRect(clip, fill)
            }
            // Darker where the sheet is tilted away from the light.
            val shade = sin(mid) * if (onFront) 0.42f else 0.3f
            if (shade > 0.01f) {
                fill.color = Color.Black.copy(alpha = shade).toArgb()
                canvas.drawRect(clip, fill)
            }
            canvas.restore()
        }
    }
}
