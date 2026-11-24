package com.hybrid87sg.bindertracker.ui.three

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.Matrix
import android.util.LruCache
import android.view.Choreographer
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.PBRNeutralToneMapper
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.hybrid87sg.bindertracker.data.OfflineArt
import com.hybrid87sg.bindertracker.data.OwnPhotos
import com.hybrid87sg.bindertracker.data.ShelfLook
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Starts the 3D engine once. False if this phone can't run it (then we use the drawn shelf). */
object Engine3D {
    val ready: Boolean by lazy {
        runCatching { Filament.init(); Gltfio.init(); true }.getOrDefault(false)
    }
}

/** Finished 3D binder files, so coming back to the shelf doesn't paint them again. */
private object GlbCache {
    val binders = LruCache<BinderLook3D, ByteArray>(24)
}

/** The colours of the room around the binders (they follow light and dark mode). */
data class ShelfColors(val wood: Color, val panel: Color, val paper: Color)

/**
 * The My binders shelf with real 3D binders.
 *
 * Tap a binder: it comes off the shelf, turns to face you and its cover
 * swings open (towards the side the shelf is turned to). If it has pages, the
 * camera then glides into its first page, which shows the real cards, and
 * [onOpen] gets that page's ID; otherwise the page ID is null.
 * Hold a binder: [onEdit]. [onOpening] reports how far the opening has got
 * (0 to 1), so the screen can fade its title out.
 */
@Composable
fun Shelf3D(
    binders: List<BinderLook3D>,
    look: ShelfLook,
    colors: ShelfColors,
    onOpen: (binderId: String, pageId: String?) -> Unit,
    onEdit: (String) -> Unit,
    onOpening: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val open by rememberUpdatedState(onOpen)
    val edit by rememberUpdatedState(onEdit)
    val progress by rememberUpdatedState(onOpening)
    val stage = remember {
        ShelfStage(context, object : ShelfStage.Listener {
            override fun onOpen(id: String, pageId: String?) = open(id, pageId)
            override fun onEdit(id: String) = edit(id)
            override fun onOpening(p: Float) = progress(p)
        })
    }
    DisposableEffect(stage) { onDispose { stage.destroy() } }
    LaunchedEffect(binders, look, colors) {
        // Paint and build each binder away from the main thread, then show them.
        // First with only the card pictures already on the phone, so the shelf
        // appears straight away; then again for any page still missing a picture,
        // this time fetching it from the internet.
        val first = withContext(Dispatchers.Default) { binders.map { b -> build(context, b, online = false) } }
        stage.show(first.map { it.first to it.second }, look, colors)
        if (first.all { it.third }) return@LaunchedEffect
        val second = withContext(Dispatchers.Default) {
            first.map { (b, glb, complete) -> if (complete) b to glb else build(context, b, online = true).let { it.first to it.second } }
        }
        stage.show(second, look, colors)
    }
    AndroidView(factory = { stage.textureView }, modifier = modifier)
}

/**
 * Builds one binder's 3D file (or takes it from the cache). The third value
 * is false when a card picture on its first page couldn't be found yet.
 */
private suspend fun build(context: Context, b: BinderLook3D, online: Boolean): Triple<BinderLook3D, ByteArray, Boolean> {
    GlbCache.binders.get(b)?.let { return Triple(b, it, true) }
    val picture = b.picturePath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    val cards = HashMap<Int, Bitmap>()
    var complete = true
    b.sheet?.pockets?.forEach { p ->
        if (p.kind != SheetPocket.Kind.Card && p.kind != SheetPocket.Kind.Missing) return@forEach
        val art = cardPicture(context, p, online)
        if (art != null) cards[p.position] = art else complete = false
    }
    // Your own card back picture, for the empty pockets on the page.
    val backPicture = if (b.sheet != null && b.back.style == com.hybrid87sg.bindertracker.data.CardBackStyle.Own) b.back.picturePath?.let { decodeSmall(File(it)) } else null
    val color = BinderPaint.parse(b.colorHex)
    val glb = BinderModel.build(
        b.dir,
        floatArrayOf(BinderModel.lin(android.graphics.Color.red(color)), BinderModel.lin(android.graphics.Color.green(color)), BinderModel.lin(android.graphics.Color.blue(color))),
        BinderPaint.pictures(b, picture, cards, backPicture),
    )
    picture?.recycle()
    backPicture?.recycle()
    // Only remember it once every picture is in, so a missing one gets another go next time.
    if (complete || online) GlbCache.binders.put(b, glb)
    return Triple(b, glb, complete)
}

