package com.hybrid87sg.bindertracker.ui.theme

import kotlin.math.abs
import kotlin.math.roundToInt

/** How each Poké Ball on the binder page is drawn. */
enum class BallStyle(val label: String) {
    /** A flat disc with a band across the middle and a ring round the centre dot (Ril's picture). */
    Badge("Badge"),
    Outline("Outline"),
    Solid("Solid"),
    Embossed("Embossed"),
}

/** How the balls are spread over the page. */
enum class BallLayout(val label: String) {
    /** Straight rows, every other row shifted half a step. */
    Rows("Rows"),
    Diagonal("Diagonal"),
    Scattered("Scattered"),
}

/** The ball colour: a colour from the ring, the app's own colour, or the classic red and white. */
sealed interface BallColour {
    data object App : BallColour
    data object RedWhite : BallColour
    data class Pick(val choice: ColourChoice) : BallColour
}

/** A quick pick under the colour ring: its name and colour. */
data class PatternGem(val name: String, val colour: BallColour)

/**
 * The pattern behind the cards on a binder page: Poké Balls on a dark page.
 * Chosen in the Page pattern editor (the paintbrush on the card page), one per
 * binder, saved on this phone only (see [PagePatternSetting]).
 *
 * Sizes are in dp. [strength] is how strongly the balls show, 0 to 1.
 */
data class PagePattern(
    val style: BallStyle = BallStyle.Badge,
    val ball: BallColour = BallColour.Pick(ColourChoice(0f, 0f)),
    val page: ColourChoice = ColourChoice(0f, 0f),
    val size: Int = 20,
    val gap: Int = 16,
    val tilt: Int = 0,
    val mixTilt: Boolean = false,
    val layout: BallLayout = BallLayout.Rows,
    val strength: Float = 0.32f,
    val drift: Boolean = false,
)

/**
 * The maths behind the page pattern. No Android code, so it can be tested on
 * a computer (see PagePatternTest).
 *
 * Colours come from the same three-ring picker as App colour: hue (middle
 * ring), Plain to Vivid (outer ring) and brightness (inner ring, Auto in the
 * middle). Balls stay light (white at Plain), the page stays dark (black at Plain).
 */
object PagePatternMath {
    const val SIZE_MIN = 8
    const val SIZE_MAX = 50
    const val GAP_MIN = 4
    const val GAP_MAX = 40
    const val STRENGTH_MIN = 0.03f
    const val STRENGTH_MAX = 0.70f

    /** The red of the classic red and white ball. */
    const val CLASSIC_RED = 0xFFE0453A.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    /** A ball colour: white at Plain, a clear strong colour at Vivid; brightness moves it lighter or darker. */
    fun ballColour(c: ColourChoice): Int {
        val s = c.strength.coerceIn(0f, 1f)
        val l = (90f - 26f * s + (c.brightness - AUTO_BRIGHTNESS) * 110f).coerceIn(12f, 97f)
        return AppColourMath.hsl(c.hue, s * 100f, l)
    }

    /** A page colour: black at Plain, a deep colour at Vivid; brightness moves it lighter or darker (never pale). */
    fun pageColour(c: ColourChoice): Int {
        val s = c.strength.coerceIn(0f, 1f)
        val l = (4f + 11f * s + (c.brightness - AUTO_BRIGHTNESS) * 70f).coerceIn(0f, 45f)
        return AppColourMath.hsl(c.hue, s * 80f, l)
    }

    val BALL_GEMS = listOf(
        PatternGem("App colour", BallColour.App),
        PatternGem("Classic red & white", BallColour.RedWhite),
        PatternGem("White", BallColour.Pick(ColourChoice(0f, 0f))),
        PatternGem("Gold", BallColour.Pick(ColourChoice(44f, 0.85f))),
        PatternGem("Ocean", BallColour.Pick(ColourChoice(214f, 0.80f))),
        PatternGem("Rose", BallColour.Pick(ColourChoice(336f, 0.75f))),
        PatternGem("Violet", BallColour.Pick(ColourChoice(262f, 0.75f))),
        PatternGem("Leaf", BallColour.Pick(ColourChoice(146f, 0.70f))),
    )

