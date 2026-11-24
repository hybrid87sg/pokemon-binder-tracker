package com.hybrid87sg.bindertracker.ui.screens

import android.Manifest
import android.os.Build
import android.util.Size
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hybrid87sg.bindertracker.permissions.Permissions
import com.hybrid87sg.bindertracker.scan.RingCodeReader
import com.hybrid87sg.bindertracker.scan.RingGuide
import com.hybrid87sg.bindertracker.ui.components.BackButton
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

private val ScanDark = Color(0xFF0E1220)
private val ScanLight = Color(0xFFF4F6FB)
private val RingRed = Color(0xFFFF4D5E)
private val RingGreen = Color(0xFF2FD27A)

/**
 * A ring code seen in one camera picture, in the picture's upright
 * coordinates (already turned the way the phone is held). [width] and
 * [height] are the upright picture's size.
 */
private class Spot(val x: Float, val y: Float, val radius: Float, val width: Float, val height: Float, val code: String?)

/**
 * Our own camera screen for binder ring codes. The round guide shows what the
 * camera sees: white dashed while it looks, red with a tip when a code is in
 * view but not lined up, and green once the whole code is inside the ring.
 * A code is only scanned while it's inside the ring, and the first clear read
 * there counts.
 */
@Composable
fun RingScanScreen(onFound: (String) -> Unit, onTypeInstead: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var allowed by remember { mutableStateOf(Permissions.hasCamera(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA) }
    BackHandler(onBack = onBack)

    // What the camera saw lately. A code must be seen twice in a row before the
    // ring reacts, and missed a few times before it goes back to white, so the
    // ring doesn't flicker.
    var spot by remember { mutableStateOf<Spot?>(null) }
    var seenInRow by remember { mutableStateOf(0) }
    var missedInRow by remember { mutableStateOf(0) }
    var scanned by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scanned) {
        val code = scanned ?: return@LaunchedEffect
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
        )
        // Long enough to see the ring turn green.
        delay(300)
        onFound(code)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(ScanDark)) {
        val density = LocalDensity.current
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val ringDp = minOf(RingGuide.RING_DP.dp, maxWidth - 24.dp)
        val ringR = with(density) { ringDp.toPx() } / 2

        // Where the code sits on screen. The camera fills the screen and is cut
        // to fit (FILL_CENTER), so the picture is scaled by the larger factor.
        val shown = spot?.takeIf { seenInRow >= 2 || scanned != null }
        val verdict = when {
            scanned != null -> RingGuide.Verdict(RingGuide.State.LinedUp)
            shown == null -> RingGuide.Verdict(RingGuide.State.Looking)
            else -> {
                val scale = maxOf(viewW / shown.width, viewH / shown.height)
                RingGuide.judge(
                    dx = (shown.x - shown.width / 2) * scale,
                    dy = (shown.y - shown.height / 2) * scale,
                    radius = shown.radius * scale,
                    ring = ringR,
                )
            }
        }

        if (allowed) {
            RingCamera { seen ->
                if (scanned != null) return@RingCamera
                if (seen == null) {
                    missedInRow++
                    if (missedInRow >= 3) {
                        spot = null
                        seenInRow = 0
                    }
                    return@RingCamera
                }
                missedInRow = 0
                seenInRow++
                spot = seen
                val code = seen.code ?: return@RingCamera
                val scale = maxOf(viewW / seen.width, viewH / seen.height)
                val inside = RingGuide.judge(
                    (seen.x - seen.width / 2) * scale,
                    (seen.y - seen.height / 2) * scale,
                    seen.radius * scale,
                    ringR,
                ).state == RingGuide.State.LinedUp
                if (inside) scanned = code
            }
        }

        // The round guide to line the code up with.
        val ringColor by animateColorAsState(
            when (verdict.state) {
                RingGuide.State.Looking -> Color.White.copy(alpha = 0.85f)
                RingGuide.State.NotLinedUp -> RingRed
                RingGuide.State.LinedUp -> RingGreen
            },
            animationSpec = tween(180),
            label = "ringColour",
        )
        val glow by animateFloatAsState(if (verdict.state == RingGuide.State.LinedUp) 1f else 0f, tween(180), label = "ringGlow")
        val looking = verdict.state == RingGuide.State.Looking
        Canvas(Modifier.align(Alignment.Center).size(ringDp + 24.dp)) {
            val r = ringDp.toPx() / 2
            if (glow > 0f) {
                drawCircle(RingGreen.copy(alpha = 0.25f * glow), radius = r, style = Stroke(16.dp.toPx()))
            }
            drawCircle(
                ringColor,
                radius = r,
                style = if (looking) {
                    Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 9.dp.toPx())))
                } else {
                    Stroke(5.dp.toPx())
                },
            )
        }

        // The tip above the ring.
        val tip = when (verdict.state) {
            RingGuide.State.Looking -> null
            RingGuide.State.NotLinedUp -> verdict.tip?.words
            RingGuide.State.LinedUp -> "Scanning…"
        }
        var lastTip by remember { mutableStateOf("") }
        if (tip != null) lastTip = tip
        val tipAlpha by animateFloatAsState(if (tip != null) 1f else 0f, tween(180), label = "tipFade")
        Box(
            Modifier.align(Alignment.Center).offset(y = -(ringDp / 2 + 30.dp)).alpha(tipAlpha)
                .background(if (verdict.state == RingGuide.State.LinedUp) RingGreen else RingRed, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(lastTip, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }

        Box(Modifier.fillMaxWidth().background(ScanDark.copy(alpha = 0.6f)).statusBarsPadding().padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
            BackButton("Back", onBack, color = ScanLight)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(ScanDark.copy(alpha = 0.6f))
                .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (allowed) "Fit the binder code on your friend's phone inside the ring" else "The camera is needed to scan a binder code",
                style = MaterialTheme.typography.titleMedium,
                color = ScanLight,
                textAlign = TextAlign.Center,
            )
            if (!allowed) {
                TextButton(onClick = { ask.launch(Manifest.permission.CAMERA) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Allow the camera", color = ScanLight)
                }
            }
            TextButton(onClick = onTypeInstead, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Type the code instead", color = ScanLight)
            }
        }
    }
}