/**
 * One card's small picture for the 3D page, from the same places as the page
 * screen: the copy saved on the phone, then TCGdex, then the backup site
 * (through the app's picture cache), then your own photo of the card.
 */
private suspend fun cardPicture(context: Context, p: SheetPocket, online: Boolean): Bitmap? {
    OfflineArt.find(context, p.cardId)?.let { f -> decodeSmall(f)?.let { return it } }
    val loader = SingletonImageLoader.get(context)
    for (url in listOfNotNull(p.url, p.backupUrl)) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(CARD_PX, CARD_PX * 88 / 63)
            .allowHardware(false)
            .networkCachePolicy(if (online) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .build()
        val result = runCatching { loader.execute(request) }.getOrNull()
        if (result is SuccessResult) return runCatching { result.image.toBitmap() }.getOrNull()
    }
    return OwnPhotos.find(context, p.cardId)?.let { decodeSmall(it) }
}

private const val CARD_PX = 240

/** Reads a picture file at about the size the 3D page needs. */
private fun decodeSmall(file: File): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= CARD_PX) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

/** Runs the 3D engine for the shelf: the binders, the light, the camera and touches. */
internal class ShelfStage(context: Context, private val listener: Listener) : UiHelper.RendererCallback {
    interface Listener {
        fun onOpen(id: String, pageId: String?)
        fun onEdit(id: String)
        fun onOpening(p: Float)
    }

    val textureView = TextureView(context)
    private val engine = Engine.create()
    private val renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val frontScene = engine.createScene() // the binder that's opening, drawn over the fade
    private val view = engine.createView()
    private val frontView = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera = engine.createCamera(cameraEntity)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var swapChain: SwapChain? = null
    private val materials = UbershaderProvider(engine)
    private val loader = AssetLoader(engine, materials, EntityManager.get())
    private val resources = ResourceLoader(engine)
    private val sun = EntityManager.get().create()
    private val fill = EntityManager.get().create()
    private val ambient = IndirectLight.Builder().irradiance(1, floatArrayOf(0.62f, 0.6f, 0.66f)).intensity(1f).build(engine)

    private class Item(val look: BinderLook3D, val glb: ByteArray, val asset: FilamentAsset, val hinge: Int) {
        var lift = 0f
        var liftTarget = 0f
        var spot = Vec3(0f, 0f, 0f)
    }

    private var items = listOf<Item>()
    private var shelfAsset: FilamentAsset? = null
    private var dim = 0
    private var layout: ShelfLayout? = null
    private var width = 0
    private var height = 0
    private val fovY = 28f
    private var fitCam: Cam? = null
    private var pan = 0f
    private var panMax = 0f
    private var worldPerPx = 0.01f
    private var destroyed = false
    private var dirty = true

    // The opening animation.
    private var opening: Item? = null
    private var openStart = 0L
    private var openSent = false
    private var openPlan: OpenPlan? = null

    private class OpenPlan(val start: Vec3, val turn: Float, val closed: Vec3, val open: Vec3, val eye0: Vec3, val look0: Vec3, val eyeEnd: Vec3, val lookEnd: Vec3, val dir: Int, val toPage: Boolean)

