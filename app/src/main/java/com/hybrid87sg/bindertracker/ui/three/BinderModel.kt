package com.hybrid87sg.bindertracker.ui.three

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The shape of a 3D ring binder, built from triangles.
 *
 * The binder stands upright. Its hinge (where the front cover bends) is on
 * the up axis at x = 0, z = 0, and the front cover faces +z (towards you
 * when it faces the camera). [dir] = 1 puts the covers to the right of the
 * spine (the cover opens to the left), -1 mirrors it.
 *
 * Sizes are in made-up units (about 10 cm each): [HEIGHT] tall, covers
 * [COVER] wide, [THICK] thick.
 */
object BinderModel {
    const val HEIGHT = 2.4f
    const val COVER = 1.9f
    const val THICK = 0.62f
    const val BOARD = 0.05f

    /** The pictures painted on the binder (see BinderPaint). */
    class Pictures(
        val cover: ByteArray,
        val spine: ByteArray,
        val lining: ByteArray,
        val sheet: ByteArray,
        val grain: ByteArray, // a leather "normal map": tiny bumps the light catches
        val mime: String = "image/jpeg",
    )

    /** Builds a GLB file for one binder. [color] is linear RGB. */
    fun build(dir: Int, color: FloatArray, pictures: Pictures): ByteArray {
        val g = Glb()
        val grain = g.texture(pictures.grain, "image/png", repeat = true)
        val leather = g.material(Glb.Material("leather", floatArrayOf(color[0], color[1], color[2], 1f), roughness = 0.72f, normalTexture = grain, normalScale = 0.6f))
        val cover = g.material(Glb.Material("cover", roughness = 0.62f, baseTexture = g.texture(pictures.cover, pictures.mime), normalTexture = grain, normalScale = 0.3f))
        val spine = g.material(Glb.Material("spine", roughness = 0.66f, baseTexture = g.texture(pictures.spine, pictures.mime), normalTexture = grain, normalScale = 0.4f))
        val pages = g.material(Glb.Material("pages", srgb(0xF4, 0xF2, 0xF7), roughness = 0.6f))
        val sheet = g.material(Glb.Material("sheet", roughness = 0.35f, baseTexture = g.texture(pictures.sheet, pictures.mime)))
        val metal = g.material(Glb.Material("metal", srgb(0xD9, 0xDB, 0xE3), roughness = 0.28f, metallic = 0.35f))
        val lining = g.material(Glb.Material("lining", roughness = 0.8f, baseTexture = g.texture(pictures.lining, pictures.mime)))

        val d = dir.toFloat()
        val H = HEIGHT; val C = COVER; val T = THICK; val B = BOARD

        // Everything that doesn't move: spine, back cover, pages, rings.
        val pLeather = Glb.Part(leather); val pSpine = Glb.Part(spine); val pPages = Glb.Part(pages)
        val pSheet = Glb.Part(sheet); val pMetal = Glb.Part(metal)
        val turnSpine = -d * PI.toFloat() / 2f
        Shapes.board(pSpine, pLeather, T + 0.01f, H, B, 0.03f, Shapes.at(Vec3(-d * B / 2f, H / 2f, -T / 2f), turnSpine))
        Shapes.board(pLeather, pLeather, C, H, B, 0.07f, Shapes.at(Vec3(d * C / 2f, H / 2f, -T + B / 2f)), capUv = 3f)
        Shapes.box(pPages, Vec3(C - 0.2f, H - 0.16f, T - 2 * B - 0.12f), Shapes.at(Vec3(d * (C / 2f + 0.06f), H / 2f, -T / 2f - 0.02f)))
        Shapes.plane(pSheet, C - 0.26f, H - 0.22f, Shapes.at(Vec3(d * (C / 2f + 0.07f), H / 2f, -0.129f)))
        for (y in listOf(0.5f, H / 2f, H - 0.5f)) Shapes.torus(pMetal, 0.19f, 0.022f, Vec3(d * 0.3f, y, -T / 2f))
        Shapes.box(pMetal, Vec3(0.36f, H - 0.5f, 0.03f), Shapes.at(Vec3(d * 0.3f, H / 2f, -T + B + 0.015f)))

        // The front cover, on its own node so it can swing open.
        val pCover = Glb.Part(cover); val pEdge = Glb.Part(leather); val pLining = Glb.Part(lining)
        Shapes.board(pCover, pEdge, C, H, B, 0.07f, Shapes.at(Vec3(d * C / 2f, H / 2f, -B / 2f)))
        Shapes.plane(pLining, C - 0.08f, H - 0.08f, Shapes.at(Vec3(d * C / 2f, H / 2f, -B - 0.003f), PI.toFloat()))

        val front = g.node(Glb.Node("front", listOf(pCover, pEdge, pLining)))
        val hinge = g.node(Glb.Node("hinge", children = listOf(front)))
        val body = g.node(Glb.Node("body", listOf(pLeather, pSpine, pPages, pSheet, pMetal)))
        val root = g.node(Glb.Node("binder", children = listOf(body, hinge)))
        return g.write(listOf(root))
    }

