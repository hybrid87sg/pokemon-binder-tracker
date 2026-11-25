package com.hybrid87sg.bindertracker.ui.three

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.hybrid87sg.bindertracker.data.LedColour
import com.hybrid87sg.bindertracker.data.ShelfWood
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.sin

/** The words on a binder's name plate: its name, and e.g. "74 of 102" or "Shared · 12 cards". */
data class ShelfPlate(val name: String, val detail: String)

/**
 * Builds the wooden storage shelf as 3D files (GLB), like [BinderModel] does
 * for binders. Two files, so changing the LED colour doesn't rebuild the wood:
 *
 * - [unit]: the wood (boards, sides, top, back, kick plate), the metal LED
 *   channels, and a name plate under each binder.
 * - [leds]: for each cubby a strip at the back ("strip_b_<row>"), its glow on
 *   the back wall ("glow_<row>"), and a strip at the front ("strip_f_<row>").
 *   Each has its own material, so the shelf can dim them one cubby at a time.
 *
 * The real light that the strips throw onto the binders comes from point
 * lights that [ShelfStage] adds in the same places (see ShelfLayout.Bay).
 */
object ShelfUnit {
    /** The colours of each wood: main colour, darker grain, and the inside back panel. */
    class Finish(val main: Int, val dark: Int, val inside: Int)

    fun finish(wood: ShelfWood) = when (wood) {
        ShelfWood.Oak -> Finish(0xFFD9B88E.toInt(), 0xFFB48D5E.toInt(), 0xFFC8A57A.toInt())
        ShelfWood.Walnut -> Finish(0xFF7A5338.toInt(), 0xFF4E3321.toInt(), 0xFF5E4029.toInt())
        ShelfWood.White -> Finish(0xFFF2F0F5.toInt(), 0xFFCFCBD8.toInt(), 0xFFE6E3EC.toInt())
        ShelfWood.Black -> Finish(0xFF2E2B35.toInt(), 0xFF151318.toInt(), 0xFF23212A.toInt())
    }

    private fun srgb(c: Int, a: Float = 1f) = floatArrayOf(
        BinderModel.lin((c shr 16) and 0xFF), BinderModel.lin((c shr 8) and 0xFF), BinderModel.lin(c and 0xFF), a,
    )

    /** The wood, the LED channels, the name plates and the see-through fade sheet ("dim"). */
    fun unit(l: ShelfLayout, wood: ShelfWood, plates: List<ShelfPlate>, fadeColour: FloatArray): ByteArray {
        val g = Glb()
        val f = finish(wood)
        val woodTex = g.texture(png(woodGrain(f)), "image/png", repeat = true)
        val woodMat = g.material(Glb.Material("wood", roughness = 0.62f, baseTexture = woodTex))
        val inside = g.material(Glb.Material("inside", srgb(f.inside), roughness = 0.85f))
        val metal = g.material(Glb.Material("metal", srgb(0xFFB9BCC6.toInt()), roughness = 0.35f, metallic = 0.3f))
        val pw = Glb.Part(woodMat); val pi = Glb.Part(inside); val pm = Glb.Part(metal)
        for (p in l.pieces) when (p.kind) {
            ShelfLayout.Kind.Wood -> woodBox(pw, p.centre, p.size)
            ShelfLayout.Kind.Inside -> Shapes.box(pi, p.size, Shapes.at(p.centre))
            ShelfLayout.Kind.Metal -> Shapes.box(pm, p.size, Shapes.at(p.centre))
        }
        val parts = arrayListOf(pw, pi, pm)
        // Name plates: all drawn on one picture, each plate showing its own part.
        val shown = l.labels.filter { it.index < plates.size }
        if (shown.isNotEmpty()) {
            val cols = 4
            val rows = (shown.size + cols - 1) / cols
            val atlas = Bitmap.createBitmap(PLATE_W * cols, PLATE_H * rows, Bitmap.Config.ARGB_8888)
            val c = Canvas(atlas)
            shown.forEachIndexed { i, lab -> drawPlate(c, (i % cols) * PLATE_W.toFloat(), (i / cols) * PLATE_H.toFloat(), plates[lab.index]) }
            val aw = atlas.width.toFloat(); val ah = atlas.height.toFloat()
            val tex = g.texture(jpeg(atlas), "image/jpeg")
            val pp = Glb.Part(g.material(Glb.Material("plates", roughness = 0.5f, baseTexture = tex)))
            shown.forEachIndexed { i, lab ->
                val x = (i % cols) * PLATE_W; val y = (i / cols) * PLATE_H
                quad(pp, lab.centre, lab.width, lab.height, x / aw, y / ah, (x + PLATE_W) / aw, (y + PLATE_H) / ah)
                // A little metal clip holding it on.
                Shapes.box(pm, Vec3(0.05f, 0.04f, 0.03f), Shapes.at(Vec3(lab.centre.x, lab.centre.y + lab.height / 2f - 0.01f, 0.02f)))
            }
            parts += pp
        }
        val fade = g.material(Glb.Material("dim", fadeColour, unlitBlend = true))
        val pd = Glb.Part(fade)
        Shapes.plane(pd, 2f, 2f, Shapes.at(Vec3(0f, 0f, 0f)))
        val shelf = g.node(Glb.Node("shelf", parts))
        val d = g.node(Glb.Node("dim", listOf(pd)))
        return g.write(listOf(shelf, d))
    }

