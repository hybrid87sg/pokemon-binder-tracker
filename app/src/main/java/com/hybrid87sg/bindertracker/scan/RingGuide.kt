package com.hybrid87sg.bindertracker.scan

import kotlin.math.sqrt

/**
 * Decides what the round guide on the ring code scanner shows, from where a
 * ring code sits compared with the guide. Plain Kotlin, so it can be tested.
 *
 * - Looking (white dashed): no code in view.
 * - Not lined up (red): a code is in view but not inside the ring, with a tip.
 * - Lined up (green): the whole code is inside the ring and big enough to
 *   read, so the first clear read scans it.
 */
object RingGuide {
    /** The guide's size on screen, in dp. */
    const val RING_DP = 330

    // A little overlap on the ring's line still counts as inside.
    private const val SLACK = 1.04f
    // Smaller than this share of the ring is too far away to read well.
    private const val MIN_SHARE = 0.4f

    enum class State { Looking, NotLinedUp, LinedUp }

    enum class Tip(val words: String) {
        IntoRing("Move the code into the ring"),
        Closer("Move closer"),
        Back("Move back a little"),
    }

    class Verdict(val state: State, val tip: Tip? = null)

    /**
     * [dx], [dy]: how far the code's centre is from the ring's centre.
     * [radius]: the code's radius. [ring]: the guide's radius. All in the same units.
     */
    fun judge(dx: Float, dy: Float, radius: Float, ring: Float): Verdict {
        val off = sqrt(dx * dx + dy * dy)
        return when {
            radius > ring * SLACK -> Verdict(State.NotLinedUp, Tip.Back)
            off + radius <= ring * SLACK && radius >= ring * MIN_SHARE -> Verdict(State.LinedUp)
            radius < ring * MIN_SHARE && off + radius <= ring -> Verdict(State.NotLinedUp, Tip.Closer)
            else -> Verdict(State.NotLinedUp, Tip.IntoRing)
        }
    }
}