    /** How far in front of the back cover the picture of the page sits (above the rings). */
    const val SHOT_Z = -0.1f

    /**
     * The binder that opens onto its page: the same outside as [build], but
     * [height] tall (so its inside has exactly the shape of the page on the
     * screen), and the inside painted with [shot], a picture of the page
     * screen's binder area. Folded, the whole picture fills the back cover.
     * As a two-page [spread], its two halves go on the inside of the front
     * cover and on the back cover, meeting at the spine.
     *
     * Nodes: "binder" (the root), "spine" (slides away as it lands), and
     * "hinge" (the front cover).
     */
    fun buildLanding(dir: Int, color: FloatArray, pictures: Pictures, shot: ByteArray, height: Float, spread: Boolean): ByteArray {
        val g = Glb()
        val grain = g.texture(pictures.grain, "image/png", repeat = true)
        val leather = g.material(Glb.Material("leather", floatArrayOf(color[0], color[1], color[2], 1f), roughness = 0.72f, normalTexture = grain, normalScale = 0.6f))
        val cover = g.material(Glb.Material("cover", roughness = 0.62f, baseTexture = g.texture(pictures.cover, pictures.mime), normalTexture = grain, normalScale = 0.3f))
        val spine = g.material(Glb.Material("spine", roughness = 0.66f, baseTexture = g.texture(pictures.spine, pictures.mime), normalTexture = grain, normalScale = 0.4f))
        val pages = g.material(Glb.Material("pages", srgb(0xF4, 0xF2, 0xF7), roughness = 0.6f))
        val lining = g.material(Glb.Material("lining", roughness = 0.8f, baseTexture = g.texture(pictures.lining, pictures.mime)))
        // The picture of the page: not lit, so it shows exactly the colours the page screen drew.
        val picture = g.material(Glb.Material("shot", baseTexture = g.texture(shot, "image/png"), unlitBlend = true))

        val d = dir.toFloat()
        val H = height; val C = COVER; val T = THICK; val B = BOARD

        val pLeather = Glb.Part(leather); val pPages = Glb.Part(pages)
        // (The back board sits just inside the picture's edge, so none of it peeks out round it.)
        Shapes.board(pLeather, pLeather, C - 0.04f, H - 0.04f, B, 0.07f, Shapes.at(Vec3(d * C / 2f, H / 2f, -T + B / 2f)), capUv = 3f)
        Shapes.box(pPages, Vec3(C - 0.2f, H - 0.16f, T - 2 * B - 0.12f), Shapes.at(Vec3(d * (C / 2f + 0.06f), H / 2f, -T / 2f - 0.02f)))
        // The page: folded, the whole picture; as a spread, the half on this side of the spine.
        val pShot = Glb.Part(picture)
        val backU = if (!spread) 0f to 1f else if (dir > 0) 0.5f to 1f else 0f to 0.5f
        plane(pShot, C, H, Vec3(d * C / 2f, H / 2f, SHOT_Z), backU, turned = false)

        val pSpine = Glb.Part(spine); val pSpineEdge = Glb.Part(leather)
        Shapes.board(pSpine, pSpineEdge, T + 0.01f, H, B, 0.03f, Shapes.at(Vec3(-d * B / 2f, H / 2f, -T / 2f), -d * PI.toFloat() / 2f))

        // The front cover. As a spread, its inside shows the other half of the picture.
        val pCover = Glb.Part(cover); val pEdge = Glb.Part(leather)
        Shapes.board(pCover, pEdge, C, H, B, 0.07f, Shapes.at(Vec3(d * C / 2f, H / 2f, -B / 2f)))
        val inside = if (spread) {
            val p = Glb.Part(picture)
            plane(p, C, H, Vec3(d * C / 2f, H / 2f, -B - 0.003f), if (dir > 0) 0f to 0.5f else 0.5f to 1f, turned = true)
            p
        } else {
            val p = Glb.Part(lining)
            Shapes.plane(p, C - 0.08f, H - 0.08f, Shapes.at(Vec3(d * C / 2f, H / 2f, -B - 0.003f), PI.toFloat()))
            p
        }

        val front = g.node(Glb.Node("front", listOf(pCover, pEdge, inside)))
        val hinge = g.node(Glb.Node("hinge", children = listOf(front)))
        val spineNode = g.node(Glb.Node("spine", listOf(pSpine, pSpineEdge)))
        val body = g.node(Glb.Node("body", listOf(pLeather, pPages, pShot)))
        val root = g.node(Glb.Node("binder", children = listOf(body, spineNode, hinge)))
        return g.write(listOf(root))
    }