    val PAGE_GEMS = listOf(
        PatternGem("Black", BallColour.Pick(ColourChoice(0f, 0f))),
        PatternGem("Navy", BallColour.Pick(ColourChoice(222f, 0.60f))),
        PatternGem("Deep ocean", BallColour.Pick(ColourChoice(200f, 0.70f))),
        PatternGem("Forest", BallColour.Pick(ColourChoice(150f, 0.60f))),
        PatternGem("Plum", BallColour.Pick(ColourChoice(290f, 0.55f))),
        PatternGem("Ember", BallColour.Pick(ColourChoice(8f, 0.65f))),
        PatternGem("Bronze", BallColour.Pick(ColourChoice(42f, 0.55f))),
        PatternGem("Berry", BallColour.Pick(ColourChoice(330f, 0.60f))),
    )

    /** The ring snaps onto a gem's colour when you drag within 5° of it. */
    fun snapHues(gems: List<PatternGem>): List<Float> =
        gems.mapNotNull { (it.colour as? BallColour.Pick)?.choice?.takeIf { c -> c.strength > 0f }?.hue }

    /** Whether two ring picks are the same colour (hue only matters once it isn't Plain). */
    fun same(a: ColourChoice, b: ColourChoice): Boolean =
        abs(a.strength - b.strength) < 0.005f && abs(a.brightness - b.brightness) < 0.005f &&
            (a.strength == 0f || AppColourMath.hueDistance(a.hue, b.hue) < 1f)

    /** A friendly name: the gem's name if it's exactly on one, else the colour family ("Dark ocean"). */
    fun nameFor(c: BallColour, page: Boolean): String = when (c) {
        BallColour.App -> "App colour"
        BallColour.RedWhite -> "Classic red & white"
        is BallColour.Pick -> {
            val ch = c.choice
            val gem = (if (page) PAGE_GEMS else BALL_GEMS).firstOrNull { g -> (g.colour as? BallColour.Pick)?.choice?.let { same(it, ch) } == true }
            when {
                gem != null -> gem.name
                else -> {
                    val base = if (ch.strength < 0.12f) (if (page) "Black" else "White") else AppColourMath.nameFor(ch.hue)
                    when {
                        abs(ch.brightness - AUTO_BRIGHTNESS) < 0.005f -> base
                        ch.brightness < AUTO_BRIGHTNESS -> "Dark ${base.lowercase()}"
                        else -> "Light ${base.lowercase()}"
                    }
                }
            }
        }
    }

    fun sizeWord(size: Int): String = when {
        size < 16 -> "Small"
        size < 30 -> "Medium"
        else -> "Large"
    }

    fun gapWord(gap: Int): String = when {
        gap < 10 -> "Tight"
        gap < 24 -> "Normal"
        else -> "Roomy"
    }

    // ---------------------------------------------------------------- where the balls go

    /** One ball: its centre (px), and its tilt in degrees. */
    data class Spot(val x: Float, val y: Float, val tilt: Float)

