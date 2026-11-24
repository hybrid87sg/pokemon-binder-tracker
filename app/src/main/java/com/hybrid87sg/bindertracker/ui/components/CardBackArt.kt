package com.hybrid87sg.bindertracker.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.hybrid87sg.bindertracker.data.CardBackStyle
import com.hybrid87sg.bindertracker.data.CoverPlacement
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws a card back with plain Android drawing, so the same code paints the
 * empty pockets on screen and the pockets inside the 3D binder.
 *
 * Every design is our own (no official Pokémon artwork). Like CardBack.kt,
 * everything is measured on a 63 x 88 grid, the size of a real card in mm.
 * Orbit, Prism and Lattice are tinted with the binder's colour.
 */
object CardBackArt {
    /** The colours of the original blue back, light and dark mode (see Theme.kt). */
    class Blue(val top: Int, val bottom: Int, val frame: Int, val swirl: Int, val spark: Int)

    val lightBlue = Blue(0xFF23379A.toInt(), 0xFF1A2A78.toInt(), 0xFFC9D2F5.toInt(), 0xFF8EA4FF.toInt(), Color.WHITE)
    val darkBlue = Blue(0xFF1C2B6E.toInt(), 0xFF142057.toInt(), 0xFF6F84D8.toInt(), 0xFF5F79E8.toInt(), 0xFFDDE4FF.toInt())

    fun draw(
        c: Canvas,
        w: Float,
        h: Float,
        style: CardBackStyle,
        binderColor: Int,
        picture: Bitmap? = null,
        placement: CoverPlacement = CoverPlacement(),
        blue: Blue = lightBlue,
    ) {
        when (style) {
            CardBackStyle.Original -> orbit(c, w, h, blue.top, blue.bottom, blue.frame, blue.swirl, blue.spark, sparkles = false)
            CardBackStyle.Orbit -> tones(binderColor).let { t -> orbit(c, w, h, t.base, t.deep, t.pale, t.light, Color.WHITE, sparkles = true) }
            CardBackStyle.Prism -> prism(c, w, h, tones(binderColor))
            CardBackStyle.Lattice -> lattice(c, w, h, tones(binderColor))
            CardBackStyle.Own ->
                if (picture != null) own(c, w, h, picture, placement)
                else orbit(c, w, h, blue.top, blue.bottom, blue.frame, blue.swirl, blue.spark, sparkles = false)
        }
    }

    /** Shades of one binder colour. */
    class Tones(val base: Int, val deep: Int, val dark: Int, val light: Int, val pale: Int)

    fun tones(color: Int): Tones {
        val base = color or 0xFF000000.toInt()
        return Tones(base, mix(base, Color.BLACK, 0.42f), mix(base, Color.BLACK, 0.65f), mix(base, Color.WHITE, 0.55f), mix(base, Color.WHITE, 0.82f))
    }

    fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private fun alpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    /** The thin frame just inside the edge, like the printed border on a real card. */
    private fun frame(c: Canvas, w: Float, h: Float, color: Int, inset: Float, p: Paint) {
        val u = w / 63f
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * u; p.color = alpha(color, 0.85f); p.shader = null
        c.drawRoundRect(RectF(inset * u, inset * u, w - inset * u, h - inset * u), 3f * u, 3f * u, p)
        p.style = Paint.Style.FILL
    }

