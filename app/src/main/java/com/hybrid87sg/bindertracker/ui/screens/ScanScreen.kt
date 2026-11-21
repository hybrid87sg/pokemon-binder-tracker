package com.hybrid87sg.bindertracker.ui.screens

import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.scan.CardCandidate
import com.hybrid87sg.bindertracker.scan.GuideFrame
import com.hybrid87sg.bindertracker.scan.PocketDraft
import com.hybrid87sg.bindertracker.scan.ScanStep
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.BottomBar
import com.hybrid87sg.bindertracker.ui.components.PrimaryButton
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * "Scan a page": the whole flow, one step at a time.
 * Camera → Reading the cards → Check and fix → Save.
 */
@Composable
fun ScanScreen(
    binder: Binder,
    step: ScanStep,
    onBack: () -> Unit,
    onPhotoTaken: (Bitmap, Int, GuideFrame) -> Unit,
    onPhotoFailed: () -> Unit,
    onRetake: () -> Unit,
    onChoose: (position: Int, card: CardCandidate?) -> Unit,
    onNameByHand: (position: Int, name: String) -> Unit,
    onSearch: suspend (String) -> List<CardCandidate>,
    onSave: () -> Unit,
) {
    when (step) {
        ScanStep.Camera -> CameraStep(binder, onBack, onPhotoTaken, onPhotoFailed)
        ScanStep.Reading -> ReadingStep()
        is ScanStep.Review -> ReviewStep(binder, step.pockets, onRetake, onChoose, onNameByHand, onSearch, onSave)
        is ScanStep.Failed -> FailedStep(step.message, onRetake, onBack)
    }
}

// ---------- Step 1: the camera ----------

/** How wide a binder page is compared to its height (a card is 63 × 88 mm, plus the sleeve). */
private fun pageAspect(binder: Binder): Float = (binder.columns * 66f) / (binder.rows * 92f)

@Composable
private fun CameraStep(
    binder: Binder,
    onBack: () -> Unit,
    onPhotoTaken: (Bitmap, Int, GuideFrame) -> Unit,
    onPhotoFailed: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // CameraX's ready-made camera "controller": it handles opening and
    // closing the camera when the screen appears and disappears.
    val controller = remember {
        LifecycleCameraController(context).apply {
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    }
    LaunchedEffect(lifecycleOwner) { controller.bindToLifecycle(lifecycleOwner) }
    var torchOn by remember { mutableStateOf(false) }
    var taking by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        // The page guide: as big as fits between the top bar and the shutter button.
        val margin = with(density) { 20.dp.toPx() }
        val topSpace = with(density) { 120.dp.toPx() }
        val bottomSpace = with(density) { 190.dp.toPx() }
        val aspect = pageAspect(binder)
        val boxW = min(viewW - 2 * margin, (viewH - topSpace - bottomSpace) * aspect)
        val boxH = boxW / aspect
        val left = (viewW - boxW) / 2
        val top = topSpace + ((viewH - topSpace - bottomSpace) - boxH) / 2
        val guide = GuideFrame(viewW.toInt(), viewH.toInt(), left, top, left + boxW, top + boxH)

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
        )

        // Darken everything outside the page guide, and draw the pocket grid inside it.
        Canvas(Modifier.fillMaxSize()) {
            val hole = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(left, top, left + boxW, top + boxH))
            }
            clipPath(hole, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.55f))
            }
            val line = 2.dp.toPx()
            drawRect(Color.White, Offset(left, top), Size(boxW, boxH), style = Stroke(width = line))
            for (c in 1 until binder.columns) {
                val x = left + boxW * c / binder.columns
                drawLine(Color.White.copy(alpha = 0.7f), Offset(x, top), Offset(x, top + boxH), strokeWidth = line / 2)
            }
            for (r in 1 until binder.rows) {
                val y = top + boxH * r / binder.rows
                drawLine(Color.White.copy(alpha = 0.7f), Offset(left, y), Offset(left + boxW, y), strokeWidth = line / 2)
            }
        }

        // Top: cancel, and the torch for dim rooms or shiny sleeves.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { torchOn = !torchOn; controller.enableTorch(torchOn) },
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) { Text(if (torchOn) "Light on" else "Light off", style = MaterialTheme.typography.titleMedium) }
        }

        // Bottom: what to do, and the shutter button.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Line up the page with the ${binder.sizeLabel} grid",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(if (taking) AppColors.Disabled else Color.White)
                    .semantics { contentDescription = "Take photo" }
                    .clickable(enabled = !taking) {
                        taking = true
                        controller.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val rotation = image.imageInfo.rotationDegrees
                                    val photo = image.toBitmap()
                                    image.close()
                                    taking = false
                                    onPhotoTaken(photo, rotation, guide)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    taking = false
                                    onPhotoFailed()
                                }
                            },
                        )
                    },
            )
        }
    }
}