    /**
     * A flat picture [w] by [h] centred on [centre], showing the part of its
     * picture from u = [u].first to [u].second. Facing +z, or -z when [turned]
     * (the inside of the front cover, which faces you once it's swung open).
     */
    private fun plane(part: Glb.Part, w: Float, h: Float, centre: Vec3, u: Pair<Float, Float>, turned: Boolean) {
        val place = Shapes.at(centre, if (turned) PI.toFloat() else 0f)
        val n = place.n(Vec3(0f, 0f, 1f))
        val a = part.vertex(place.p(Vec3(-w / 2f, -h / 2f, 0f)), n, u.first, 1f)
        val b = part.vertex(place.p(Vec3(w / 2f, -h / 2f, 0f)), n, u.second, 1f)
        val c = part.vertex(place.p(Vec3(w / 2f, h / 2f, 0f)), n, u.second, 0f)
        val e = part.vertex(place.p(Vec3(-w / 2f, h / 2f, 0f)), n, u.first, 0f)
        part.tri(a, b, c); part.tri(a, c, e)
    }

    /** The corners of the binder's box, before it's turned (for fitting and tapping). */
    fun bounds(dir: Int): Pair<Vec3, Vec3> {
        val minX = if (dir > 0) -BOARD else -COVER
        val maxX = if (dir > 0) COVER else BOARD
        return Vec3(minX, 0f, -THICK) to Vec3(maxX, HEIGHT, 0f)
    }

    /** sRGB 0-255 to the linear colour the 3D engine wants. */
    fun srgb(r: Int, g: Int, b: Int, a: Float = 1f) = floatArrayOf(lin(r), lin(g), lin(b), a)

