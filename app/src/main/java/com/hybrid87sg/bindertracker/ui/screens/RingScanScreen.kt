package com.hybrid87sg.bindertracker.ui.screens

import android.Manifest
import android.util.Size
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hybrid87sg.bindertracker.permissions.Permissions
import com.hybrid87sg.bindertracker.scan.RingCodeReader
import com.hybrid87sg.bindertracker.ui.components.BackButton
import java.util.concurrent.Executors

private val ScanDark = Color(0xFF0E1220)
private val ScanLight = Color(0xFFF4F6FB)

/**
 * Our own camera screen for binder ring codes. It reads every camera picture
 * it can, and once the same code comes out twice in a row it calls [onFound].
 * Reading twice makes sure a half-seen code never counts.
 */
@Composable
fun RingScanScreen(onFound: (String) -> Unit, onTypeInstead: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Permissions.hasCamera(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA) }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(ScanDark)) {
        if (allowed) RingCamera(onFound)

        // The round guide to line the code up with.
        Canvas(Modifier.align(Alignment.Center).size(260.dp)) {
            drawCircle(
                Color.White.copy(alpha = 0.85f),
                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
            )
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
                if (allowed) "Point the camera at the binder code on your friend's phone" else "The camera is needed to scan a binder code",
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
private fun RingCamera(onFound: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val found by rememberUpdatedState(onFound)
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
        var last: String? = null
        var done = false
        controller.setImageAnalysisAnalyzer(executor) { proxy ->
            try {
                if (!done) {
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
                    val code = RingCodeReader.read(gray, w, h)
                    if (code != null && code == last) {
                        done = true
                        main.execute { found(code) }
                    }
                    last = code
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