// ---------- Step 2: reading ----------

@Composable
private fun ReadingStep() {
    Column(
        Modifier.fillMaxSize().background(AppColors.Paper),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = AppColors.Accent)
        Spacer(Modifier.height(18.dp))
        Text("Reading the cards…", style = MaterialTheme.typography.titleMedium)
    }
}

// ---------- Step 3: check and fix ----------

/** The "check the scan" screen. Public so the screenshot test can draw it. */
@Composable
fun ReviewStep(
    binder: Binder,
    pockets: List<PocketDraft>,
    onRetake: () -> Unit,
    onChoose: (position: Int, card: CardCandidate?) -> Unit,
    onNameByHand: (position: Int, name: String) -> Unit,
    onSearch: suspend (String) -> List<CardCandidate>,
    onSave: () -> Unit,
) {
    var editing by remember { mutableStateOf<PocketDraft?>(null) }
    val found = pockets.count { !it.empty && !it.needsHelp }
    val help = pockets.count { it.needsHelp }
    val empty = pockets.count { it.empty }

    Column(Modifier.fillMaxSize().background(AppColors.Paper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp)) {
            BackButton("Retake", onRetake)
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Check the scan", style = MaterialTheme.typography.headlineMedium)
            Text(
                buildList {
                    add("$found found")
                    if (help > 0) add("$help to name")
                    if (empty > 0) add("$empty empty")
                }.joinToString(" · ") + ". Tap a pocket to fix it.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Muted,
            )
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pockets.chunked(binder.columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { pocket ->
                        PocketTile(pocket, Modifier.weight(1f)) { editing = pocket }
                    }
                }
            }
        }

        BottomBar {
            PrimaryButton(text = "Save page", onClick = onSave, icon = R.drawable.ic_check)
        }
    }

    editing?.let { pocket ->
        PocketDialog(
            pocket = pocket,
            columns = binder.columns,
            onSearch = onSearch,
            onPick = { card -> onChoose(pocket.position, card); editing = null },
            onNameByHand = { name -> onNameByHand(pocket.position, name); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun PocketTile(pocket: PocketDraft, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = when {
        pocket.empty -> AppColors.Border
        pocket.needsHelp -> AppColors.Danger
        else -> AppColors.BorderStrong
    }
    Column(
        modifier
            .aspectRatio(63f / 88f)
            .clip(shape)
            .background(if (pocket.empty) AppColors.ShelfBackground else AppColors.White)
            .border(if (pocket.needsHelp) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            pocket.empty -> Text("Empty", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            pocket.needsHelp -> Text("Tap to name", style = MaterialTheme.typography.bodySmall, color = AppColors.Danger, fontWeight = FontWeight.SemiBold)
            else -> {
                Text(
                    pocket.displayName.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                pocket.chosen?.setName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                pocket.displayNumber?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, maxLines = 1)
                }
            }
        }
    }
}

/** "Row 2, pocket 3" — easier to follow than a single number. */
private fun pocketLabel(position: Int, columns: Int) = "Row ${position / columns + 1}, pocket ${position % columns + 1}"

@Composable
private fun PocketDialog(
    pocket: PocketDraft,
    columns: Int,
    onSearch: suspend (String) -> List<CardCandidate>,
    onPick: (CardCandidate?) -> Unit,
    onNameByHand: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(pocket.displayName.orEmpty()) }
    var results by remember { mutableStateOf(pocket.candidates) }
    // Search the card index a moment after the person stops typing.
    LaunchedEffect(query) {
        if (query.isBlank() || query == pocket.displayName) return@LaunchedEffect
        delay(300)
        results = onSearch(query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pocketLabel(pocket.position, columns)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pocket.read.text.isNotBlank()) {
                    Text(
                        "Read from the photo: " + pocket.read.text.lines().take(3).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Muted,
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Card name or number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                results.take(8).forEach { card ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(card) }.padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Text(card.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("${card.setName} · ${card.number}", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                    HorizontalDivider(color = AppColors.Border)
                }
                if (query.isNotBlank() && query != pocket.displayName) {
                    OutlinedButton(onClick = { onNameByHand(query) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Use “${query.trim()}”", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = { onPick(null) }) { Text("Mark empty") }
        },
    )
}

// ---------- If something goes wrong ----------

@Composable
private fun FailedStep(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AppColors.Paper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp)) {
            BackButton("Binder", onBack)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Scan didn't work", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted, textAlign = TextAlign.Center)
        }
        BottomBar {
            PrimaryButton(text = "Try again", onClick = onRetry, icon = R.drawable.ic_camera)
        }
    }
}
