package com.hybrid87sg.bindertracker.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hybrid87sg.bindertracker.GuideRect
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.scan.LiveCheck
import com.hybrid87sg.bindertracker.scan.LiveStep
import com.hybrid87sg.bindertracker.scan.PageReader
import com.hybrid87sg.bindertracker.scan.ReadCard
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.roundToInt

private val CamDark = Color(0xFF121211)
private val CamLight = Color(0xFFF6F4EF)
private val CamMuted = Color(0xFFB8B4A8)
private val LockGreen = Color(0xFF8CF0C8)
private val WaitGold = Color(0xFFFFD36E)

// How long a card can be in view, without a sure match, before search opens.
private const val SEARCH_AFTER_MS = 4_000L
// How often the camera picture is read (at most).
private const val LOOK_EVERY_MS = 200L

/** How far each check got, for the list under the camera. */
enum class StepState { Waiting, Checking, Found, NotFound, Skipped }

data class StepRow(val state: StepState = StepState.Waiting, val note: String = "")

/** The four checks, in order: number, name, moves, artwork. */
data class Ladder(
    val number: StepRow = StepRow(),
    val name: StepRow = StepRow(),
    val moves: StepRow = StepRow(),
    val artwork: StepRow = StepRow(),
)

/** What live scanning has to work with: the checks and what to do with the answer. */
class LiveScanActions(
    val check: suspend (ReadCard) -> LiveCheck,
    val artwork: suspend (ReadCard, Bitmap, List<Card>) -> Card?,
    // Put the card in a pocket.
    val place: (position: Int, card: Card, picture: Bitmap?) -> Unit,
    // Not sure: open search for this pocket with the name typed in.
    val unsure: (position: Int, name: String, picture: Bitmap?, guesses: List<Card>) -> Unit,
    // "Choose from list" without a name.
    val chooseFromList: (position: Int) -> Unit,
    // "Take a photo instead": the old way.
    val photo: (position: Int, file: File, guide: GuideRect) -> Unit,
    val done: () -> Unit,
)

/** Where the frame sits on the camera preview (in screen pixels), to cut it out of each camera picture. */
private data class CropBox(val previewW: Float, val previewH: Float, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /**
     * Cuts the frame out of an upright camera picture. The preview fills the
     * screen and trims the picture's edges ("fill centre"), so we undo that.
     * A little extra round the frame is kept, so the card's edges are in.
     */
    fun cut(picture: Bitmap): Bitmap? {
        val w = picture.width.toFloat()
        val h = picture.height.toFloat()
        if (w <= 0f || h <= 0f || previewW <= 0f || previewH <= 0f) return null
        val scale = max(previewW / w, previewH / h)
        val offX = (w * scale - previewW) / 2f
        val offY = (h * scale - previewH) / 2f
        val margin = (right - left) * 0.06f
        val l = (((left - margin) + offX) / scale).roundToInt().coerceIn(0, picture.width - 1)
        val t = (((top - margin) + offY) / scale).roundToInt().coerceIn(0, picture.height - 1)
        val r = (((right + margin) + offX) / scale).roundToInt().coerceIn(l + 1, picture.width)
        val b = (((bottom + margin) + offY) / scale).roundToInt().coerceIn(t + 1, picture.height)
        val cut = Bitmap.createBitmap(picture, l, t, r - l, b - t)
        // Big enough for the small print, small enough to read quickly.
        val maxH = 1400
        return if (cut.height <= maxH) cut else Bitmap.createScaledBitmap(cut, (cut.width * maxH / cut.height).coerceAtLeast(1), maxH, true)
    }
}

/**
 * Scans one card without taking a photo: the camera reads the card a few
 * times a second. Checks, in order, stopping at the first that's sure:
 * 1. the card number and set size (e.g. 025/198),
 * 2. the name,
 * 3. the moves (to tell apart cards with the same name),
 * 4. the artwork (once, only when the others couldn't decide).
 * A card must match on two looks in a row, so a half-read number never
 * fills a pocket. If nothing is sure after a few seconds, search opens with
 * the name already typed in.
 */