    /** The LED strips and back-wall glows, in [colours] blending from left to right. */
    fun leds(l: ShelfLayout, colours: List<LedColour>): ByteArray {
        val g = Glb()
        val strip = g.texture(png(stripPicture(colours)), "image/png")
        val glow = g.texture(png(glowPicture(colours)), "image/png")
        val roots = ArrayList<Int>()
        for (bay in l.bays) {
            val r = bay.row
            val back = Glb.Part(g.material(Glb.Material("strip_b_$r", unlit = true, baseTexture = strip)))
            Shapes.box(back, bay.backStrip.size, Shapes.at(bay.backStrip.centre))
            val front = Glb.Part(g.material(Glb.Material("strip_f_$r", unlit = true, baseTexture = strip)))
            Shapes.box(front, bay.frontStrip.size, Shapes.at(bay.frontStrip.centre))
            val wash = Glb.Part(g.material(Glb.Material("glow_$r", floatArrayOf(1f, 1f, 1f, 0f), unlitBlend = true, baseTexture = glow)))
            Shapes.plane(wash, bay.glow.size.x, bay.glow.size.y, Shapes.at(bay.glow.centre))
            roots += g.node(Glb.Node("strip_b_$r", listOf(back)))
            roots += g.node(Glb.Node("strip_f_$r", listOf(front)))
            roots += g.node(Glb.Node("glow_$r", listOf(wash)))
        }
        return g.write(roots)
    }

    // ---- Shapes ----

