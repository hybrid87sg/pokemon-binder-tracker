package com.hybrid87sg.bindertracker.ui.three

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * A tiny writer for GLB files: the standard 3D model format that the
 * Filament engine's loader (gltfio) reads.
 *
 * We build our binders in code instead of shipping a model file, so the
 * colour, name, picture and fill bar are always the binder's own. This file
 * only knows about triangles, materials and pictures; [BinderModel] says what
 * the binder looks like. It's plain Kotlin, so unit tests can check it.
 */
class Glb {
    /** A material. Colours are linear (not sRGB), 0 to 1. */
    class Material(
        val name: String,
        val color: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
        val roughness: Float = 0.7f,
        val metallic: Float = 0f,
        val baseTexture: Int? = null,
        val normalTexture: Int? = null,
        val normalScale: Float = 1f,
        // Unlit + see-through: for the fade layer behind an opening binder.
        val unlitBlend: Boolean = false,
        val doubleSided: Boolean = false,
    )

    /** Triangles that share one material. */
    class Part(val material: Int) {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val uv = ArrayList<Float>()
        val idx = ArrayList<Int>()
        val vertexCount get() = pos.size / 3

        fun vertex(p: Vec3, n: Vec3, u: Float, v: Float): Int {
            pos += p.x; pos += p.y; pos += p.z
            nrm += n.x; nrm += n.y; nrm += n.z
            uv += u; uv += v
            return vertexCount - 1
        }

        fun tri(a: Int, b: Int, c: Int) { idx += a; idx += b; idx += c }
    }

    class Node(val name: String, val parts: List<Part> = emptyList(), val children: List<Int> = emptyList())

    private val images = ArrayList<Pair<ByteArray, String>>()
    private val textures = ArrayList<Pair<Int, Boolean>>() // image, repeat
    val materials = ArrayList<Material>()
    val nodes = ArrayList<Node>()

    /** Adds a JPEG or PNG picture and returns its texture number. */
    fun texture(bytes: ByteArray, mime: String, repeat: Boolean = false): Int {
        images += bytes to mime
        textures += (images.size - 1) to repeat
        return textures.size - 1
    }

    fun material(m: Material): Int { materials += m; return materials.size - 1 }

    fun node(n: Node): Int { nodes += n; return nodes.size - 1 }

