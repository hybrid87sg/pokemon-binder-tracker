package com.hybrid87sg.bindertracker.ui.three

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.hybrid87sg.bindertracker.data.CoverPlacement
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/** Everything the 3D binder shows, so we know when to paint it again. */
data class BinderLook3D(
    val id: String,
    val name: String,
    val tag: String,
    val colorHex: String,
    val fill: Float,
    val dir: Int,
    val picturePath: String?,
    val pictureVersion: Long,
    val placement: CoverPlacement,
    /** The binder's first page, shown inside it. Null: the binder has no pages yet. */
    val sheet: SheetLook? = null,
    /** This phone's card back (Me > Card back), for the empty pockets on that page. */
    val back: com.hybrid87sg.bindertracker.data.CardBackChoice = com.hybrid87sg.bindertracker.data.CardBackChoice(),
)

/** The first page of a binder, as the 3D binder shows it when it opens. */
data class SheetLook(
    val pageId: String,
    val columns: Int,
    val rows: Int,
    val pockets: List<SheetPocket>,
    /** Where the page screen shows these cards (see PageLanding), or null to lay them out evenly. */
    val landing: List<Float>? = null,
    /** The page's colour, the same as the page screen's (light or dark mode). */
    val surface: Int = 0xFFF8F8FB.toInt(),
    /** Content of the right page if the binder is shown unfolded. */
    val secondPageLook: Int? = null,
)

/**
 * One pocket of that page. [url] and [backupUrl] are the small card pictures
 * (TCGdex, then pokemontcg.io), tried after the copy saved on the phone.
 */
data class SheetPocket(
    val position: Int,
    val kind: Kind,
    val cardId: String? = null,
    val url: String? = null,
    val backupUrl: String? = null,
) {
    enum class Kind {
        /** A card you have. */
        Card,
        /** A card you're missing from the binder's expansions: shown grey. */
        Missing,
        /** Marked empty: our blue card back. */
        Empty,
        /** Something there we don't know yet: a dashed outline. */
        Unknown,
    }
}

/**
 * Paints the pictures that go on a 3D binder: the cover, the spine label,
 * the inside of the cover and the first pocket page. Runs off the main thread.
 */
object BinderPaint {
    /** [cards] are the card pictures for the first page, by pocket position. */
    fun pictures(look: BinderLook3D, picture: Bitmap?, cards: Map<Int, Bitmap> = emptyMap(), backPicture: Bitmap? = null): BinderModel.Pictures {
        val color = parse(look.colorHex)
        return BinderModel.Pictures(
            cover = jpeg(cover(look, color, picture)),
            spine = jpeg(spine(look, color, picture)),
            lining = jpeg(lining(color)),
            sheet = look.sheet?.let { jpeg(firstPage(it, cards, Back(look.back, color, backPicture))) } ?: sheet,
            grain = grain,
        )
    }

    fun parse(hex: String): Int = runCatching { Color.parseColor(hex) }.getOrDefault(0xFF4B36D1.toInt())

    fun isPale(color: Int): Boolean {
        val l = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return l > 170
    }