    /** A box whose wood grain runs along its longest side, at the same size everywhere. */
    private fun woodBox(part: Glb.Part, centre: Vec3, size: Vec3) {
        val hx = size.x / 2f; val hy = size.y / 2f; val hz = size.z / 2f
        val faces = listOf(
            Triple(Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f)),
            Triple(Vec3(0f, 0f, -1f), Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f)),
            Triple(Vec3(1f, 0f, 0f), Vec3(0f, 0f, -1f), Vec3(0f, 1f, 0f)),
            Triple(Vec3(-1f, 0f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, 1f, 0f)),
            Triple(Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, -1f)),
            Triple(Vec3(0f, -1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, 1f)),
        )
        fun ext(d: Vec3) = Vec3(d.x * hx, d.y * hy, d.z * hz)
        for ((nrm, u, v) in faces) {
            val c = ext(nrm); val du = ext(u); val dv = ext(v)
            val lu = 2f * du.length(); val lv = 2f * dv.length()
            // The grain lines run across the picture, so lay it along the longer side.
            val along = lu >= lv
            val corners = listOf(c - du - dv, c + du - dv, c + du + dv, c - du + dv)
            val st = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
            // A different patch of grain on each board, so they don't all match.
            val shift = abs(sin(centre.x * 3.1f + centre.y * 7.7f + centre.z * 1.3f))
            val ids = corners.mapIndexed { i, p ->
                val (a, b) = st[i]
                val uu = if (along) a * lu / GRAIN_SIZE else b * lv / GRAIN_SIZE
                val vv = if (along) b * lv / GRAIN_SIZE else a * lu / GRAIN_SIZE
                part.vertex(centre + p, nrm, uu + shift, vv + shift * 0.7f)
            }
            part.tri(ids[0], ids[1], ids[2]); part.tri(ids[0], ids[2], ids[3])
        }
    }

    /** A flat rectangle facing you, showing the part of the picture from (u0, v0) to (u1, v1). */
    private fun quad(part: Glb.Part, centre: Vec3, w: Float, h: Float, u0: Float, v0: Float, u1: Float, v1: Float) {
        val n = Vec3(0f, 0f, 1f)
        val a = part.vertex(centre + Vec3(-w / 2f, -h / 2f, 0f), n, u0, v1)
        val b = part.vertex(centre + Vec3(w / 2f, -h / 2f, 0f), n, u1, v1)
        val c = part.vertex(centre + Vec3(w / 2f, h / 2f, 0f), n, u1, v0)
        val d = part.vertex(centre + Vec3(-w / 2f, h / 2f, 0f), n, u0, v0)
        part.tri(a, b, c); part.tri(a, c, d)
    }

    // ---- Pictures ----

    private const val GRAIN_SIZE = 3f // world units one wood picture covers
    private const val PLATE_W = 400
    private const val PLATE_H = 150

    /** Wood grain: wavy lighter and darker streaks over the wood's colour. */
    private fun woodGrain(f: Finish): Bitmap {
        val n = 512
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(f.main)
        val rnd = java.util.Random(7L)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(140) { i ->
            val y = rnd.nextFloat() * n
            val h = 1f + rnd.nextFloat() * 3f
            p.color = if (rnd.nextBoolean()) 0x12000000 else 0x0FFFFFFF
            val path = Path()
            path.moveTo(0f, y)
            var x = 0f
            while (x <= n) { path.lineTo(x, y + sin(x / 60f + i) * 4f); x += 32f }
            path.lineTo(n.toFloat(), y + h); path.lineTo(0f, y + h); path.close()
            c.drawPath(path, p)
        }
        // A few long darker streaks in the grain colour.
        p.color = (f.dark and 0x00FFFFFF) or 0x22000000
        repeat(18) { i ->
            val y = rnd.nextFloat() * n
            val path = Path()
            path.moveTo(0f, y)
            var x = 0f
            while (x <= n) { path.lineTo(x, y + sin(x / 90f + i * 2) * 6f); x += 32f }
            path.lineTo(n.toFloat(), y + 2f); path.lineTo(0f, y + 2f); path.close()
            c.drawPath(path, p)
        }
        return bmp
    }

    private fun drawPlate(c: Canvas, x: Float, y: Float, plate: ShelfPlate) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = RectF(x, y, x + PLATE_W, y + PLATE_H)
        p.color = 0xFF2A2633.toInt()
        c.drawRect(r, p) // the gap round the plate (it barely shows)
        p.color = 0xFFF7F3EA.toInt()
        c.drawRoundRect(r, 14f, 14f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0x2E000000
        c.drawRoundRect(RectF(r.left + 2, r.top + 2, r.right - 2, r.bottom - 2), 13f, 13f, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.color = 0xFF1B1730.toInt(); p.textSize = 50f
        c.drawText(ellipsize(plate.name, p, 370f), x + PLATE_W / 2f, y + 52f + 17f, p)
        p.color = 0xFF5D5878.toInt(); p.textSize = 38f
        c.drawText(ellipsize(plate.detail, p, 370f), x + PLATE_W / 2f, y + 108f + 13f, p)
    }

    private fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
        if (p.measureText(text) <= maxWidth) return text
        var t = text
        while (t.length > 1 && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    /** The strip itself: the colours blending from left to right. */
    private fun stripPicture(colours: List<LedColour>): Bitmap {
        val bmp = Bitmap.createBitmap(256, 8, Bitmap.Config.ARGB_8888)
        paintBlend(Canvas(bmp), 256f, 8f, colours)
        return bmp
    }

    /** The glow on the back wall: brightest at the top under the strip, fading down and at the ends. */
    private fun glowPicture(colours: List<LedColour>): Bitmap {
        val n = 256
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        paintBlend(c, n.toFloat(), n.toFloat(), colours)
        val p = Paint()
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        p.shader = LinearGradient(
            0f, 0f, 0f, n.toFloat(),
            intArrayOf(0xFFFFFFFF.toInt(), 0xE6FFFFFF.toInt(), 0x47FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.08f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
        p.shader = LinearGradient(
            0f, 0f, n.toFloat(), 0f,
            intArrayOf(0x59000000, 0xFF000000.toInt(), 0xFF000000.toInt(), 0x59000000),
            floatArrayOf(0f, 0.12f, 0.88f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
        return bmp
    }

    private fun paintBlend(c: Canvas, w: Float, h: Float, colours: List<LedColour>) {
        val p = Paint()
        if (colours.size <= 1) {
            p.color = Led.at(colours, 0f)
        } else {
            val cols = colours.map { Led.argb(it) }.toIntArray()
            val stops = FloatArray(cols.size) { it / (cols.size - 1f) }
            p.shader = LinearGradient(0f, 0f, w, 0f, cols, stops, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, p)
    }

    private fun png(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun jpeg(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
