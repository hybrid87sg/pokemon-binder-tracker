package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Shared animation settings, so every movement in the app feels the same.
 * "Easing" is the speed curve: this one starts quick and settles gently.
 */
object Motion {
    val Ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    const val Short = 220
    const val Medium = 380
    const val Long = 650
}

/**
 * Makes something shrink a little while a finger is on it, then spring back,
 * so every tap feels like pressing a real button.
 */
@Composable
fun Modifier.pressSquish(source: InteractionSource, pressedScale: Float = 0.95f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * A number that counts up from 0 to [target] the first time it's shown
 * (for example "142 cards"), then simply follows [target] afterwards.
 */
@Composable
fun countUp(target: Int, delayMillis: Int = 0): Int {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (value.value == 0f && target > 0) delay(delayMillis.toLong())
        value.animateTo(target.toFloat(), tween(Motion.Long, easing = Motion.Ease))
    }
    return value.value.toInt()
}

/**
 * Plays an "arrive" animation once: goes from 0 to 1 after [delayMillis].
 * Use the value to slide or fade something into place.
 * When [play] is false it starts at 1, so nothing moves.
 */
@Composable
fun arrive(play: Boolean, delayMillis: Int = 0, durationMillis: Int = Motion.Medium): Float {
    val value = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (value.value < 1f) {
            delay(delayMillis.toLong())
            value.animateTo(1f, tween(durationMillis, easing = Motion.Ease))
        }
    }
    return value.value
}
