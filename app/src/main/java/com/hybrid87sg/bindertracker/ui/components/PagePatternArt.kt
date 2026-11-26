package com.hybrid87sg.bindertracker.ui.components

import android.provider.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.ui.theme.AppColourMath
import com.hybrid87sg.bindertracker.ui.theme.AppColourSetting
import com.hybrid87sg.bindertracker.ui.theme.BallColour
import com.hybrid87sg.bindertracker.ui.theme.BallStyle
import com.hybrid87sg.bindertracker.ui.theme.ColourChoice
import com.hybrid87sg.bindertracker.ui.theme.PagePattern
import com.hybrid87sg.bindertracker.ui.theme.PagePatternMath
import kotlin.math.max
import kotlin.math.min

/** The colours for words written on a binder page (the card names and numbers under the cards). */
data class OnPage(val ink: Color, val muted: Color)

/** What the binder page under the cards is like, so their names stay easy to read on it. Null = the plain light page. */
val LocalOnPage = staticCompositionLocalOf<OnPage?> { null }

/** The page colour, as a Compose colour. */
fun PagePattern.pageColor(): Color = Color(PagePatternMath.pageColour(page))

/** Light words on a dark page, dark words on a light one. */
fun PagePattern.onPage(): OnPage =
    if (pageColor().luminance() > 0.18f) OnPage(Color(0xFF15131C), Color(0xFF4A4756))
    else OnPage(Color(0xFFF2F2F5), Color(0xFFB7BAC4))

/** The ball colour for "App colour": your main app colour, made light and clear enough to show on a dark page. */
fun appBallColour(): Color {
    val main = AppColourSetting.colours?.firstOrNull() ?: AppColourMath.CLASSIC
    return Color(PagePatternMath.ballColour(ColourChoice(main.hue, max(main.strength, 0.6f))))
}

/** How a ball colour choice looks (red and white shows as white: the red is added when drawing). */
fun ballColor(c: BallColour): Color = when (c) {
    BallColour.App -> appBallColour()
    BallColour.RedWhite -> Color.White
    is BallColour.Pick -> Color(PagePatternMath.ballColour(c.choice))
}

private val ClassicRed = Color(PagePatternMath.CLASSIC_RED)

/**
 * Fills the binder page with its Poké Ball pattern, behind the cards.
 * With Slow drift on, the balls glide sideways (they stay still when the
 * phone's "Remove animations" is on).
 */
fun Modifier.pagePattern(pattern: PagePattern): Modifier = composed {
    val context = LocalContext.current
    val animationsOff = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val ball = ballColor(pattern.ball)
    if (pattern.drift && !animationsOff) {
        // 5 dp a second, read only while drawing so nothing else on the page redraws.
        var seconds by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { seconds = (it - start) / 1_000_000_000f }
        }
        drawWithCache {
            onDrawBehind { drawPagePattern(pattern, ball, (seconds * 5f).dp.toPx()) }
        }
    } else {
        drawWithCache {
            // Where the balls go is worked out once for this page size, then just drawn.
            val spots = PagePatternMath.spots(pattern, size.width, size.height, pattern.size.dp.toPx(), pattern.gap.dp.toPx())
            onDrawBehind { drawPagePattern(pattern, ball, spots = spots) }
        }
    }
}

/** Draws the page colour and every ball. [shift] slides the pattern sideways (slow drift). */
fun DrawScope.drawPagePattern(
    p: PagePattern,
    ball: Color,
    shift: Float = 0f,
    spots: List<PagePatternMath.Spot> = PagePatternMath.spots(p, size.width, size.height, p.size.dp.toPx(), p.gap.dp.toPx(), shift),
) {
    val page = p.pageColor()
    drawRect(page)
    val r = p.size.dp.toPx() / 2f
    val red = p.ball == BallColour.RedWhite
    val a = p.strength.coerceIn(0f, 1f)
    val lw = max(1.dp.toPx(), r * 0.09f)
    for (s in spots) {
        withTransform({
            translate(s.x, s.y)
            rotate(s.tilt, pivot = Offset.Zero)
        }) {
            when (p.style) {
                BallStyle.Badge -> badge(r, ball, page, a, red)
                BallStyle.Outline -> outline(r, ball, a, red, lw)
                BallStyle.Solid -> solid(r, ball, page, a, red, lw)
                BallStyle.Embossed -> embossed(r, ball, a, lw)
            }
        }
    }
}