    /**
     * Where every ball sits on a page [width] x [height] px, for balls [sizePx]
     * across with [gapPx] between them. [shift] slides the pattern sideways (slow drift).
     * Balls hang over the edges so the pattern looks like it carries on.
     */
    fun spots(p: PagePattern, width: Float, height: Float, sizePx: Float, gapPx: Float, shift: Float = 0f): List<Spot> {
        if (width <= 1f || height <= 1f || sizePx <= 0f) return emptyList()
        val step = sizePx + gapPx
        val random = Lcg(11)
        fun tiltOf(): Float = if (p.mixTilt) random.next() * 180f - 90f else p.tilt.toFloat()
        val out = ArrayList<Spot>()
        if (p.layout == BallLayout.Scattered) {
            val n = (width * height / (step * step) * 1.2f).roundToInt().coerceAtMost(4000)
            val w = width + step
            val h = height + step
            for (i in 0 until n) {
                val x = ((random.next() * w + shift) % w + w) % w - step / 2f
                val y = ((random.next() * h + shift * 0.6f) % h + h) % h - step / 2f
                out += Spot(x, y, tiltOf())
            }
        } else {
            val rows = p.layout == BallLayout.Rows
            val gx = step * (if (rows) 2f else 1.15f)
            val gy = step * (if (rows) 0.8f else 1f)
            val drift = ((shift % gx) + gx) % gx
            var row = -3
            var y = -gy * 2f
            while (y < height + gy * 2f) {
                val rowShift = if (rows) (if (row % 2 != 0) gx / 2f else 0f) else ((row * gx * 0.5f) % gx + gx) % gx
                var x = rowShift - gx * 2f
                while (x < width + gx * 2f) {
                    out += Spot(x + drift, y, tiltOf())
                    x += gx
                }
                row++
                y += gy
                if (out.size > 6000) break
            }
        }
        return out
    }

    /** A tiny random number maker that gives the same numbers every time, so the pattern stays put. */
    private class Lcg(private var seed: Long) {
        fun next(): Float {
            seed = (seed * 16807L) % 2147483647L
            return seed / 2147483647f
        }
    }

    // ---------------------------------------------------------------- saving

    private fun pick(c: ColourChoice) = "${c.hue}:${c.strength}:${c.brightness}"

    private fun readPick(text: String?): ColourChoice? {
        val bits = text?.split(":") ?: return null
        val hue = bits.getOrNull(0)?.toFloatOrNull() ?: return null
        val strength = bits.getOrNull(1)?.toFloatOrNull() ?: return null
        val brightness = bits.getOrNull(2)?.toFloatOrNull() ?: AUTO_BRIGHTNESS
        return ColourChoice(((hue % 360f) + 360f) % 360f, strength.coerceIn(0f, 1f), brightness.coerceIn(0f, 1f))
    }

    /** A short line of text for saving, like "Badge|0.0:0.0:0.5|0.0:0.0:0.5|20|16|0|0|Rows|0.32|0". */
    fun encode(p: PagePattern): String = listOf(
        p.style.name,
        when (val b = p.ball) { BallColour.App -> "app"; BallColour.RedWhite -> "redwhite"; is BallColour.Pick -> pick(b.choice) },
        pick(p.page),
        p.size, p.gap, p.tilt,
        if (p.mixTilt) 1 else 0,
        p.layout.name,
        p.strength,
        if (p.drift) 1 else 0,
    ).joinToString("|")

    /** Reads [encode]'s text back. Anything missing or broken falls back to the default look. */
    fun decode(text: String?): PagePattern {
        val d = PagePattern()
        if (text.isNullOrBlank()) return d
        val f = text.split("|")
        fun int(i: Int, def: Int, lo: Int, hi: Int) = f.getOrNull(i)?.toIntOrNull()?.coerceIn(lo, hi) ?: def
        return PagePattern(
            style = BallStyle.entries.firstOrNull { it.name == f.getOrNull(0) } ?: d.style,
            ball = when (val b = f.getOrNull(1)) {
                "app" -> BallColour.App
                "redwhite" -> BallColour.RedWhite
                else -> readPick(b)?.let { BallColour.Pick(it) } ?: d.ball
            },
            page = readPick(f.getOrNull(2)) ?: d.page,
            size = int(3, d.size, SIZE_MIN, SIZE_MAX),
            gap = int(4, d.gap, GAP_MIN, GAP_MAX),
            tilt = int(5, d.tilt, -90, 90),
            mixTilt = f.getOrNull(6) == "1",
            layout = BallLayout.entries.firstOrNull { it.name == f.getOrNull(7) } ?: d.layout,
            strength = f.getOrNull(8)?.toFloatOrNull()?.coerceIn(STRENGTH_MIN, STRENGTH_MAX) ?: d.strength,
            drift = f.getOrNull(9) == "1",
        )
    }
}
