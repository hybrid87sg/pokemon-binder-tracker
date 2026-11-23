package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * A card flying between a pocket (or a Missing cards tile) and the card screen.
 *
 * Opening: the card slides up out of its sleeve ([LiftMillis]), then flies and
 * grows into the card screen ([FlyMillis]). Closing does the reverse, then the
 * card slides back down into the sleeve ([SettleMillis]). Ril picked the
 * "Relaxed" speed in the prototype: about a second each way.
 *
 * Both screens mark the card picture with [cardFlight] and the same key, and
 * Compose's shared element transition does the flying.
 */
object CardFlight {
    const val LiftMillis = 340
    const val FlyMillis = 720
    const val SettleMillis = 480
    val FlyEasing = CubicBezierEasing(0.2f, 0.75f, 0.2f, 1f)

    /** The key for the card in one pocket. */
    fun pocketKey(pageId: String, position: Int) = "pocket-$pageId-$position"

    /** The key for a card on the Missing cards page. */
    fun missingKey(cardId: String) = "missing-$cardId"

    /**
     * The card that is flying back from the card screen, by its key. Its pocket
     * starts lifted out of the sleeve, so the card lands there and then slides in.
     */
    var landing by mutableStateOf<String?>(null)

    /** The card sliding up out of its sleeve on the way to the card screen, by its key. */
    var lifting by mutableStateOf<String?>(null)
}

/** Set round the whole app (see MainActivity), so any screen can fly a card. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The screen's own enter/exit animation, set for each screen in MainActivity. */
val LocalScreenAnimationScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks a card picture that flies between screens. Screens that aren't inside
 * the navigation (or a null [key]) get no flight.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.cardFlight(key: String?): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val screen = LocalScreenAnimationScope.current ?: return this
    if (key == null) return this
    return with(shared) {
        this@cardFlight.sharedElement(
            rememberSharedContentState(key),
            animatedVisibilityScope = screen,
            boundsTransform = BoundsTransform { _, _ -> tween(CardFlight.FlyMillis, easing = CardFlight.FlyEasing) },
        )
    }
}
