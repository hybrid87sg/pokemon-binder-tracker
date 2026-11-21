package com.hybrid87sg.bindertracker.ui.screens

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hybrid87sg.bindertracker.GuideRect
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.ui.components.BackButton
import java.io.File
import java.util.UUID

private val Dark = Color(0xFF121211)
private val Light = Color(0xFFF6F4EF)
private val LightMuted = Color(0xFFB8B4A8)

/**
 * The camera screen with the real camera (CameraX).
 *
 * The camera is set up with "what you see is what you get": the photo is
 * cropped to exactly what the preview shows, so the frame's position on
 * screen is also its position in the photo.
 */
@Composable
fun ScanRoute(binder: Binder, onBack: () -> Unit, onCaptured: (File, GuideRect) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    var previewBounds by remember { mutableStateOf<Rect?>(null) }
    var guideBounds by remember { mutableStateOf<Rect?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun capture() {
        val preview = previewBounds ?: return
        val guide = guideBounds ?: return
        capturing = true
        error = null
        // Where the frame sits inside the preview, as fractions of its size.
        val fractions = GuideRect(
            left = ((guide.left - preview.left) / preview.width).coerceIn(0f, 1f),
            top = ((guide.top - preview.top) / preview.height).coerceIn(0f, 1f),
            right = ((guide.right - preview.left) / preview.width).coerceIn(0f, 1f),
            bottom = ((guide.bottom - preview.top) / preview.height).coerceIn(0f, 1f),
        )
        val file = File(context.filesDir, "pages/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    onCaptured(file, fractions)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    error = "Couldn't take the photo. Hold the phone still and try again."
                }
            },
        )
    }

    ScanScreen(
        binder = binder,
        capturing = capturing,
        error = error,
        onBack = onBack,
        onCapture = ::capture,
        onPreviewPositioned = { previewBounds = it },
        onGuidePositioned = { guideBounds = it },
        cameraPreview = { modifier ->
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
                modifier = modifier,
            )
        },
    )
}

/** The look of the camera screen. [cameraPreview] is swapped for a plain box in tests. */
@Composable
fun ScanScreen(
    binder: Binder,
    capturing: Boolean,
    error: String?,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    onPreviewPositioned: (Rect) -> Unit,
    onGuidePositioned: (Rect) -> Unit,
    cameraPreview: @Composable (Modifier) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Dark)) {
        cameraPreview(Modifier.fillMaxSize().onGloballyPositioned { onPreviewPositioned(it.boundsInRoot()) })

        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().background(Dark.copy(alpha = 0.6f)).statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton("Back", onBack, color = Light)
                Spacer(Modifier.weight(1f))
                Text("${binder.name} · ${binder.sizeLabel}", style = MaterialTheme.typography.bodyMedium, color = LightMuted)
            }

            // The frame, sized to the binder's shape (e.g. 3 columns of cards × 3 rows).
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                val pageAspect = (binder.columns * 63f) / (binder.rows * 88f)
                val byWidth = maxWidth
                val byHeight = maxHeight * pageAspect
                val frameWidth = if (byWidth < byHeight) byWidth else byHeight
                PocketGuide(
                    columns = binder.columns,
                    rows = binder.rows,
                    modifier = Modifier
                        .size(frameWidth, frameWidth / pageAspect)
                        .onGloballyPositioned { onGuidePositioned(it.boundsInRoot()) },
                )
            }

            // Hint and shutter button
            Column(
                Modifier.fillMaxWidth().background(Dark.copy(alpha = 0.6f)).navigationBarsPadding().padding(top = 12.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    error ?: "Fit the page inside the frame. Each box is one pocket.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) Color(0xFFFFB4A8) else Light,
                )
                Surface(
                    onClick = onCapture,
                    enabled = !capturing,
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(4.dp, Light),
                    modifier = Modifier.size(76.dp).semantics { contentDescription = "Take photo of binder page" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (capturing) {
                            CircularProgressIndicator(color = Light, modifier = Modifier.size(32.dp))
                        } else {
                            Box(Modifier.size(56.dp).background(Light, CircleShape))
                        }
                    }
                }
            }
        }
    }
}

/** White frame split into dashed pocket boxes. */
@Composable
private fun PocketGuide(columns: Int, rows: Int, modifier: Modifier) {
    Box(
        modifier
            .border(2.dp, Light, RoundedCornerShape(14.dp))
            .drawBehind {
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                val stroke = 1.5.dp.toPx()
                val line = Light.copy(alpha = 0.7f)
                for (c in 1 until columns) {
                    val x = size.width * c / columns
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke, pathEffect = dash)
                }
                for (r in 1 until rows) {
                    val y = size.height * r / rows
                    drawLine(line, Offset(0f, y), Offset(size.width, y), stroke, pathEffect = dash)
                }
            },
    )
}