@Composable
fun LiveScanRoute(
    startPosition: Int,
    pockets: List<PocketWithCard>,
    autoNext: Boolean,
    onAutoNextChange: (Boolean) -> Unit,
    picturesListReady: Boolean,
    actions: LiveScanActions,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            // Preview and live pictures in the same shape, so the frame lines up with both.
            setPreviewResolutionSelector(
                ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build(),
            )
            setImageAnalysisResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    // Sharp enough to read the small card number.
                    .setResolutionStrategy(ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build(),
            )
            setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            setImageAnalysisOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    val textReader = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val frames = remember { Channel<Bitmap>(Channel.CONFLATED) }
    val busy = remember { AtomicBoolean(false) }
    val crop = remember { AtomicReference<CropBox?>(null) }
    var previewBounds by remember { mutableStateOf<Rect?>(null) }
    var guideBounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(previewBounds, guideBounds) {
        val p = previewBounds
        val g = guideBounds
        crop.set(if (p == null || g == null) null else CropBox(p.width, p.height, g.left - p.left, g.top - p.top, g.right - p.left, g.bottom - p.top))
    }

    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        var last = 0L
        controller.setImageAnalysisAnalyzer(executor) { proxy ->
            try {
                val now = SystemClock.elapsedRealtime()
                val box = crop.get()
                if (box != null && !busy.get() && now - last >= LOOK_EVERY_MS) {
                    last = now
                    val full = proxy.toBitmap()
                    val turn = proxy.imageInfo.rotationDegrees
                    val upright = if (turn == 0) full else {
                        Bitmap.createBitmap(full, 0, 0, full.width, full.height, Matrix().apply { postRotate(turn.toFloat()) }, true)
                    }
                    box.cut(upright)?.let { picture ->
                        busy.set(true)
                        frames.trySend(picture)
                    }
                }
            } catch (_: Throwable) {
                busy.set(false)
            } finally {
                proxy.close()
            }
        }
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            executor.shutdown()
            textReader.close()
        }
    }

    var position by remember { mutableIntStateOf(startPosition) }
    var ladder by remember { mutableStateOf(Ladder()) }
    var status by remember { mutableStateOf("Hold the card inside the frame") }
    var locked by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val currentPockets by rememberUpdatedState(pockets)
    val currentAutoNext by rememberUpdatedState(autoNext)

    LaunchedEffect(Unit) {
        var firstSeen = 0L
        var lastSureId: String? = null
        var lastUnsureName: String? = null
        var artworkTried = false
        var needGap = false
        var lastPlacedId: String? = null
        val placed = mutableSetOf<Int>()

        fun reset() {
            firstSeen = 0L; lastSureId = null; lastUnsureName = null; artworkTried = false
            ladder = Ladder(); locked = false
        }

        // Puts the card in the pocket. Returns true when the screen is finished.
        suspend fun place(card: Card, picture: Bitmap): Boolean {
            locked = true
            actions.place(position, card, picture)
            placed += position
            lastPlacedId = card.id
            added = "Added ${card.name} to pocket ${position + 1}"
            status = "Got it"
            val next = if (currentAutoNext) nextPocketToScan(currentPockets, position, placed) else null
            delay(900)
            added = null
            if (next == null) {
                actions.done()
                return true
            }
            position = next
            needGap = true
            reset()
            status = "Now pocket ${next + 1}. Take the card away and show the next one."
            return false
        }

        for (picture in frames) {
            val read = runCatching { PageReader.readCard(textReader, picture) }.getOrNull()
            if (read == null) { busy.set(false); continue }
            val check = runCatching { actions.check(read) }.getOrDefault(LiveCheck.NoCard)
            val now = SystemClock.elapsedRealtime()

            // After a card is added, wait for it to be taken away (or a different card).
            if (needGap) {
                val different = check is LiveCheck.Sure && check.card.id != lastPlacedId
                if (check is LiveCheck.NoCard || different) needGap = false
                if (needGap) { busy.set(false); continue }
            }

            when (check) {
                is LiveCheck.NoCard -> {
                    reset()
                    status = "Hold the card inside the frame"
                }
                is LiveCheck.Sure -> {
                    if (firstSeen == 0L) firstSeen = now
                    ladder = ladderFor(read, check)
                    if (check.card.id == lastSureId) {
                        if (place(check.card, picture)) break
                    } else {
                        lastSureId = check.card.id
                        status = "Found ${check.card.name}. Hold still…"
                    }
                }
                is LiveCheck.Unsure -> {
                    if (firstSeen == 0L) firstSeen = now
                    lastSureId = null
                    val steady = check.name != null && check.name == lastUnsureName
                    lastUnsureName = check.name
                    ladder = ladderFor(read, check, artwork = if (artworkTried) StepRow(StepState.NotFound, "no sure match") else StepRow())
                    when {
                        steady && !artworkTried -> {
                            artworkTried = true
                            ladder = ladder.copy(artwork = StepRow(StepState.Checking, ""))
                            status = if (picturesListReady) "Comparing the artwork…" else "Getting the card pictures list (one time)…"
                            val card = runCatching { actions.artwork(read, picture, check.shortlist) }.getOrNull()
                            if (card != null) {
                                ladder = ladder.copy(artwork = StepRow(StepState.Found, "1 clear match"))
                                if (place(card, picture)) break
                            } else {
                                ladder = ladder.copy(artwork = StepRow(StepState.NotFound, "no sure match"))
                                status = "Still not sure…"
                            }
                        }
                        artworkTried && check.name != null && now - firstSeen >= SEARCH_AFTER_MS -> {
                            actions.unsure(position, check.name, picture, check.shortlist)
                            break
                        }
                        check.name == null && now - firstSeen >= SEARCH_AFTER_MS ->
                            status = "Can't read this card. Move closer, or tilt it away from the light."
                        else -> status = "Reading…"
                    }
                }
            }
            busy.set(false)
        }
    }

    fun takePhoto() {
        val preview = previewBounds ?: return
        val guide = guideBounds ?: return
        capturing = true
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
                    actions.photo(position, file, fractions)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    status = "Couldn't take the photo. Hold the phone still and try again."
                }
            },
        )
    }

    LiveScanScreen(
        position = position,
        ladder = ladder,
        status = status,
        locked = locked,
        added = added,
        autoNext = autoNext,
        onAutoNextChange = onAutoNextChange,
        capturing = capturing,
        onBack = onBack,
        onChooseFromList = { actions.chooseFromList(position) },
        onTakePhoto = ::takePhoto,
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

/**
 * The next pocket on the page still waiting for a card: after [from] first,
 * then from the start. Skips pockets marked empty and ones just filled.
 */
fun nextPocketToScan(pockets: List<PocketWithCard>, from: Int, placed: Set<Int>): Int? {
    val open = pockets.filter { it.cardId == null && !it.isEmpty && it.position != from && it.position !in placed }.map { it.position }.sorted()
    return open.firstOrNull { it > from } ?: open.firstOrNull()
}

/** Fills in the list of checks from one look. */
fun ladderFor(read: ReadCard, check: LiveCheck, artwork: StepRow = StepRow()): Ladder {
    val printed = read.printedNumber
    return when (check) {
        is LiveCheck.NoCard -> Ladder()
        is LiveCheck.Sure -> when (check.step) {
            LiveStep.Number -> Ladder(
                number = StepRow(StepState.Found, printed.orEmpty()),
                name = if (read.name != null) StepRow(StepState.Found, "matches") else StepRow(StepState.Skipped, "not needed"),
                moves = StepRow(StepState.Skipped, "not needed"),
                artwork = StepRow(StepState.Skipped, "not needed"),
            )
            LiveStep.Name -> Ladder(
                number = numberRow(read),
                name = StepRow(StepState.Found, check.card.name),
                moves = StepRow(StepState.Skipped, "not needed"),
                artwork = StepRow(StepState.Skipped, "not needed"),
            )
            LiveStep.Moves -> Ladder(
                number = numberRow(read),
                name = StepRow(StepState.Found, "${check.card.name} · ${check.sameName} cards"),
                moves = StepRow(StepState.Found, "1 match"),
                artwork = StepRow(StepState.Skipped, "not needed"),
            )
            LiveStep.Artwork -> Ladder(
                number = numberRow(read),
                name = StepRow(StepState.Found, check.card.name),
                moves = StepRow(StepState.Skipped, ""),
                artwork = StepRow(StepState.Found, "1 clear match"),
            )
        }
        is LiveCheck.Unsure -> Ladder(
            number = numberRow(read),
            name = when {
                check.name == null -> StepRow(StepState.Checking, "reading…")
                check.sameName == 0 -> StepRow(StepState.NotFound, "no card called ${check.name}")
                else -> StepRow(StepState.Found, "${check.name} · ${check.sameName} cards")
            },
            moves = when {
                check.name == null || check.sameName == 0 -> StepRow(StepState.Waiting, "")
                check.movesNarrowed -> StepRow(StepState.Found, "${check.sameName} → ${check.shortlist.size} cards")
                else -> StepRow(StepState.NotFound, "didn't narrow it")
            },
            artwork = artwork,
        )
    }
}

private fun numberRow(read: ReadCard): StepRow =
    read.printedNumber?.let { StepRow(StepState.NotFound, "$it not found") } ?: StepRow(StepState.NotFound, "can't read")

/** The look of the live camera screen. [cameraPreview] is swapped for a plain box in tests. */
@Composable
fun LiveScanScreen(
    position: Int,
    ladder: Ladder,
    status: String,
    locked: Boolean,
    added: String?,
    autoNext: Boolean,
    onAutoNextChange: (Boolean) -> Unit,
    capturing: Boolean,
    onBack: () -> Unit,
    onChooseFromList: () -> Unit,
    onTakePhoto: () -> Unit,
    onPreviewPositioned: (Rect) -> Unit,
    onGuidePositioned: (Rect) -> Unit,
    cameraPreview: @Composable (Modifier) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(CamDark)) {
        cameraPreview(Modifier.fillMaxSize().onGloballyPositioned { onPreviewPositioned(it.boundsInRoot()) })

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(CamDark.copy(alpha = 0.6f)).statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton("Back", onBack, color = CamLight)
                Spacer(Modifier.weight(1f))
                Text("Scan pocket ${position + 1}", style = MaterialTheme.typography.titleMedium, color = CamLight)
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp, vertical = 20.dp), contentAlignment = Alignment.Center) {
                val aspect = 63f / 88f
                val frameWidth = if (maxWidth < maxHeight * aspect) maxWidth else maxHeight * aspect
                val corner by animateColorAsState(if (locked) LockGreen else CamLight, label = "corners")
                Box(
                    Modifier.width(frameWidth).aspectRatio(aspect)
                        .onGloballyPositioned { onGuidePositioned(it.boundsInRoot()) }
                        .drawBehind {
                            // Corner brackets round the card.
                            val len = 24.dp.toPx()
                            val w = 3.5.dp.toPx()
                            val s = size
                            listOf(
                                Offset(0f, 0f) to Offset(1f, 1f), Offset(s.width, 0f) to Offset(-1f, 1f),
                                Offset(0f, s.height) to Offset(1f, -1f), Offset(s.width, s.height) to Offset(-1f, -1f),
                            ).forEach { (p, d) ->
                                drawLine(corner, p, Offset(p.x + d.x * len, p.y), w, cap = StrokeCap.Round)
                                drawLine(corner, p, Offset(p.x, p.y + d.y * len), w, cap = StrokeCap.Round)
                            }
                        },
                )
                Text(
                    added ?: status,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .background(if (added != null) AppColors.Success else CamDark.copy(alpha = 0.7f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }

            // The checks, and the other ways to fill the pocket.
            Column(
                Modifier.fillMaxWidth()
                    .background(AppColors.Surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                LadderRow(1, "Card number & set", ladder.number)
                LadderRow(2, "Name", ladder.name)
                LadderRow(3, "Moves", ladder.moves)
                LadderRow(4, "Artwork", ladder.artwork)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Then scan the next empty pocket", style = MaterialTheme.typography.bodyMedium, color = AppColors.Ink, modifier = Modifier.weight(1f))
                    Switch(checked = autoNext, onCheckedChange = onAutoNextChange)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onChooseFromList) { Text("Choose from list", color = AppColors.AccentText, fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = onTakePhoto, enabled = !capturing) {
                        Text(if (capturing) "Taking photo…" else "Take a photo instead", color = AppColors.AccentText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LadderRow(number: Int, label: String, row: StepRow) {
    val (dot, onDot) = when (row.state) {
        StepState.Found -> AppColors.Success to AppColors.White
        StepState.Checking -> AppColors.Accent to AppColors.White
        StepState.NotFound -> AppColors.CheckFill to AppColors.Black
        StepState.Waiting, StepState.Skipped -> AppColors.Surface2 to AppColors.Muted
    }
    val faded = row.state == StepState.Skipped
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(24.dp).background(dot, CircleShape)
                .then(if (row.state == StepState.Checking) Modifier.border(2.dp, WaitGold, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (row.state == StepState.Found) "✓" else "$number", color = onDot, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (faded) AppColors.Muted else AppColors.Ink, modifier = Modifier.weight(1f))
        Text(
            row.note,
            fontSize = 13.sp,
            color = when (row.state) {
                StepState.Found -> AppColors.SuccessText
                StepState.NotFound -> AppColors.Check
                else -> AppColors.Muted
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f, fill = false),
        )
    }
}
