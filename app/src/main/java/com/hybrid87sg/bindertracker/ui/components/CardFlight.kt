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
 * grows into the card screen ([FlyMillis]). Ril picked the "Relaxed" speed in
 * the prototype: about a second.
 *
 * Closing: no flight back. The card screen just fades away and the card is
 * already resting in its pocket (Ril asked to drop the slide back in).
 *
 * Both screens mark the card picture with [cardFlight] and the same key, and
 * Compose's shared element transition does the flying.
 */
object CardFlight {
    const val LiftMillis = 340
    const val FlyMillis = 720
    val FlyEasing = CubicBezierEasing(0.2f, 0.75f, 0.2f, 1f)

    /** The key for the card in one pocket. */
    fun pocketKey(pageId: String, position: Int) = "pocket-$pageId-$position"

    /** The key for a card on the Missing cards page. */
    fun missingKey(cardId: String) = "missing-$cardId"

    /**
     * True while the card screen is closing. Cards don't fly then (the screen
     * just fades), and the binder's cards don't pop in again: they are simply
     * there. MainActivity clears it once the fade is over.
     */
    var closing by mutableStateOf(false)

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
    if (key == null || CardFlight.closing) return this
    return with(shared) {
        this@cardFlight.sharedElement(
            rememberSharedContentState(key),
            animatedVisibilityScope = screen,
            boundsTransform = BoundsTransform { _, _ -> tween(CardFlight.FlyMillis, easing = CardFlight.FlyEasing) },
        )
    }
}
