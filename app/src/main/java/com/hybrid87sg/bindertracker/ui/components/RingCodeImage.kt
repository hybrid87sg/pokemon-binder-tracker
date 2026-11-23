package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.sync.RingCode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws [code] as a binder ring code: white arcs on three rings around the
 * app logo, on a disc in the binder's colour. The layout comes from
 * [RingCode], which the camera reader uses too.
 *
 * It looks the same in light and dark mode. The white edge round the disc
 * lets the camera find it on any background.
 */
@Composable
fun RingCodeImage(code: String, binderColor: Color, modifier: Modifier = Modifier) {
    val bits = remember(code) { RingCode.encode(code) }
    // Light binder colours are darkened, so the white arcs stand out clearly.
    val disc = remember(binderColor) {
        if (binderColor.luminance() > 0.1f) lerp(binderColor, Color.Black, 0.35f) else binderColor
    }
    val track = lerp(disc, Color.White, 0.16f)
    val logo = painterResource(R.mipmap.ic_launcher_foreground)

    Canvas(modifier.semantics { contentDescription = "Binder code to join this binder" }) {
        val unit = size.minDimension / 2 / RingCode.HALO_RADIUS
        val centre = Offset(size.width / 2, size.height / 2)
        val step = 2 * PI / RingCode.POSITIONS
        val width = RingCode.RING_WIDTH * unit

        drawCircle(Color.White, radius = RingCode.HALO_RADIUS * unit, center = centre)
        drawCircle(disc, radius = unit, center = centre)

        for (ring in 0 until RingCode.RINGS) {
            val r = RingCode.RING_RADII[ring] * unit
            drawCircle(track, radius = r, center = centre, style = Stroke(width))
            // A small gap between arcs (the round ends stick out by half the ring's width).
            val pad = (width / 2 + 2f / 196f * unit) / r
            var slot = 0
            while (slot < RingCode.SLOTS) {
                if (!bits[ring * RingCode.SLOTS + slot]) { slot++; continue }
                var end = slot
                while (end + 1 < RingCode.SLOTS && bits[ring * RingCode.SLOTS + end + 1]) end++
                val from = (slot + 1) * step + pad
                val to = (end + 2) * step - pad
                if (to - from < 0.02) {
                    // A lone slot: a round dot.
                    val a = (slot + 1.5) * step
                    drawCircle(
                        Color.White,
                        radius = width / 2,
                        center = Offset(centre.x + r * sin(a).toFloat(), centre.y - r * cos(a).toFloat()),
                    )
                } else {
                    // Compose measures angles from 3 o'clock; ours start at 12.
                    drawArc(
                        Color.White,
                        startAngle = Math.toDegrees(from).toFloat() - 90f,
                        sweepAngle = Math.toDegrees(to - from).toFloat(),
                        useCenter = false,
                        topLeft = Offset(centre.x - r, centre.y - r),
                        size = Size(2 * r, 2 * r),
                        style = Stroke(width, cap = StrokeCap.Round),
                    )
                }
                slot = end + 1
            }
        }

        // The "this way up" triangle above the empty top position.
        val notch = Path().apply {
            moveTo(centre.x - 0.06f * unit, centre.y - 0.97f * unit)
            lineTo(centre.x + 0.06f * unit, centre.y - 0.97f * unit)
            lineTo(centre.x, centre.y - 0.90f * unit)
            close()
        }
        drawPath(notch, Color.White)

        // The white middle with the logo. The logo picture has empty space
        // round it, so it's drawn bigger to make the logo itself fill 0.38.
        drawCircle(Color.White, radius = RingCode.CENTRE_RADIUS * unit, center = centre)
        val logoSide = 2 * 0.38f * unit / 0.607f
        translate(centre.x - logoSide / 2, centre.y - logoSide / 2) {
            with(logo) { draw(Size(logoSide, logoSide)) }
        }
    }
}
