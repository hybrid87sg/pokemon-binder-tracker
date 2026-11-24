package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.hybrid87sg.bindertracker.data.ExpansionOption
import com.hybrid87sg.bindertracker.ui.components.CollectingSheet
import com.hybrid87sg.bindertracker.ui.components.holoTopStripe
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.BinderResize
import com.hybrid87sg.bindertracker.data.BinderSize
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.data.CoverPicture
import com.hybrid87sg.bindertracker.data.CoverPlacement
import com.hybrid87sg.bindertracker.data.ShelfLook
import com.hybrid87sg.bindertracker.ui.components.BinderTurn
import com.hybrid87sg.bindertracker.ui.components.CoverLook
import com.hybrid87sg.bindertracker.ui.components.colorFromHex
import com.hybrid87sg.bindertracker.ui.components.coverPictureSize
import com.hybrid87sg.bindertracker.ui.components.drawCover
import com.hybrid87sg.bindertracker.ui.components.loadCoverBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Change an existing binder's name, spine colour and size.
 *
 * It's the same form as "New binder", filled in with the binder's current
 * choices. [filledPositions] lists the pocket numbers that hold a card
 * (across every page), so we can warn before a smaller size moves cards.
 */
@Composable
fun EditBinderScreen(
    binder: Binder,
    pageCount: Int,
    filledPositions: List<Int>,
    onCancel: () -> Unit,
    onSave: (name: String, colorHex: String, size: BinderSize, collecting: List<String>, countSecretRares: Boolean) -> Unit,
    // Every expansion, for the "Collecting" section.
    expansions: List<ExpansionOption> = emptyList(),
    // This binder's own cover picture (kept on this phone), or null.
    cover: CoverPicture? = null,
    // The shelf's angle and side, so the cover preview turns the same way.
    look: ShelfLook = ShelfLook(),
    // Copies a picked photo to a temporary file; null if it couldn't be read.
    importCover: suspend (Uri) -> String? = { null },
    // Saves the cover picture (null path = remove it).
    onSaveCover: (path: String?, placement: CoverPlacement) -> Unit = { _, _ -> },
    // Throws away a picked photo that wasn't saved.
    discardCoverDraft: (String?) -> Unit = {},
) {
    // The cover picture being edited: nothing is kept until "Save changes".
    var coverPath by rememberSaveable { mutableStateOf(cover?.path) }
    var coverX by rememberSaveable { mutableStateOf(cover?.placement?.x ?: 0.5f) }
    var coverY by rememberSaveable { mutableStateOf(cover?.placement?.y ?: 0.5f) }
    var coverZoom by rememberSaveable { mutableStateOf(cover?.placement?.zoom ?: 1f) }
    var coverChanged by rememberSaveable { mutableStateOf(false) }
    var adjusting by rememberSaveable { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picture by produceState<ImageBitmap?>(null, coverPath) {
        value = coverPath?.let { p -> withContext(Dispatchers.IO) { loadCoverBitmap(p) } }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            reading = true
            scope.launch {
                val path = importCover(uri)
                reading = false
                if (path != null) {
                    if (coverPath != cover?.path) discardCoverDraft(coverPath)
                    coverPath = path
                    coverX = 0.5f; coverY = 0.5f; coverZoom = 1f
                    coverChanged = true
                    adjusting = true
                }
            }
        }
    }
    val leave = {
        if (coverPath != cover?.path) discardCoverDraft(coverPath)
        onCancel()
    }
    var collecting by rememberSaveable { mutableStateOf(binder.followedSetIds) }
    var secret by rememberSaveable { mutableStateOf(binder.countSecretRares) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    BinderForm(
        title = "Edit binder",
        initialName = binder.name,
        initialColorHex = binder.colorHex,
        initialSize = BinderSize.of(binder.columns, binder.rows),
        saveText = "Save changes",
        onCancel = leave,
        onSave = { name, colorHex, size ->
            if (coverChanged) onSaveCover(coverPath, CoverPlacement(coverX, coverY, coverZoom))
            onSave(name, colorHex, size, collecting, secret)
        },
        sizeNote = { size -> SizeChangeNote(binder, size, pageCount, filledPositions) },
        extra = {
            CoverSection(
                binder = binder,
                look = look,
                picture = picture,
                placement = CoverPlacement(coverX, coverY, coverZoom),
                hasPicture = coverPath != null,
                reading = reading,
                onChoose = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onAdjust = { adjusting = true },
                onRemove = {
                    if (coverPath != cover?.path) discardCoverDraft(coverPath)
                    coverPath = null
                    coverChanged = true
                },
            )
            CollectingSection(
                names = collecting.map { id -> expansions.firstOrNull { it.id == id }?.name ?: id },
                ids = collecting,
                secret = secret,
                onRemove = { id -> collecting = collecting - id },
                onAdd = { choosing = true },
                onSecret = { secret = it },
            )
        },
    )
    val shown = picture
    if (adjusting && shown != null) {
        CoverAdjustSheet(
            picture = shown,
            color = colorFromHex(binder.colorHex),
            name = binder.name,
            initial = CoverPlacement(coverX, coverY, coverZoom),
            onDismiss = { adjusting = false },
            onDone = { p ->
                coverX = p.x; coverY = p.y; coverZoom = p.zoom
                coverChanged = true
                adjusting = false
            },
        )
    }
    if (choosing) {
        CollectingSheet(
            expansions = expansions,
            selected = collecting,
            onDismiss = { choosing = false },
            onDone = { collecting = it; choosing = false },
        )
    }
}

/** "Collecting": the expansions this binder follows, for the Missing cards page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectingSection(
    names: List<String>,
    ids: List<String>,
    secret: Boolean,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    onSecret: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(AppColors.Surface).holoTopStripe().border(1.dp, AppColors.Border, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text("Collecting", style = MaterialTheme.typography.titleMedium)
            Text(
                "The Missing cards page lists the cards from these expansions that aren't in the binder yet.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Muted,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ids.zip(names).forEach { (id, name) ->
                Surface(
                    onClick = { onRemove(id) },
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.Accent,
                    modifier = Modifier.semantics { contentDescription = "$name, tap to remove" },
                ) {
                    Text("$name  ×", color = AppColors.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                }
            }
            Surface(
                onClick = onAdd,
                shape = RoundedCornerShape(12.dp),
                color = AppColors.Surface,
                border = BorderStroke(1.dp, AppColors.BorderStrong),
            ) {
                Text("+ Add expansion", color = AppColors.AccentText, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Count secret rares", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("Master set: cards numbered past the set size. Just on this phone.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
            Switch(checked = secret, onCheckedChange = onSecret)
        }
    }
}

/** Explains what a new size will do to the pages and cards already in the binder. */
@Composable
private fun SizeChangeNote(binder: Binder, size: BinderSize, pageCount: Int, filledPositions: List<Int>) {
    if (size.pockets == binder.pocketsPerPage || pageCount == 0) return
    val moving = filledPositions.count { it >= size.pockets }
    val lines = buildList {
        if (size.pockets > binder.pocketsPerPage) {
            add("Each page gets ${size.pockets - binder.pocketsPerPage} more empty pockets. No cards move.")
        } else if (moving == 0) {
            add("The pockets that go away are empty, so no cards move.")
        } else {
            val pages = BinderResize.pagesNeeded(moving, size.pockets)
            add(
                "${plural(moving, "card")} won't fit the smaller pages. They'll move, in order, " +
                    "onto ${plural(pages, "new page")} at the back. Every other card stays where it is.",
            )
        }
    }
    Text(
        lines.joinToString("\n\n"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    )
}

private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

/** "Cover picture": your own photo on the binder's cover, with a turned preview. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverSection(
    binder: Binder,
    look: ShelfLook,
    picture: ImageBitmap?,
    placement: CoverPlacement,
    hasPicture: Boolean,
    reading: Boolean,
    onChoose: () -> Unit,
    onAdjust: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(AppColors.Surface).holoTopStripe().border(1.dp, AppColors.Border, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text("Cover picture", style = MaterialTheme.typography.titleMedium)
            Text(
                "Put your own picture on the binder's cover. The spine keeps its colour and label. " +
                    "It shows when your shelf's binders are turned (Edit shelf), and stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Muted,
            )
        }
        // Always turned (at least 30°) here, so you can see the cover.
        val previewLook = ShelfLook(angle = maxOf(look.angle, 30), coversOnRight = look.coversOnRight)
        ShelfPreview(
            listOf(PreviewBinder(binder.name, binder.sizeLabel, binder.colorHex, fill = null, picture = picture, placement = placement)),
            previewLook,
            perRow = 1,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onChoose,
                enabled = !reading,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.White),
            ) { Text(if (reading) "Opening…" else if (hasPicture) "Change picture" else "Choose picture") }
            if (hasPicture) {
                OutlinedButton(onClick = onAdjust, enabled = picture != null) { Text("Adjust", color = AppColors.Ink) }
                OutlinedButton(onClick = onRemove) { Text("Remove", color = AppColors.Ink) }
            }
        }
    }
}

/**
 * Adjust picture: drag the picture to move it on the cover, and pinch or use
 * the slider to zoom. The stitching, strap and crease are drawn on top so you
 * can see what they'll cover. The picture always fills the cover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverAdjustSheet(
    picture: ImageBitmap,
    color: androidx.compose.ui.graphics.Color,
    name: String,
    initial: CoverPlacement,
    onDismiss: () -> Unit,
    onDone: (CoverPlacement) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var place by remember { mutableStateOf(initial) }
    val measurer = rememberTextMeasurer()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppColors.Surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text("Adjust picture", style = MaterialTheme.typography.titleLarge)
                Text("Drag the picture to move it. Pinch or use the slider to zoom.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
            val frameH = 280.dp
            Canvas(
                Modifier
                    .size(frameH * BinderTurn.COVER_RATIO, frameH)
                    .semantics { contentDescription = "Cover picture. Drag to move, pinch to zoom." }
                    .pointerInput(picture) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val frame = Size(size.width.toFloat(), size.height.toFloat())
                            val z = (place.zoom * zoom).coerceIn(1f, CoverPlacement.MAX_ZOOM)
                            val (dw, dh) = coverPictureSize(picture, z, frame)
                            val overX = dw - frame.width
                            val overY = dh - frame.height
                            place = CoverPlacement(
                                x = if (overX > 0.5f) place.x - pan.x / overX else place.x,
                                y = if (overY > 0.5f) place.y - pan.y / overY else place.y,
                                zoom = z,
                            ).clamped()
                        }
                    },
            ) {
                drawCover(size, CoverLook(color, name, picture, place), hingeOnLeft = true, measurer = measurer)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("−", fontSize = 20.sp, color = AppColors.Muted)
                Slider(
                    value = place.zoom,
                    onValueChange = { place = place.copy(zoom = it).clamped() },
                    valueRange = 1f..CoverPlacement.MAX_ZOOM,
                    colors = SliderDefaults.colors(thumbColor = AppColors.Accent, activeTrackColor = AppColors.Accent, inactiveTrackColor = AppColors.Border),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Zoom" },
                )
                Text("+", fontSize = 20.sp, color = AppColors.Muted)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { place = CoverPlacement() }, modifier = Modifier.weight(1f)) { Text("Reset", color = AppColors.Ink) }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", color = AppColors.Ink) }
                Button(
                    onClick = { onDone(place) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.White),
                ) { Text("Done") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
