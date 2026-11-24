package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * How far the big card in the card view is tilted by your finger, -1 to 1
 * on each axis (the same scale as [rememberPhoneTilt], so [holoTilt] can
 * draw it). It goes back to flat whenever [key] changes, like when you
 * swipe to the next card.
 */
@Composable
fun rememberTouchTilt(key: Any?): Animatable<Offset, AnimationVector2D> {
    val tilt = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(key) { tilt.snapTo(Offset.Zero) }
    return tilt
}

/**
 * Touch the card and move your finger: it leans towards where you press,
 * like pushing that corner down. Let go and it springs back flat with a
 * small wobble. A touch that hardly moves is left alone, so a tap still
 * opens the card full screen, and once your finger moves the touch is
 * kept for tilting (so it doesn't also swipe to the next card or scroll).
 */
fun Modifier.touchTilt(
    tilt: Animatable<Offset, AnimationVector2D>,
    scope: CoroutineScope,
    enabled: Boolean,
): Modifier = if (!enabled) this else pointerInput(tilt) {
    // Smaller than the swipe's own threshold, so tilting wins on the card.
    val slop = 4.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var tilting = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if (!tilting && (change.position - down.position).getDistance() > slop) tilting = true
            if (tilting) {
                change.consume()
                val target = fingerTilt(change.position, size)
                scope.launch { tilt.snapTo(target) }
            }
        }
        if (tilting) {
            scope.launch { tilt.animateTo(Offset.Zero, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow)) }
        }
    }
}

/** Where the finger is on the card, -1 to 1 from the middle, kept inside a circle so no direction goes past the most. */
private fun fingerTilt(p: Offset, size: IntSize): Offset {
    if (size.width == 0 || size.height == 0) return Offset.Zero
    val x = (p.x / size.width - 0.5f) * 2f
    val y = (p.y / size.height - 0.5f) * 2f
    val m = hypot(x, y)
    return if (m > 1f) Offset(x / m, y / m) else Offset(x, y)
}
