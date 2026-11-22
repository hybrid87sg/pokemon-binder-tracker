package com.hybrid87sg.bindertracker.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app's background: a soft gradient (pale lavender to soft blue, or
 * deep violet to navy in dark mode) with a warm glow in the top-right
 * corner. Used instead of the flat background colour on every screen.
 */
fun Modifier.appBackground(): Modifier = drawBehind {
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
}