private fun DrawScope.half(colour: Color, r: Float, top: Boolean, alpha: Float) =
    drawArc(colour, if (top) 180f else 0f, 180f, useCenter = true, topLeft = Offset(-r, -r), size = Size(r * 2, r * 2), alpha = alpha)

/** A flat disc with the band and the ring round the centre dot cut out (painted in the page colour). */
private fun DrawScope.badge(r: Float, ball: Color, page: Color, a: Float, red: Boolean) {
    if (red) {
        half(ClassicRed, r, top = true, alpha = a)
        half(Color.White, r, top = false, alpha = a)
    } else {
        drawCircle(ball, r, Offset.Zero, alpha = a)
    }
    drawRect(page, topLeft = Offset(-r - 1f, -r * 0.07f), size = Size(r * 2 + 2f, r * 0.14f))
    drawCircle(page, r * 0.42f, Offset.Zero)
    drawCircle(ball, r * 0.26f, Offset.Zero, alpha = a)
}

/** Just the lines of a ball. */
private fun DrawScope.outline(r: Float, ball: Color, a: Float, red: Boolean, lw: Float) {
    val stroke = Stroke(width = lw)
    drawArc(if (red) ClassicRed else ball, 180f, 180f, useCenter = false, topLeft = Offset(-r, -r), size = Size(r * 2, r * 2), alpha = a, style = stroke)
    drawArc(ball, 0f, 180f, useCenter = false, topLeft = Offset(-r, -r), size = Size(r * 2, r * 2), alpha = a, style = stroke)
    drawLine(ball, Offset(-r, 0f), Offset(-r * 0.33f, 0f), lw, alpha = a)
    drawLine(ball, Offset(r * 0.33f, 0f), Offset(r, 0f), lw, alpha = a)
    drawCircle(ball, r * 0.33f, Offset.Zero, alpha = a, style = stroke)
    drawCircle(ball, r * 0.15f, Offset.Zero, alpha = a, style = stroke)
}

/** A filled ball: the top half full, the bottom half lighter, a thin band and the centre ring cut out. */
private fun DrawScope.solid(r: Float, ball: Color, page: Color, a: Float, red: Boolean, lw: Float) {
    half(if (red) ClassicRed else ball, r, top = true, alpha = a)
    half(ball, r, top = false, alpha = a * (if (red) 1f else 0.55f))
    drawRect(page, topLeft = Offset(-r - 1f, -lw / 2f), size = Size(r * 2 + 2f, lw))
    drawCircle(page, r * 0.36f, Offset.Zero)
    drawCircle(ball, r * 0.2f, Offset.Zero, alpha = a)
}

/** Lines pressed into the page: a light edge with a dark shadow just below it. */
private fun DrawScope.embossed(r: Float, ball: Color, a: Float, lw: Float) {
    val stroke = Stroke(width = lw * 1.2f)
    for ((d, colour, alpha) in listOf(Triple(-1.dp.toPx(), ball, min(1f, a * 1.1f)), Triple(1.2.dp.toPx(), Color.Black, 0.8f))) {
        val c = Offset(d, d)
        drawCircle(colour, r, c, alpha = alpha, style = stroke)
        drawLine(colour, Offset(-r + d, d), Offset(-r * 0.33f + d, d), lw * 1.2f, alpha = alpha)
        drawLine(colour, Offset(r * 0.33f + d, d), Offset(r + d, d), lw * 1.2f, alpha = alpha)
        drawCircle(colour, r * 0.33f, c, alpha = alpha, style = stroke)
    }
}
