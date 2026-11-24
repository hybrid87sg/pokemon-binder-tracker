package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.hybrid87sg.bindertracker.data.CardBackChoice
import com.hybrid87sg.bindertracker.data.CardBackStyle
import com.hybrid87sg.bindertracker.ui.theme.AppColors

/** This phone's card back (Me > Card back) and your own picture, set round the whole app. */
class CardBackSetting(val choice: CardBackChoice = CardBackChoice(), val picture: android.graphics.Bitmap? = null) {
    /** The design actually drawn: the original blue until your own picture has loaded. */
    val style: CardBackStyle get() = if (choice.style == CardBackStyle.Own && picture == null) CardBackStyle.Original else choice.style
}

val LocalCardBack = compositionLocalOf { CardBackSetting() }

/** The colour of the binder the cards are in: Orbit, Prism and Lattice take it on. */
val LocalBinderColor = compositionLocalOf<Color?> { null }

/**
 * The picture shown in an empty pocket: our own "back of a card" design.
 *
 * It isn't the official Pokémon card back (that artwork belongs to The
 * Pokémon Company). It's a blue card with a thin frame, a swirl of broken
 * rings and a small star, drawn with code so it's sharp at any size.
 *
 * Everything is measured on a 63 x 88 grid (a real card is 63 mm x 88 mm)
 * and then stretched to fit, so the drawing keeps its shape in any pocket.
 * Colours come from AppColors, so it changes with light and dark mode.
 */
@androidx.compose.runtime.Composable
fun CardBack(modifier: Modifier = Modifier) {
    val setting = LocalCardBack.current
    if (setting.style != CardBackStyle.Original) {
        // One of the other designs, or your own picture: drawn by CardBackArt
        // (the same drawing as the pockets inside the 3D binder).
        val binder = (LocalBinderColor.current ?: AppColors.Accent).toArgb()
        Canvas(modifier.semantics { contentDescription = "Empty pocket" }) {
            drawIntoCanvas {
                CardBackArt.draw(it.nativeCanvas, size.width, size.height, setting.style, binder, setting.picture, setting.choice.placement)
            }
        }
        return
    }
    val top = AppColors.CardBack
    val bottom = AppColors.CardBackDeep
    val frame = AppColors.CardBackFrame
    val swirl = AppColors.CardBackSwirl
    val spark = AppColors.CardBackSpark
    Canvas(modifier.semantics { contentDescription = "Empty pocket" }) {
        val u = size.width / 63f                      // one grid step, in pixels
        fun at(x: Float, y: Float) = Offset(x * u, y * (size.height / 88f))

        // The blue card, a little darker towards the bottom-right.
        drawRect(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(size.width, size.height)))

        // Thin frame inside the edge, like the printed border on a real card.
        drawRoundRect(
            color = frame.copy(alpha = 0.85f),
            topLeft = at(3.5f, 3.5f),
            size = Size(56f * u, size.height - 7f * (size.height / 88f)),
            cornerRadius = CornerRadius(3f * u),
            style = Stroke(width = 1.2f * u),
        )

        // The swirl: three rings, each drawn as two arcs with gaps, each ring
        // turned a different way so together they seem to spin.
        val centre = at(31.5f, 44f)
        // radius, line width, see-through-ness, where the first arc starts (degrees)
        val rings = listOf(
            Ring(21f, 2.2f, 0.55f, -20f),
            Ring(15f, 2.6f, 0.75f, 70f),
            Ring(9f, 2.8f, 1f, 160f),
        )
        for (ring in rings) {
            val r = ring.radius * u
            repeat(2) { half ->
                drawArc(
                    color = swirl.copy(alpha = ring.alpha),
                    startAngle = ring.start + half * 180f,
                    sweepAngle = 117f,
                    useCenter = false,
                    topLeft = Offset(centre.x - r, centre.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = ring.width * u, cap = StrokeCap.Round),
                )
            }
        }

        // A small four-pointed star in the middle.
        val star = Path().apply {
            val (cx, cy) = centre
            fun p(dx: Float, dy: Float) = Offset(cx + dx * u, cy + dy * u)
            p(0f, -4.5f).let { moveTo(it.x, it.y) }
            listOf(p(1.4f, -1.4f), p(4.5f, 0f), p(1.4f, 1.4f), p(0f, 4.5f), p(-1.4f, 1.4f), p(-4.5f, 0f), p(-1.4f, -1.4f))
                .forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(star, spark)
    }
}

private class Ring(val radius: Float, val width: Float, val alpha: Float, val start: Float)