@Composable
private fun RingCamera(onSeen: (Spot?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val seen by rememberUpdatedState(onSeen)
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            setImageAnalysisResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build(),
            )
            setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        var gray = IntArray(0)
        controller.setImageAnalysisAnalyzer(executor) { proxy ->
            try {
                run {
                    // The camera's first layer is brightness only, which is all the reader needs.
                    val plane = proxy.planes[0]
                    val w = proxy.width
                    val h = proxy.height
                    if (gray.size != w * h) gray = IntArray(w * h)
                    val buffer = plane.buffer
                    val row = plane.rowStride
                    val step = plane.pixelStride
                    for (y in 0 until h) {
                        val start = y * row
                        for (x in 0 until w) gray[y * w + x] = buffer.get(start + x * step).toInt() and 0xFF
                    }
                    val found = RingCodeReader.look(gray, w, h)
                    // Turn the spot the way the phone is held, to match the screen.
                    val spot = found?.let { f ->
                        when (proxy.imageInfo.rotationDegrees) {
                            90 -> Spot(h - f.y, f.x, f.radius, h.toFloat(), w.toFloat(), f.code)
                            180 -> Spot(w - f.x, h - f.y, f.radius, w.toFloat(), h.toFloat(), f.code)
                            270 -> Spot(f.y, w - f.x, f.radius, h.toFloat(), w.toFloat(), f.code)
                            else -> Spot(f.x, f.y, f.radius, w.toFloat(), h.toFloat(), f.code)
                        }
                    }
                    main.execute { seen(spot) }
                }
            } catch (_: Throwable) {
                // A bad picture: just wait for the next one.
            } finally {
                proxy.close()
            }
        }
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            executor.shutdown()
        }
    }
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