    private fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int, p: Paint) {
        val path = Path()
        for (i in 0 until 8) {
            val a = i * PI / 4
            val rad = if (i % 2 == 1) r * 0.38f else r
            val px = x + sin(a).toFloat() * rad; val py = y - cos(a).toFloat() * rad
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        p.style = Paint.Style.FILL; p.shader = null; p.color = color
        c.drawPath(path, p)
    }

    /** Three broken rings spinning round a star (the original back, and "Orbit"). */
    private fun orbit(c: Canvas, w: Float, h: Float, top: Int, bottom: Int, frameColor: Int, swirl: Int, spark: Int, sparkles: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val u = w / 63f
        p.shader = LinearGradient(0f, 0f, w, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        if (sparkles) {
            p.shader = RadialGradient(w / 2f, h / 2f, h * 0.6f, intArrayOf(alpha(swirl, 0.25f), 0), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
        }
        p.shader = null
        frame(c, w, h, frameColor, 3.5f, p)
        val cx = w / 2f; val cy = h / 2f
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        for ((r, width, a, start) in listOf(Ring(21f, 2.2f, 0.55f, -20f), Ring(15f, 2.6f, 0.75f, 70f), Ring(9f, 2.8f, 1f, 160f))) {
            p.strokeWidth = width * u; p.color = alpha(swirl, a)
            val o = RectF(cx - r * u, cy - r * u, cx + r * u, cy + r * u)
            c.drawArc(o, start, 117f, false, p)
            c.drawArc(o, start + 180f, 117f, false, p)
        }
        p.style = Paint.Style.FILL
        star(c, cx, cy, 4.5f * u, spark, p)
        if (sparkles) {
            for ((x, y, s) in listOf(Triple(12f, 14f, 1.3f), Triple(50f, 20f, 1f), Triple(14f, 74f, 1f), Triple(52f, 70f, 1.4f))) {
                star(c, x * u, y * h / 88f, s * u, alpha(frameColor, 0.8f), p)
            }
        }
    }

    private data class Ring(val radius: Float, val width: Float, val alpha: Float, val start: Float)

    /** "Prism": light rays fanning out from a gem in the middle. */
    private fun prism(c: Canvas, w: Float, h: Float, t: Tones) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val u = w / 63f
        val cx = w / 2f; val cy = h / 2f
        p.color = t.deep; c.drawRect(0f, 0f, w, h, p)
        val rays = 24
        for (i in 0 until rays) {
            p.color = if (i % 2 == 1) alpha(t.base, 0.9f) else alpha(t.light, 0.32f)
            val start = i * 360f / rays
            c.drawArc(RectF(cx - h, cy - h, cx + h, cy + h), start, 360f / rays, true, p)
        }
        p.shader = RadialGradient(cx, cy, h * 0.62f, intArrayOf(alpha(Color.WHITE, 0.35f), alpha(Color.WHITE, 0f), alpha(t.dark, 0.75f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        frame(c, w, h, t.pale, 3.5f, p)
        frame(c, w, h, t.pale, 5.5f, p)
        // The gem: a diamond with a light and a dark side.
        val s = 10f * u
        fun gem(vararg pts: Float): Path = Path().apply { moveTo(cx + pts[0], cy + pts[1]); var i = 2; while (i < pts.size) { lineTo(cx + pts[i], cy + pts[i + 1]); i += 2 }; close() }
        p.color = t.pale; c.drawPath(gem(0f, -s * 1.25f, s, 0f, 0f, s * 1.25f, -s, 0f), p)
        p.color = t.light; c.drawPath(gem(0f, -s * 1.25f, s, 0f, 0f, 0f), p)
        p.color = t.base; c.drawPath(gem(0f, s * 1.25f, -s, 0f, 0f, 0f), p)
        p.style = Paint.Style.STROKE; p.strokeWidth = u; p.color = Color.WHITE
        c.drawPath(gem(0f, -s * 1.25f, s, 0f, 0f, s * 1.25f, -s, 0f), p)
        p.style = Paint.Style.FILL
        star(c, cx - 3f * u, cy - 6f * u, 1.6f * u, Color.WHITE, p)
    }

    /** "Lattice": woven diamonds with a round badge showing binder rings. */
    private fun lattice(c: Canvas, w: Float, h: Float, t: Tones) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val u = w / 63f
        val cx = w / 2f; val cy = h / 2f
        p.shader = LinearGradient(0f, 0f, 0f, h, t.base, t.deep, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(5f * u, 5f * u, w - 5f * u, h - 5f * u), 2.5f * u, 2.5f * u, Path.Direction.CW) })
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.7f * u; p.color = alpha(t.light, 0.35f)
        val step = 7f * u
        var x = -h
        while (x < w + h) {
            c.drawLine(x, 0f, x + h, h, p)
            c.drawLine(x, h, x + h, 0f, p)
            x += step
        }
        c.restore()
        p.style = Paint.Style.FILL
        frame(c, w, h, t.pale, 3.5f, p)
        // The badge.
        p.color = t.dark; c.drawCircle(cx, cy, 16f * u, p)
        p.style = Paint.Style.STROKE; p.color = t.pale
        p.strokeWidth = 1.6f * u; c.drawCircle(cx, cy, 16f * u, p)
        p.strokeWidth = 0.8f * u; c.drawCircle(cx, cy, 13f * u, p)
        p.style = Paint.Style.FILL
        for (dy in listOf(-7f, 0f, 7f)) {
            p.color = t.pale; c.drawCircle(cx - 5f * u, cy + dy * u, 2.1f * u, p)
            p.color = t.dark; c.drawCircle(cx - 5f * u, cy + dy * u, 1f * u, p)
        }
        p.color = t.pale; c.drawRoundRect(RectF(cx - u, cy - 8.5f * u, cx + 7f * u, cy + 8.5f * u), 1.5f * u, 1.5f * u, p)
        p.color = t.base
        for (dy in listOf(-5f, -1.5f, 2f, 5.5f)) c.drawRect(cx + 0.5f * u, cy + dy * u, cx + 5.5f * u, cy + (dy + 0.9f) * u, p)
    }

    /** Your own picture, filling the card where Adjust picture put it. */
    private fun own(c: Canvas, w: Float, h: Float, picture: Bitmap, placement: CoverPlacement) {
        val fit = max(w / picture.width, h / picture.height) * placement.zoom
        val dw = picture.width * fit; val dh = picture.height * fit
        val left = -(dw - w) * placement.x; val top = -(dh - h) * placement.y
        c.drawColor(0xFF222222.toInt())
        c.drawBitmap(picture, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }
}
