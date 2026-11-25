package com.hybrid87sg.bindertracker.ui.three

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/** The words under a binder: its name, and e.g. "74 of 102" or "Shared · 12 cards". */
data class ShelfPlate(val name: String, val detail: String)

/** The colours of the shelf: its ledges, and the name and count under each binder. */
data class ShelfPaint(val ledge: Int, val ink: Int, val muted: Int)

/**
 * Builds the simple shelf as one 3D file (GLB), like [BinderModel] does for
 * binders: a thin ledge under each row, the name and count under each binder
 * (Cover side), and the see-through fade sheet ("dim") used while a binder opens.
 */
object ShelfUnit {
    private fun srgb(c: Int, a: Float = 1f) = floatArrayOf(
        BinderModel.lin((c shr 16) and 0xFF), BinderModel.lin((c shr 8) and 0xFF), BinderModel.lin(c and 0xFF), a,
    )

    fun unit(l: ShelfLayout, plates: List<ShelfPlate>, paint: ShelfPaint, fadeColour: FloatArray): ByteArray {
        val g = Glb()
        val ledge = g.material(Glb.Material("ledge", srgb(paint.ledge), roughness = 0.55f))
        val pl = Glb.Part(ledge)
        for (p in l.ledges) Shapes.box(pl, p.size, Shapes.at(p.centre))
        val parts = arrayListOf(pl)
        // Captions: all drawn on one see-through picture, each showing its own part.
        val shown = l.labels.filter { it.index < plates.size }
        if (shown.isNotEmpty()) {
            val cols = 4
            val rows = (shown.size + cols - 1) / cols
            val w = ShelfLayout.CAPTION_W; val h = ShelfLayout.CAPTION_H
            val atlas = Bitmap.createBitmap(w * cols, h * rows, Bitmap.Config.ARGB_8888)
            val c = Canvas(atlas)
            shown.forEachIndexed { i, lab -> drawCaption(c, (i % cols) * w.toFloat(), (i / cols) * h.toFloat(), plates[lab.index], paint) }
            val aw = atlas.width.toFloat(); val ah = atlas.height.toFloat()
            val tex = g.texture(png(atlas), "image/png")
            val pc = Glb.Part(g.material(Glb.Material("captions", floatArrayOf(1f, 1f, 1f, 1f), unlitBlend = true, baseTexture = tex)))
            shown.forEachIndexed { i, lab ->
                val x = (i % cols) * w; val y = (i / cols) * h
                quad(pc, lab.centre, lab.width, lab.height, x / aw, y / ah, (x + w) / aw, (y + h) / ah)
            }
            parts += pc
        }
        val fade = g.material(Glb.Material("dim", fadeColour, unlitBlend = true))
        val pd = Glb.Part(fade)
        Shapes.plane(pd, 2f, 2f, Shapes.at(Vec3(0f, 0f, 0f)))
        val shelf = g.node(Glb.Node("shelf", parts))
        val d = g.node(Glb.Node("dim", listOf(pd)))
        return g.write(listOf(shelf, d))
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

    /** The binder's name in bold, and its count underneath, on a clear background. */
    private fun drawCaption(c: Canvas, x: Float, y: Float, plate: ShelfPlate, paint: ShelfPaint) {
        val w = ShelfLayout.CAPTION_W.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.color = paint.ink; p.textSize = 54f
        c.drawText(ellipsize(plate.name, p, w - 24f), x + w / 2f, y + 62f, p)
        p.typeface = Typeface.DEFAULT
        p.color = paint.muted; p.textSize = 42f
        c.drawText(ellipsize(plate.detail, p, w - 24f), x + w / 2f, y + 124f, p)
    }

    private fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
        if (p.measureText(text) <= maxWidth) return text
        var t = text
        while (t.length > 1 && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    private fun png(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