    fun lin(c: Int): Float {
        val v = c / 255f
        return if (v <= 0.04045f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
}

/** Builders for the basic shapes. Each one takes a placement that moves and turns it. */
object Shapes {
    /** Moves a shape to [offset], after turning it [turnY] radians around the up axis. */
    class Place(val offset: Vec3, val turnY: Float) {
        fun p(v: Vec3) = v.rotateY(turnY) + offset
        fun n(v: Vec3) = v.rotateY(turnY)
    }

    fun at(offset: Vec3, turnY: Float = 0f) = Place(offset, turnY)

    /** A flat board with rounded corners, centred, its big faces pointing ±z. */
    fun board(caps: Glb.Part, sides: Glb.Part, w: Float, h: Float, d: Float, r: Float, place: Place, capUv: Float = 1f, sideUv: Float = 3f) {
        val outline = roundedRect(w, h, r, 6)
        val n = outline.size
        // Front and back faces, as fans of triangles from the middle.
        for (front in listOf(true, false)) {
            val z = if (front) d / 2f else -d / 2f
            val normal = place.n(Vec3(0f, 0f, if (front) 1f else -1f))
            fun uvOf(x: Float, y: Float) = ((x + w / 2f) / w * capUv) to ((1f - (y + h / 2f) / h) * capUv)
            val (cu, cv) = uvOf(0f, 0f)
            val c = caps.vertex(place.p(Vec3(0f, 0f, z)), normal, cu, cv)
            val ids = outline.map { (x, y) -> val (u, v) = uvOf(x, y); caps.vertex(place.p(Vec3(x, y, z)), normal, u, v) }
            for (i in 0 until n) {
                val a = ids[i]; val b = ids[(i + 1) % n]
                if (front) caps.tri(c, a, b) else caps.tri(c, b, a)
            }
        }
        // The edge all the way round, with smooth normals on the corners.
        val edgeNormals = (0 until n).map { i ->
            val (x0, y0) = outline[i]; val (x1, y1) = outline[(i + 1) % n]
            Vec3(y1 - y0, -(x1 - x0), 0f).normalized()
        }
        var run = 0f
        for (i in 0 until n) {
            val (x0, y0) = outline[i]; val (x1, y1) = outline[(i + 1) % n]
            val n0 = (edgeNormals[(i - 1 + n) % n] + edgeNormals[i]).normalized()
            val n1 = (edgeNormals[i] + edgeNormals[(i + 1) % n]).normalized()
            val len = Vec3(x1 - x0, y1 - y0, 0f).length()
            val a = sides.vertex(place.p(Vec3(x0, y0, -d / 2f)), place.n(n0), run * sideUv, 0f)
            val b = sides.vertex(place.p(Vec3(x1, y1, -d / 2f)), place.n(n1), (run + len) * sideUv, 0f)
            val c = sides.vertex(place.p(Vec3(x1, y1, d / 2f)), place.n(n1), (run + len) * sideUv, d * sideUv)
            val e = sides.vertex(place.p(Vec3(x0, y0, d / 2f)), place.n(n0), run * sideUv, d * sideUv)
            sides.tri(a, b, c); sides.tri(a, c, e)
            run += len
        }
    }

    /** Points round a rounded rectangle, anticlockwise, starting bottom middle. */
    fun roundedRect(w: Float, h: Float, r: Float, steps: Int): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        val corners = listOf(
            Triple(w / 2f - r, -h / 2f + r, -PI / 2), // bottom right
            Triple(w / 2f - r, h / 2f - r, 0.0),       // top right
            Triple(-w / 2f + r, h / 2f - r, PI / 2),   // top left
            Triple(-w / 2f + r, -h / 2f + r, PI),      // bottom left
        )
        for ((cx, cy, start) in corners) for (s in 0..steps) {
            val a = start + (PI / 2) * s / steps
            out += (cx + r * cos(a).toFloat()) to (cy + r * sin(a).toFloat())
        }
        return out
    }

    /** A box of [size], centred on the placement. */
    fun box(part: Glb.Part, size: Vec3, place: Place) {
        val hx = size.x / 2f; val hy = size.y / 2f; val hz = size.z / 2f
        // Each face: its normal, and two directions across it.
        val faces = listOf(
            Triple(Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f)),
            Triple(Vec3(0f, 0f, -1f), Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f)),
            Triple(Vec3(1f, 0f, 0f), Vec3(0f, 0f, -1f), Vec3(0f, 1f, 0f)),
            Triple(Vec3(-1f, 0f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, 1f, 0f)),
            Triple(Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, -1f)),
            Triple(Vec3(0f, -1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, 1f)),
        )
        for ((nrm, u, v) in faces) {
            fun ext(dv: Vec3) = Vec3(dv.x * hx, dv.y * hy, dv.z * hz)
            val c = ext(nrm); val du = ext(u); val dv = ext(v)
            val corners = listOf(c - du - dv, c + du - dv, c + du + dv, c - du + dv)
            val uvs = listOf(0f to 1f, 1f to 1f, 1f to 0f, 0f to 0f)
            val ids = corners.mapIndexed { i, p -> part.vertex(place.p(p), place.n(nrm), uvs[i].first, uvs[i].second) }
            part.tri(ids[0], ids[1], ids[2]); part.tri(ids[0], ids[2], ids[3])
        }
    }

    /** A flat rectangle facing +z (before turning), picture the right way up. */
    fun plane(part: Glb.Part, w: Float, h: Float, place: Place) {
        val n = place.n(Vec3(0f, 0f, 1f))
        val a = part.vertex(place.p(Vec3(-w / 2f, -h / 2f, 0f)), n, 0f, 1f)
        val b = part.vertex(place.p(Vec3(w / 2f, -h / 2f, 0f)), n, 1f, 1f)
        val c = part.vertex(place.p(Vec3(w / 2f, h / 2f, 0f)), n, 1f, 0f)
        val d = part.vertex(place.p(Vec3(-w / 2f, h / 2f, 0f)), n, 0f, 0f)
        part.tri(a, b, c); part.tri(a, c, d)
    }

    /** A ring lying flat (its hole points up), like a binder ring. */
    fun torus(part: Glb.Part, big: Float, small: Float, centre: Vec3, around: Int = 28, tube: Int = 8) {
        val start = part.vertexCount
        for (i in 0..around) {
            val a = 2 * PI * i / around
            val dir = Vec3(cos(a).toFloat(), 0f, sin(a).toFloat())
            for (j in 0..tube) {
                val b = 2 * PI * j / tube
                val n = dir * cos(b).toFloat() + Vec3(0f, sin(b).toFloat(), 0f)
                part.vertex(centre + dir * big + n * small, n, i.toFloat() / around, j.toFloat() / tube)
            }
        }
        for (i in 0 until around) for (j in 0 until tube) {
            val a = start + i * (tube + 1) + j
            val b = start + (i + 1) * (tube + 1) + j
            part.tri(a, a + 1, b); part.tri(b, a + 1, b + 1)
        }
    }
}