    /** Writes the file. [roots] are the top nodes of the scene. */
    fun write(roots: List<Int>): ByteArray {
        val bin = ByteArrayOutputStream()
        val views = StringBuilder()
        val accessors = StringBuilder()
        var viewCount = 0
        var accessorCount = 0

        fun align() { while (bin.size() % 4 != 0) bin.write(0) }
        fun addView(bytes: ByteArray, target: Int?): Int {
            align()
            val offset = bin.size()
            bin.write(bytes)
            if (viewCount > 0) views.append(',')
            views.append("{\"buffer\":0,\"byteOffset\":$offset,\"byteLength\":${bytes.size}")
            if (target != null) views.append(",\"target\":$target")
            views.append('}')
            return viewCount++
        }
        fun floats(list: List<Float>): ByteArray {
            val b = ByteBuffer.allocate(list.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            list.forEach { b.putFloat(it) }
            return b.array()
        }
        fun addAccessor(view: Int, componentType: Int, count: Int, type: String, minMax: String = ""): Int {
            if (accessorCount > 0) accessors.append(',')
            accessors.append("{\"bufferView\":$view,\"componentType\":$componentType,\"count\":$count,\"type\":\"$type\"$minMax}")
            return accessorCount++
        }

        // Meshes: one per node that has parts.
        val meshJson = StringBuilder()
        var meshCount = 0
        val nodeMesh = IntArray(nodes.size) { -1 }
        nodes.forEachIndexed { ni, node ->
            val parts = node.parts.filter { it.idx.isNotEmpty() }
            if (parts.isEmpty()) return@forEachIndexed
            if (meshCount > 0) meshJson.append(',')
            meshJson.append("{\"name\":\"${node.name}\",\"primitives\":[")
            parts.forEachIndexed { pi, part ->
                var lo = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
                var hi = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
                for (i in 0 until part.vertexCount) for (k in 0..2) {
                    lo[k] = min(lo[k], part.pos[i * 3 + k]); hi[k] = max(hi[k], part.pos[i * 3 + k])
                }
                val mm = ",\"min\":[${lo.joinToString(",") { num(it) }}],\"max\":[${hi.joinToString(",") { num(it) }}]"
                val p = addAccessor(addView(floats(part.pos), 34962), 5126, part.vertexCount, "VEC3", mm)
                val n = addAccessor(addView(floats(part.nrm), 34962), 5126, part.vertexCount, "VEC3")
                val t = addAccessor(addView(floats(part.uv), 34962), 5126, part.vertexCount, "VEC2")
                val ib = ByteBuffer.allocate(part.idx.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                part.idx.forEach { ib.putInt(it) }
                val i = addAccessor(addView(ib.array(), 34963), 5125, part.idx.size, "SCALAR")
                if (pi > 0) meshJson.append(',')
                meshJson.append("{\"attributes\":{\"POSITION\":$p,\"NORMAL\":$n,\"TEXCOORD_0\":$t},\"indices\":$i,\"material\":${part.material}}")
            }
            meshJson.append("]}")
            nodeMesh[ni] = meshCount++
        }

        val imageJson = images.joinToString(",") { (bytes, mime) ->
            "{\"bufferView\":${addView(bytes, null)},\"mimeType\":\"$mime\"}"
        }
        align()

        val json = StringBuilder()
        json.append("{\"asset\":{\"version\":\"2.0\",\"generator\":\"BinderTracker\"}")
        if (materials.any { it.unlitBlend }) json.append(",\"extensionsUsed\":[\"KHR_materials_unlit\"]")
        json.append(",\"scene\":0,\"scenes\":[{\"nodes\":[${roots.joinToString(",")}]}]")
        json.append(",\"nodes\":[")
        nodes.forEachIndexed { i, n ->
            if (i > 0) json.append(',')
            json.append("{\"name\":\"${n.name}\"")
            if (nodeMesh[i] >= 0) json.append(",\"mesh\":${nodeMesh[i]}")
            if (n.children.isNotEmpty()) json.append(",\"children\":[${n.children.joinToString(",")}]")
            json.append('}')
        }
        json.append("]")
        if (meshCount > 0) json.append(",\"meshes\":[$meshJson]")
        json.append(",\"materials\":[")
        materials.forEachIndexed { i, m ->
            if (i > 0) json.append(',')
            json.append("{\"name\":\"${m.name}\",\"pbrMetallicRoughness\":{\"baseColorFactor\":[${m.color.joinToString(",") { num(it) }}]")
            m.baseTexture?.let { json.append(",\"baseColorTexture\":{\"index\":$it}") }
            json.append(",\"metallicFactor\":${num(m.metallic)},\"roughnessFactor\":${num(m.roughness)}}")
            m.normalTexture?.let { json.append(",\"normalTexture\":{\"index\":$it,\"scale\":${num(m.normalScale)}}") }
            if (m.unlitBlend) json.append(",\"alphaMode\":\"BLEND\",\"extensions\":{\"KHR_materials_unlit\":{}}")
            if (m.doubleSided || m.unlitBlend) json.append(",\"doubleSided\":true")
            json.append('}')
        }
        json.append("]")
        if (textures.isNotEmpty()) {
            json.append(",\"samplers\":[{\"magFilter\":9729,\"minFilter\":9987,\"wrapS\":33071,\"wrapT\":33071},{\"magFilter\":9729,\"minFilter\":9987,\"wrapS\":10497,\"wrapT\":10497}]")
            json.append(",\"textures\":[${textures.joinToString(",") { (img, rep) -> "{\"sampler\":${if (rep) 1 else 0},\"source\":$img}" }}]")
            json.append(",\"images\":[$imageJson]")
        }
        if (viewCount > 0) {
            json.append(",\"buffers\":[{\"byteLength\":${bin.size()}}]")
            json.append(",\"bufferViews\":[$views]")
            json.append(",\"accessors\":[$accessors]")
        }
        json.append('}')

        val jsonBytes = json.toString().toByteArray(Charsets.UTF_8).let { b ->
            val pad = (4 - b.size % 4) % 4
            b + ByteArray(pad) { ' '.code.toByte() }
        }
        val binBytes = bin.toByteArray()
        val total = 12 + 8 + jsonBytes.size + (if (binBytes.isNotEmpty()) 8 + binBytes.size else 0)
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67) // "glTF"
        out.putInt(2)
        out.putInt(total)
        out.putInt(jsonBytes.size); out.putInt(0x4E4F534A); out.put(jsonBytes) // "JSON"
        if (binBytes.isNotEmpty()) { out.putInt(binBytes.size); out.putInt(0x004E4942); out.put(binBytes) } // "BIN"
        return out.array()
    }

    private fun num(f: Float): String = if (f.isFinite()) f.toString() else "0"
}

/** A small 3D point or direction. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = kotlin.math.sqrt(dot(this))
    fun normalized(): Vec3 { val l = length(); return if (l == 0f) this else this * (1f / l) }

    /** Turned around the up (Y) axis by [radians], the same way as the 3D engine. */
    fun rotateY(radians: Float): Vec3 {
        val c = kotlin.math.cos(radians); val s = kotlin.math.sin(radians)
        return Vec3(x * c + z * s, y, -x * s + z * c)
    }
}