    private fun cover(look: BinderLook3D, color: Int, picture: Bitmap?): Bitmap {
        val w = 600; val h = 760
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(color)
        if (picture != null) drawPicture(c, picture, look.placement, w.toFloat(), h.toFloat())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Soft light from the top left.
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0x1AFFFFFF, 0x00000000, 0x2E000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        // The crease along the hinge.
        val hinge = if (look.dir > 0) 0f else w.toFloat()
        p.shader = LinearGradient(hinge, 0f, hinge + look.dir * 70f, 0f,
            intArrayOf(0x61000000, 0x29FFFFFF, 0x33000000, 0x00000000), floatArrayOf(0f, 0.35f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        // Stitching round the edge.
        val pale = isPale(color) && picture == null
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f
        p.color = if (pale) 0x73503C00 else 0x99FFFFFF.toInt()
        p.pathEffect = DashPathEffect(floatArrayOf(16f, 11f), 0f)
        val left = if (look.dir > 0) 62f else 22f
        c.drawRoundRect(RectF(left, 22f, left + w - 84f, h - 22f), 22f, 22f, p)
        p.pathEffect = null; p.style = Paint.Style.FILL
        // The elastic strap that keeps the binder shut.
        val sx = if (look.dir > 0) w - 92f else 62f
        p.color = 0xC70F0C1E.toInt(); c.drawRect(sx, 0f, sx + 30f, h.toFloat(), p)
        p.color = 0x1FFFFFFF; c.drawRect(sx + 3f, 0f, sx + 7f, h.toFloat(), p)
        if (picture == null) {
            // A stamped name panel, like the drawn shelf.
            val cx = w / 2f + look.dir * 12f; val cy = h * 0.42f
            p.color = 0x38000000; c.drawRoundRect(RectF(cx - 170f, cy - 48f, cx + 170f, cy + 48f), 14f, 14f, p)
            p.color = 0xD9FFFFFF.toInt(); p.textSize = 40f; p.typeface = Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.CENTER
            c.drawText(ellipsize(look.name, p, 310f), cx, cy + 14f, p)
        }
        return bmp
    }

    private fun spine(look: BinderLook3D, color: Int, picture: Bitmap?): Bitmap {
        val w = 200; val h = (200 * BinderModel.HEIGHT / BinderModel.THICK).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(color)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(0x40000000, 0x24FFFFFF, 0x00000000, 0x4D000000), floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        val cream = 0xFFF6F1E4.toInt()
        var top = 60f
        if (picture != null) {
            // The window: a cream frame with the picture cropped to a square.
            p.color = cream; c.drawRoundRect(RectF(28f, 38f, w - 28f, w - 18f), 10f, 10f, p)
            c.save()
            val win = RectF(38f, 48f, w - 38f, w - 28f)
            c.clipRect(win)
            c.translate(win.left, win.top)
            drawPicture(c, picture, look.placement, win.width(), win.height())
            c.restore()
            top = w.toFloat()
        }
        // The paper label with a rainbow strip and the name running up it.
        val bottom = h - 200f
        p.color = cream; c.drawRoundRect(RectF(34f, top, w - 34f, bottom), 10f, 10f, p)
        p.shader = LinearGradient(34f, 0f, w - 34f, 0f, intArrayOf(0xFFFF7AB6.toInt(), 0xFFFFD36E.toInt(), 0xFF8CF0C8.toInt(), 0xFF7FB2FF.toInt(), 0xFFC49BFF.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(34f, top, w - 34f, top + 12f, p)
        p.shader = null
        c.save()
        val mid = (top + bottom) / 2f + 6f
        c.rotate(-90f, w / 2f, mid)
        p.color = 0xFF1B1730.toInt(); p.textSize = 46f; p.typeface = Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.CENTER
        c.drawText(ellipsize(look.name, p, bottom - top - 50f), w / 2f, mid + 16f, p)
        c.restore()
        // How full the binder is, and its size or "Shared".
        val ink = if (isPale(color)) 0xFF1B1730.toInt() else Color.WHITE
        p.color = (ink and 0x00FFFFFF) or 0x59000000; c.drawRoundRect(RectF(34f, h - 160f, w - 34f, h - 144f), 8f, 8f, p)
        p.color = ink; c.drawRoundRect(RectF(34f, h - 160f, 34f + (w - 68f) * look.fill.coerceIn(0f, 1f), h - 144f), 8f, 8f, p)
        p.textSize = 34f
        c.drawText(look.tag, w / 2f, h - 84f, p)
        return bmp
    }

    private fun lining(color: Int): Bitmap {
        val w = 600; val h = 760
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFEFE9DC.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = (color and 0x00FFFFFF) or 0x2E000000; c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0x1F000000
        c.drawRoundRect(RectF(44f, 44f, w - 44f, h - 44f), 16f, 16f, p)
        return bmp
    }

    /**
     * The binder's real first page: each pocket in its place, like the page
     * screen. Cards you have, grey missing cards, blue card backs for empty
     * pockets, and a dashed outline where we don't know the card yet.
     */
    /** How to draw the card backs on the page: the design, the binder's colour, your own picture. */
    private class Back(val choice: com.hybrid87sg.bindertracker.data.CardBackChoice, val binderColor: Int, val picture: Bitmap?)

    private fun firstPage(s: SheetLook, cards: Map<Int, Bitmap>, back: Back): Bitmap {
        val w = SHEET_PX
        val h = (SHEET_PX * SheetGrid.HEIGHT / SheetGrid.WIDTH).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(s.surface or 0xFF000000.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val byPosition = s.pockets.associateBy { it.position }
        // Where the page screen puts each card, if we've seen it; otherwise an even grid.
        val spots = s.landing?.let { SheetGrid.fromScreen(it)?.cards }?.takeIf { it.size == s.columns * s.rows }
            ?: SheetGrid.cards(s.columns, s.rows)
        spots.forEachIndexed { position, f ->
            val r = RectF(f[0] * w, f[1] * h, f[2] * w, f[3] * h)
            val corner = r.width() * 0.05f
            // The plastic sleeve, a little bigger than the card.
            val sleeve = RectF(r.left - 5f, r.top - 5f, r.right + 5f, r.bottom + 5f)
            p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); c.drawRoundRect(sleeve, corner + 4f, corner + 4f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x1A000000; c.drawRoundRect(sleeve, corner + 4f, corner + 4f, p)
            p.style = Paint.Style.FILL
            val pocket = byPosition[position]
            when (pocket?.kind) {
                SheetPocket.Kind.Card, SheetPocket.Kind.Missing -> {
                    val art = cards[position]
                    val missing = pocket.kind == SheetPocket.Kind.Missing
                    if (art != null) {
                        c.save()
                        c.clipPath(android.graphics.Path().apply { addRoundRect(r, corner, corner, android.graphics.Path.Direction.CW) })
                        p.colorFilter = if (missing) greyFilter else null
                        p.alpha = if (missing) 204 else 255
                        // Fill the pocket, cropping a little if the picture's shape is different.
                        val scale = max(r.width() / art.width, r.height() / art.height)
                        val dw = art.width * scale; val dh = art.height * scale
                        c.drawBitmap(art, null, RectF(r.centerX() - dw / 2f, r.centerY() - dh / 2f, r.centerX() + dw / 2f, r.centerY() + dh / 2f), p)
                        p.colorFilter = null; p.alpha = 255
                        c.restore()
                    } else if (missing) {
                        cardBack(c, r, corner, back)
                    } else {
                        // No picture anywhere: a plain blank card, like the page screen.
                        p.color = 0xFFE7E6EE.toInt(); c.drawRoundRect(r, corner, corner, p)
                    }
                }
                SheetPocket.Kind.Empty -> cardBack(c, r, corner, back)
                else -> {
                    p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0xFFB4B0C8.toInt()
                    p.pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
                    c.drawRoundRect(r, corner, corner, p)
                    p.pathEffect = null; p.style = Paint.Style.FILL
                    if (pocket != null) {
                        p.color = 0xFFB4B0C8.toInt(); p.textSize = r.width() * 0.3f; p.typeface = Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.CENTER
                        c.drawText("?", r.centerX(), r.centerY() + p.textSize * 0.35f, p)
                    }
                }
            }
            // A soft shine across the sleeve.
            p.shader = LinearGradient(sleeve.left, sleeve.top, sleeve.right, sleeve.bottom,
                intArrayOf(0x00FFFFFF, 0x2EFFFFFF, 0x00FFFFFF), floatArrayOf(0.25f, 0.4f, 0.55f), Shader.TileMode.CLAMP)
            c.drawRoundRect(sleeve, corner + 4f, corner + 4f, p)
            p.shader = null
        }
        return bmp
    }

    /** The card back this phone uses (see CardBackArt), clipped to the card's round corners. */
    private fun cardBack(c: Canvas, r: RectF, corner: Float, back: Back) {
        c.save()
        c.clipPath(android.graphics.Path().apply { addRoundRect(r, corner, corner, android.graphics.Path.Direction.CW) })
        c.translate(r.left, r.top)
        val style = back.choice.style.let { if (it == com.hybrid87sg.bindertracker.data.CardBackStyle.Own && back.picture == null) com.hybrid87sg.bindertracker.data.CardBackStyle.Original else it }
        com.hybrid87sg.bindertracker.ui.components.CardBackArt.draw(c, r.width(), r.height(), style, back.binderColor, back.picture, back.choice.placement)
        c.restore()
    }

    /** Grey and a little faded, like missing cards on the page screen. */
    private val greyFilter by lazy {
        val k = 0.62f
        ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0.2126f * k, 0.7152f * k, 0.0722f * k, 0f, 0f,
            0.2126f * k, 0.7152f * k, 0.0722f * k, 0f, 0f,
            0.2126f * k, 0.7152f * k, 0.0722f * k, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
    }

    private const val SHEET_PX = 768

    /** The first pocket page: nine soft-coloured cards. Same for every binder. */
    private val sheet: ByteArray by lazy {
        val w = 600; val h = 760
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF8F8FB.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val colors = intArrayOf(0xFFF7C6D9.toInt(), 0xFFFFE39B.toInt(), 0xFFBDF2DC.toInt(), 0xFFBCD6FF.toInt(), 0xFFDDC8FF.toInt(), 0xFFFFD0B5.toInt(), 0xFFC8F1FF.toInt(), 0xFFF2D7A8.toInt(), 0xFFD5F5C6.toInt())
        val pw = (w - 80f) / 3f; val ph = (h - 80f) / 3f
        colors.forEachIndexed { i, col ->
            val x = 30f + (i % 3) * (pw + 10f); val y = 30f + (i / 3) * (ph + 10f)
            p.style = Paint.Style.FILL; p.color = col; c.drawRoundRect(RectF(x, y, x + pw, y + ph), 12f, 12f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0x14000000; c.drawRoundRect(RectF(x, y, x + pw, y + ph), 12f, 12f, p)
            p.style = Paint.Style.FILL; p.color = 0x59FFFFFF; c.drawRect(x + 8f, y + 8f, x + 8f + pw * 0.35f, y + ph - 8f, p)
        }
        jpeg(bmp)
    }

    /**
     * The leather's tiny bumps, as a "normal map": each pixel's colour says
     * which way that bit of surface leans, so the light catches the grain.
     */
    private val grain: ByteArray by lazy {
        val n = 256
        val rnd = Random(7)
        var hgt = FloatArray(n * n) { rnd.nextFloat() }
        // Blur twice (wrapping round the edges, so it tiles without seams).
        repeat(2) {
            val src = hgt
            hgt = FloatArray(n * n) { i ->
                val x = i % n; val y = i / n
                var s = 0f
                for (dy in -1..1) for (dx in -1..1) s += src[((y + dy + n) % n) * n + (x + dx + n) % n]
                s / 9f
            }
        }
        // A few deeper pores.
        repeat(900) { val i = rnd.nextInt(n * n); hgt[i] -= 0.35f }
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val px = IntArray(n * n)
        val k = 4f
        for (y in 0 until n) for (x in 0 until n) {
            val dx = hgt[y * n + (x + 1) % n] - hgt[y * n + (x - 1 + n) % n]
            val dy = hgt[((y + 1) % n) * n + x] - hgt[((y - 1 + n) % n) * n + x]
            var nx = -dx * k; var ny = dy * k; var nz = 1f
            val l = sqrt(nx * nx + ny * ny + nz * nz); nx /= l; ny /= l; nz /= l
            px[y * n + x] = Color.rgb(((nx * 0.5f + 0.5f) * 255).toInt(), ((ny * 0.5f + 0.5f) * 255).toInt(), ((nz * 0.5f + 0.5f) * 255).toInt())
        }
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

    /** Draws the picture the way Adjust picture placed it (see CoverPlacement). */
    private fun drawPicture(c: Canvas, picture: Bitmap, placement: CoverPlacement, w: Float, h: Float) {
        val fit = max(w / picture.width, h / picture.height) * placement.zoom
        val dw = picture.width * fit; val dh = picture.height * fit
        val left = -(dw - w) * placement.x; val top = -(dh - h) * placement.y
        c.drawBitmap(picture, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
        if (p.measureText(text) <= maxWidth) return text
        var t = text
        while (t.length > 1 && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    private fun jpeg(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