    init {
        renderer.setClearOptions(Renderer.ClearOptions().apply { clear = true; setClearColor(0.0, 0.0, 0.0, 0.0) })
        val grading = ColorGrading.Builder().toneMapper(PBRNeutralToneMapper()).build(engine)
        for (v in listOf(view, frontView)) {
            v.setCamera(camera)
            v.setBlendMode(View.BlendMode.TRANSLUCENT)
            v.setAntiAliasing(View.AntiAliasing.FXAA)
            v.setMultiSampleAntiAliasingOptions(View.MultiSampleAntiAliasingOptions().apply { enabled = true; sampleCount = 4 })
            v.setColorGrading(grading)
        }
        view.setScene(scene)
        frontView.setScene(frontScene)
        camera.setExposure(1f)
        // A soft light from the top left that casts shadows, and a cool fill light from the right.
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 0.98f, 0.95f).intensity(2.3f).direction(0.29f, -0.21f, -0.93f)
            .castShadows(true)
            .shadowOptions(LightManager.ShadowOptions().apply { mapSize = 2048; normalBias = 1f })
            .build(engine, sun)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.86f, 0.82f, 1f).intensity(0.6f).direction(-0.8f, -0.3f, -0.5f)
            .build(engine, fill)
        for (s in listOf(scene, frontScene)) {
            s.addEntity(sun); s.addEntity(fill)
            s.setIndirectLight(ambient)
        }
        uiHelper.setOpaque(false)
        uiHelper.setRenderCallback(this)
        uiHelper.attachTo(textureView)
        textureView.isOpaque = false
        setUpTouches()
    }

    // ---- Showing the binders ----

    fun show(built: List<Pair<BinderLook3D, ByteArray>>, look: ShelfLook, colors: ShelfColors) {
        if (destroyed || opening != null) return
        // Keep binders that haven't changed, swap the rest.
        val old = items.associateBy { it.look }
        val next = built.mapNotNull { (b, glb) -> old[b]?.takeIf { it.glb === glb } ?: load(b, glb) }
        items.filter { it !in next }.forEach { remove(it.asset) }
        items = next
        val l = ShelfLayout(items.size, look.angle, look.coversOnRight)
        layout = l
        items.forEachIndexed { i, it -> it.spot = l.spots[i].position }
        shelfAsset?.let { remove(it) }
        shelfAsset = loadAsset(shelfGlb(l, colors))?.also { a ->
            dim = a.getFirstEntityByName("dim")
            setDim(0f, colors.paper)
        }
        dimColor = colors.paper
        fit()
        dirty = true
    }

    private var dimColor = Color.White

    private fun load(b: BinderLook3D, glb: ByteArray): Item? {
        val asset = loadAsset(glb) ?: return null
        return Item(b, glb, asset, asset.getFirstEntityByName("hinge"))
    }

    private fun loadAsset(glb: ByteArray): FilamentAsset? {
        val buffer = ByteBuffer.allocateDirect(glb.size).order(ByteOrder.nativeOrder()).put(glb)
        buffer.flip()
        val asset = loader.createAsset(buffer) ?: return null
        resources.loadResources(asset)
        asset.releaseSourceData()
        val rm = engine.renderableManager
        for (e in asset.renderableEntities) {
            val i = rm.getInstance(e)
            rm.setCastShadows(i, true)
            rm.setReceiveShadows(i, true)
        }
        scene.addEntities(asset.entities)
        return asset
    }

    private fun remove(asset: FilamentAsset) {
        scene.removeEntities(asset.entities)
        frontScene.removeEntities(asset.entities)
        loader.destroyAsset(asset)
    }

    /** The planks, the back of each shelf, and a see-through sheet used to fade the room. */
    private fun shelfGlb(l: ShelfLayout, colors: ShelfColors): ByteArray {
        val g = Glb()
        fun lin(c: Color, a: Float = 1f): FloatArray {
            val f = { v: Float -> if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f) }
            return floatArrayOf(f(c.red), f(c.green), f(c.blue), a)
        }
        val wood = g.material(Glb.Material("wood", lin(colors.wood), roughness = 0.55f))
        val panel = g.material(Glb.Material("panel", lin(colors.panel), roughness = 0.95f))
        val fade = g.material(Glb.Material("dim", lin(colors.paper, 0f), unlitBlend = true))
        val pw = Glb.Part(wood); val pp = Glb.Part(panel); val pd = Glb.Part(fade)
        l.planks.forEach { Shapes.box(pw, it.size, Shapes.at(it.centre)) }
        l.panels.forEach { Shapes.box(pp, it.size, Shapes.at(it.centre)) }
        Shapes.plane(pd, 2f, 2f, Shapes.at(Vec3(0f, 0f, 0f)))
        val shelf = g.node(Glb.Node("shelf", listOf(pw, pp)))
        val d = g.node(Glb.Node("dim", listOf(pd)))
        return g.write(listOf(shelf, d))
    }

    private fun setDim(alpha: Float, color: Color) {
        if (dim == 0) return
        val rm = engine.renderableManager
        val inst = rm.getInstance(dim)
        if (inst == 0) return
        val f = { v: Float -> if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f) }
        rm.getMaterialInstanceAt(inst, 0).setParameter("baseColorFactor", Colors.RgbaType.LINEAR, f(color.red), f(color.green), f(color.blue), alpha)
        rm.setLayerMask(inst, 0xff, if (alpha > 0.001f) 0xff else 0)
    }

    // ---- Camera ----

    /** Points the camera so the whole width of the shelf fits, top shelf at the top. */
    private fun fit() {
        val l = layout ?: return
        if (width == 0 || height == 0) return
        val aspect = width.toFloat() / height
        camera.setProjection(fovY.toDouble(), aspect.toDouble(), 0.1, 100.0, Camera.Fov.VERTICAL)
        val pts = listOf(l.min.x, l.max.x).flatMap { x -> listOf(l.min.y, l.max.y).flatMap { y -> listOf(l.min.z, l.max.z).map { z -> Vec3(x, y, z) } } }
        val tanH = kotlin.math.tan(Math.toRadians(fovY / 2.0)).toFloat()
        val margin = 12f * textureView.resources.displayMetrics.density
        val tw = width - 2 * margin
        val th = height - 2 * margin
        var cx = (l.min.x + l.max.x) / 2f
        var cy = (l.min.y + l.max.y) / 2f
        var d = (l.max.x - l.min.x) / (2f * tanH * aspect) * 1.1f
        val oneRowHigh = l.spots.isNotEmpty() && l.panels.size <= 2
        repeat(6) {
            val cam = Cam(Vec3(cx, cy + 0.12f * d, d), Vec3(cx, cy, 0f), fovY, aspect)
            var lft = Float.MAX_VALUE; var rgt = -Float.MAX_VALUE; var top = Float.MAX_VALUE; var bot = -Float.MAX_VALUE
            for (p in pts) {
                val (sx, sy) = cam.project(p)
                lft = min(lft, sx * width); rgt = max(rgt, sx * width); top = min(top, sy * height); bot = max(bot, sy * height)
            }
            val scale = if (oneRowHigh) max((rgt - lft) / tw, (bot - top) / th) else (rgt - lft) / tw
            val k = d * 2f * tanH / height
            cx -= (width / 2f - (lft + rgt) / 2f) * k
            // Top aligned when it's tall (you can drag to see more), centred when it fits.
            val wantTop = if ((bot - top) / scale <= th) (height - (bot - top) / scale) / 2f else margin
            cy += (wantTop - top) * k
            d *= scale
        }
        fitCam = Cam(Vec3(cx, cy + 0.12f * d, d), Vec3(cx, cy, 0f), fovY, aspect)
        worldPerPx = d * 2f * tanH / height
        // How far you can drag down to see lower shelves.
        val cam = fitCam!!
        var bot = -Float.MAX_VALUE
        for (p in pts) bot = max(bot, cam.project(p).second * height)
        panMax = max(0f, (bot - (height - margin)) * worldPerPx)
        pan = pan.coerceIn(0f, panMax)
        placeCamera(currentCam())
    }

    private fun currentCam(): Cam {
        val c = fitCam ?: Cam(Vec3(0f, 1f, 10f), Vec3(0f, 1f, 0f), fovY, 1f)
        return Cam(c.eye - Vec3(0f, pan, 0f), c.target - Vec3(0f, pan, 0f), fovY, c.aspect)
    }

    private fun placeCamera(c: Cam) {
        camera.lookAt(c.eye.x.toDouble(), c.eye.y.toDouble(), c.eye.z.toDouble(), c.target.x.toDouble(), c.target.y.toDouble(), c.target.z.toDouble(), 0.0, 1.0, 0.0)
    }

    // ---- Touches ----

    private var pressed: Item? = null

    private fun setUpTouches() {
        val detector = GestureDetector(textureView.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                if (opening != null) return false
                pressed = pick(e.x, e.y)?.also { it.liftTarget = 0.22f; dirty = true }
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val it = pressed ?: return false
                pressed = null
                startOpening(it)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val it = pressed ?: return
                pressed = null
                it.liftTarget = 0f
                textureView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                listener.onEdit(it.look.id)
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                pressed?.liftTarget = 0f
                pressed = null
                if (panMax <= 0f) return false
                pan = (pan + dy * worldPerPx).coerceIn(0f, panMax)
                placeCamera(currentCam())
                dirty = true
                return true
            }
        })
        textureView.setOnTouchListener { v, e ->
            val handled = detector.onTouchEvent(e)
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                pressed?.liftTarget = 0f
                pressed = null
                dirty = true
                if (e.actionMasked == MotionEvent.ACTION_UP && !handled) v.performClick()
            }
            true
        }
    }

    /** The binder under a finger, if any. */
    private fun pick(x: Float, y: Float): Item? {
        val l = layout ?: return null
        if (width == 0) return null
        val cam = currentCam()
        val ray = cam.ray(x / width, y / height)
        val (lo, hi) = BinderModel.bounds(l.dir)
        var best: Item? = null
        var bestT = Float.MAX_VALUE
        for (it in items) {
            // Turn the ray into the binder's own space, where its box is simple.
            val o = (cam.eye - it.spot).rotateY(-l.turn)
            val d = ray.rotateY(-l.turn)
            val t = hitBox(o, d, lo, hi) ?: continue
            if (t < bestT) { bestT = t; best = it }
        }
        return best
    }

    // ---- Opening ----

    private fun startOpening(item: Item) {
        val l = layout ?: return
        val cam = currentCam()
        item.liftTarget = 0f
        // Where the closed binder sits in front of you: the open spread fills the width.
        val aspect = width.toFloat() / height
        val tanH = kotlin.math.tan(Math.toRadians(fovY / 2.0)).toFloat()
        val dist = (2f * BinderModel.COVER * 1.04f) / (2f * tanH * aspect)
        val centre = cam.eye + cam.forward * dist
        val d = l.dir.toFloat()
        val H = BinderModel.HEIGHT
        val C = BinderModel.COVER
        val closed = Vec3(centre.x - d * C / 2f, centre.y - H / 2f, centre.z)
        val open = Vec3(centre.x, centre.y - H / 2f, centre.z)
        // With pages: end looking straight at the first page, so it fills the screen
        // (where the page screen takes over). Without: a view of the open binder.
        val toPage = item.look.sheet != null
        val page = Vec3(open.x + d * (C / 2f + 0.07f), open.y + H / 2f, open.z - 0.129f)
        val pageDist = max(SheetGrid.WIDTH / (0.94f * 2f * tanH * aspect), SheetGrid.HEIGHT / (0.86f * 2f * tanH))
        val eyeEnd = if (toPage) page + Vec3(0f, 0f, pageDist) else Vec3(centre.x + d * C * 0.52f, centre.y, centre.z + H * 1.05f)
        openPlan = OpenPlan(
            start = item.spot + Vec3(0f, item.lift * 0.25f, item.lift),
            turn = l.turn, closed = closed, open = open,
            eye0 = cam.eye, look0 = cam.target,
            eyeEnd = eyeEnd, lookEnd = if (toPage) page else Vec3(eyeEnd.x, centre.y, centre.z),
            dir = l.dir, toPage = toPage,
        )
        // Draw it over the fade.
        scene.removeEntities(item.asset.entities)
        frontScene.addEntities(item.asset.entities)
        opening = item
        openSent = false
        openStart = System.nanoTime()
        dirty = true
    }

    private fun stepOpening(now: Long) {
        val item = opening ?: return
        val plan = openPlan ?: return
        val t = ((now - openStart) / 1e6f / (if (plan.toPage) PAGE_MILLIS else OPEN_MILLIS)).coerceIn(0f, 1f)
        val p1 = out(seg(t, 0f, 0.14f)); val p2 = ease(seg(t, 0.1f, 0.5f))
        // Going to the page: the cover opens a little sooner, then the camera glides into the page.
        val p3 = if (plan.toPage) ease(seg(t, 0.42f, 0.8f)) else ease(seg(t, 0.46f, 0.84f))
        val p4 = if (plan.toPage) ease(seg(t, 0.6f, 0.95f)) else ease(seg(t, 0.8f, 1f))
        var pos = plan.start + Vec3(0f, 0.12f * p1, 0.8f * p1)
        pos = mix(pos, plan.closed, p2)
        pos = mix(pos, plan.open, p3)
        setBinder(item, pos, plan.turn * (1f - p2), -0.05f * sin(p2 * PI.toFloat()))
        setHinge(item, -plan.dir * 176f * p3)
        val eye = mix(plan.eye0, plan.eyeEnd, p4)
        val target = mix(plan.look0, plan.lookEnd, p4)
        val cam = Cam(eye, target, fovY, width.toFloat() / height)
        placeCamera(cam)
        // The fade sheet sits just in front of the camera.
        placeDim(cam)
        setDim(0.9f * ease(seg(t, 0.05f, 0.45f)), dimColor)
        listener.onOpening(t)
        if (!openSent && t >= (if (plan.toPage) 0.93f else 0.86f)) {
            openSent = true
            listener.onOpen(item.look.id, item.look.sheet?.pageId)
        }
        if (t >= 1f) finishOpening()
    }

    private fun finishOpening() {
        val item = opening ?: return
        frontScene.removeEntities(item.asset.entities)
        scene.addEntities(item.asset.entities)
        setHinge(item, 0f)
        opening = null
        setDim(0f, dimColor)
        placeCamera(currentCam())
        listener.onOpening(0f)
        dirty = true
    }

    private fun placeDim(cam: Cam) {
        if (dim == 0) return
        val p = cam.eye + cam.forward * 0.5f
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, p.x, p.y, p.z)
        Matrix.scaleM(m, 0, 2f, 2f, 1f)
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(dim), m)
    }

    private fun setBinder(item: Item, pos: Vec3, turn: Float, tilt: Float) {
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, pos.x, pos.y, pos.z)
        Matrix.rotateM(m, 0, Math.toDegrees(turn.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(m, 0, Math.toDegrees(tilt.toDouble()).toFloat(), 1f, 0f, 0f)
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(item.asset.root), m)
    }

    private fun setHinge(item: Item, degrees: Float) {
        if (item.hinge == 0) return
        val m = FloatArray(16)
        Matrix.setRotateM(m, 0, degrees, 0f, 1f, 0f)
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(item.hinge), m)
    }

    // ---- Drawing ----

    private var lastFrame = 0L

    private val frame: Choreographer.FrameCallback by lazy { object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            if (destroyed) return
            Choreographer.getInstance().postFrameCallback(this)
            val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1e9f).coerceIn(0f, 0.05f)
            lastFrame = now
            val l = layout
            if (opening != null) {
                stepOpening(now)
                dirty = true
            } else if (l != null) {
                // Binders you press lift out of the shelf a little.
                for (it in items) {
                    if (it.lift != it.liftTarget || dirty) {
                        val k = 1f - 0.0001f.pow(dt)
                        it.lift += (it.liftTarget - it.lift) * k
                        if (kotlin.math.abs(it.lift - it.liftTarget) < 0.002f) it.lift = it.liftTarget
                        setBinder(it, it.spot + Vec3(0f, it.lift * 0.25f, it.lift), l.turn, 0f)
                        dirty = true
                    }
                }
            }
            if (!dirty) return
            val chain = swapChain ?: return
            if (!uiHelper.isReadyToRender) return
            if (renderer.beginFrame(chain, now)) {
                renderer.render(view)
                if (opening != null) renderer.render(frontView)
                renderer.endFrame()
                dirty = opening != null
            }
        }
    } }

    init {
        // Start drawing once everything above is set up.
        Choreographer.getInstance().postFrameCallback(frame)
    }

    // ---- The surface we draw on ----

    override fun onNativeWindowChanged(surface: android.view.Surface) {
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
        dirty = true
    }

    override fun onDetachedFromSurface() {
        swapChain?.let {
            engine.destroySwapChain(it)
            engine.flushAndWait()
            swapChain = null
        }
    }

    override fun onResized(width: Int, height: Int) {
        this.width = width
        this.height = height
        view.setViewport(Viewport(0, 0, width, height))
        frontView.setViewport(Viewport(0, 0, width, height))
        fit()
        dirty = true
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        Choreographer.getInstance().removeFrameCallback(frame)
        uiHelper.detach()
        items.forEach { remove(it.asset) }
        items = emptyList()
        shelfAsset?.let { remove(it) }
        shelfAsset = null
        resources.destroy()
        loader.destroy()
        materials.destroyMaterials()
        materials.destroy()
        engine.destroyEntity(sun)
        engine.destroyEntity(fill)
        engine.destroyIndirectLight(ambient)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyView(frontView)
        engine.destroyScene(scene)
        engine.destroyScene(frontScene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(sun)
        EntityManager.get().destroy(fill)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }

    companion object {
        const val OPEN_MILLIS = 1500f
        /** Opening onto the first page takes a little longer (the glide into the page). */
        const val PAGE_MILLIS = 1800f
        private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
        private fun ease(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
        private fun out(t: Float) = 1f - (1f - t).pow(3)
        private fun mix(a: Vec3, b: Vec3, t: Float) = a + (b - a) * t
    }
}
