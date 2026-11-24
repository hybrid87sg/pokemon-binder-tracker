package com.hybrid87sg.bindertracker.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import com.hybrid87sg.bindertracker.ui.components.rememberPhoneTilt

/**
 * The app's background: a soft gradient built from the person's app colours
 * (pale lavender to soft blue by default, or deep violet to navy in dark
 * mode), a glow in the top-right corner, and, if they turned it on, a
 * glittering holo foil layer. Used instead of a flat colour on every screen.
 */
fun Modifier.appBackground(): Modifier = composed {
    val glitter = AppColourSetting.glitter
    // A clock in seconds that only runs while there is glitter to twinkle.
    val time: State<Float> = if (glitter > 0f) {
        rememberInfiniteTransition(label = "glitter").animateFloat(
            initialValue = 0f,
            targetValue = 600f,
            animationSpec = infiniteRepeatable(tween(600_000, easing = LinearEasing), RepeatMode.Restart),
            label = "glitterClock",
        )
    } else remember { mutableFloatStateOf(0f) }
    // Tilting the phone moves the glitter. The motion sensor is only used
    // while the app is open on screen and the glitter and tilt are both on.
    val reaction = AppColourSetting.tiltReaction
    val tiltOn = glitter > 0f && reaction > 0f
    val tilt = rememberPhoneTilt(enabled = tiltOn)

    drawBehind {
        // 165 degrees: from the top (a little left) to the bottom (a little right).
        val angle = Math.toRadians(165.0)
        val dx = sin(angle).toFloat()
        val dy = (-cos(angle)).toFloat()
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val c = center
        drawRect(Brush.linearGradient(AppColors.BackgroundStops, start = Offset(c.x - dx * half, c.y - dy * half), end = Offset(c.x + dx * half, c.y + dy * half)))
        drawRect(
            Brush.radialGradient(
                0f to AppColors.BackgroundGlow,
                0.6f to Color.Transparent,
                center = Offset(size.width, 0f),
                radius = size.width * 1.2f,
            ),
        )
        if (glitter > 0f) drawGlitter(glitter, time.value, AppColors.isDark, if (tiltOn) tilt.value else Offset.Zero, if (tiltOn) reaction else 0f)
    }
        // Everything on top of the background (lists, cards, text) goes in its own
        // layer. The glitter redraws every frame; without this, the whole screen's
        // contents were drawn again with it every frame, which made scrolling stutter.
        .graphicsLayer()
}

/**
 * One sparkle: where it sits (as a fraction of the screen), its size, colour
 * and how it twinkles. [faceX]/[faceY] (-1 to 1) is the tilt that makes it
 * catch the light and flash; [depth] is how far it moves when you tilt.
 */
private class Spark(
    val x: Float, val y: Float, val radius: Float, val colour: Int, val phase: Float, val speed: Float,
    val faceX: Float, val faceY: Float, val depth: Float,
)

/** Always the same sparkles in the same places, so they don't jump around between screens. */
private val SPARKS: List<Spark> = Random(87).let { r ->
    List(360) {
        val big = r.nextFloat() < 0.14f
        Spark(
            x = r.nextFloat(), y = r.nextFloat(),
            radius = if (big) 1.6f + r.nextFloat() * 1.8f else 0.5f + r.nextFloat() * 0.9f,
            colour = r.nextInt(6), phase = r.nextFloat() * 2f * PI.toFloat(), speed = 0.6f + r.nextFloat() * 2.2f,
            faceX = r.nextFloat() * 2f - 1f, faceY = r.nextFloat() * 2f - 1f, depth = if (r.nextBoolean()) 0.4f else 1f,
        )
    }
}

// Foil colours for the sparkles. Pale ones glow on dark backgrounds; on light
// backgrounds pale sparkles vanish, so light mode uses deeper ones.
private val SPARK_DARK = listOf(Color(0xFFFF9AD5), Color(0xFFFFD36E), Color(0xFF8CF0C8), Color(0xFF7FB7FF), Color(0xFFC59BFF), Color.White)
private val SPARK_LIGHT = listOf(Color(0xFFE248A8), Color(0xFFDE960A), Color(0xFF18AA78), Color(0xFF3273EB), Color(0xFF8C48E1), Color.White)
private val SHEEN = listOf(Color(0xFFFF9AD5), Color(0xFFFFD36E), Color(0xFF8CF0C8), Color(0xFF7FB7FF), Color(0xFFC59BFF), Color(0xFFFF9AD5))

/**
 * The glitter holo layer: a rainbow foil shine that drifts slowly across the
 * background, with small twinkling sparkles and four-point stars on top.
 * [amount] is 0 to 1; [seconds] is the clock that makes it move.
 */
private fun DrawScope.drawGlitter(amount: Float, seconds: Float, dark: Boolean, tilt: Offset, reaction: Float) {
    // Tilting slides the shine the opposite way, like light moving across a holo card.
    val lean = Offset(-tilt.x, -tilt.y) * (70.dp.toPx() * reaction)
    // The foil shine: rainbow bands at an angle, gently sliding back and forth.
    val band = size.width * 0.9f
    val drift = sin(seconds / 14f * PI.toFloat()) * size.width * 0.5f
    val a = Math.toRadians(115.0)
    val start = Offset(drift, 0f) + lean
    val end = Offset(drift + band * cos(a).toFloat(), band * sin(a).toFloat())
    drawRect(
        Brush.linearGradient(SHEEN, start = start, end = end, tileMode = TileMode.Mirror),
        alpha = amount * if (dark) 0.5f else 0.32f,
        blendMode = if (dark) BlendMode.Softlight else BlendMode.Multiply,
    )

    // The sparkles: more of them, and brighter, as [amount] goes up.
    val count = (30 + 330 * amount).toInt().coerceAtMost(SPARKS.size)
    val strength = 0.35f + 0.65f * amount
    val colours = if (dark) SPARK_DARK else SPARK_LIGHT
    val blend = if (dark) BlendMode.Plus else BlendMode.SrcOver
    for (i in 0 until count) {
        val s = SPARKS[i]
        val twinkle = (0.5f + 0.5f * sin(seconds * s.speed + s.phase)).pow(3)
        // Catching the light: bright when the tilt points the way this sparkle faces.
        val d = Offset(tilt.x - s.faceX, tilt.y - s.faceY).getDistance()
        val flash = (1f - d / 0.35f).coerceAtLeast(0f).pow(2) * reaction
        val alpha = ((twinkle * (1f - 0.5f * reaction) + flash * 1.6f) * strength).coerceAtMost(1f)
        if (alpha < 0.03f) continue
        // Near sparkles move a bit more than far ones, for a little depth.
        val p = Offset(s.x * size.width, s.y * size.height) + tilt * (14.dp.toPx() * reaction * s.depth)
        val r = s.radius * density
        if (s.radius > 1.5f || flash > 0.5f) {
            // A little four-point star with a white centre.
            val arm = r * (2.2f + 2f * twinkle + 4f * flash)
            val col = colours[s.colour].copy(alpha = alpha)
            drawLine(col, Offset(p.x - arm, p.y), Offset(p.x + arm, p.y), strokeWidth = density * 0.9f, blendMode = blend)
            drawLine(col, Offset(p.x, p.y - arm), Offset(p.x, p.y + arm), strokeWidth = density * 0.9f, blendMode = blend)
            drawCircle(Color.White.copy(alpha = alpha), radius = r * 0.7f + flash * density, center = p, blendMode = blend)
        } else {
            drawCircle(colours[s.colour].copy(alpha = alpha), radius = r, center = p, blendMode = blend)
        }
    }
}
