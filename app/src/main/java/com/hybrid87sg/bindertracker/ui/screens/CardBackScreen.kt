package com.hybrid87sg.bindertracker.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.data.CardBackChoice
import com.hybrid87sg.bindertracker.data.CardBackStyle
import com.hybrid87sg.bindertracker.data.CoverPlacement
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.CardBack
import com.hybrid87sg.bindertracker.ui.components.CardBackArt
import com.hybrid87sg.bindertracker.ui.components.CardBackSetting
import com.hybrid87sg.bindertracker.ui.components.Eyebrow
import com.hybrid87sg.bindertracker.ui.components.LocalBinderColor
import com.hybrid87sg.bindertracker.ui.components.LocalCardBack
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.appBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** One binder in the preview: its name and colour. */
data class CardBackBinder(val name: String, val color: Color)

/**
 * Me > Card back: the back every face-down card shows (empty pockets, a
 * spinning card, the page inside a 3D binder). Orbit, Prism and Lattice take
 * on each binder's colour; your own picture stays on this phone.
 */
@Composable
fun CardBackScreen(
    choice: CardBackChoice,
    binders: List<CardBackBinder>,
    onBack: () -> Unit,
    onPick: (CardBackStyle) -> Unit,
    importPicture: suspend (Uri) -> String?,
    discardDraft: (String?) -> Unit,
    onSavePicture: (path: String, placement: CoverPlacement) -> Unit,
    onRemovePicture: () -> Unit,
) {
    val saved = LocalCardBack.current
    // A picture being placed (just picked, or the saved one being adjusted).
    var draftPath by remember { mutableStateOf<String?>(null) }
    var draftPlacement by remember { mutableStateOf(CoverPlacement()) }
    var reading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val draftPicture by produceState<Bitmap?>(null, draftPath) {
        value = draftPath?.let { p -> withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(p) }.getOrNull() } }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            reading = true
            scope.launch {
                val path = importPicture(uri)
                reading = false
                if (path != null) {
                    draftPath = path
                    draftPlacement = CoverPlacement()
                }
            }
        }
    }
    val choose = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val previewColor = binders.firstOrNull()?.color ?: AppColors.Accent

    Column(Modifier.fillMaxSize().appBackground().statusBarsPadding()) {
        Box(Modifier.padding(start = 8.dp, top = 12.dp)) { BackButton("Me", onBack) }
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("Appearance")
                Text("Card back", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "The back of every card: empty pockets, a card spinning open and the page inside a 3D binder. " +
                        "Orbit, Prism and Lattice take on each binder's colour. This is set for this phone only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                )
            }
            val shape = RoundedCornerShape(16.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(AppColors.Surface).border(1.dp, AppColors.Border, shape)) {
                CardBackStyle.entries.forEachIndexed { i, style ->
                    val hasPicture = choice.picturePath != null
                    val selected = choice.style == style && (style != CardBackStyle.Own || hasPicture)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(role = Role.RadioButton) {
                                if (style == CardBackStyle.Own && !hasPicture) choose() else onPick(style)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.width(40.dp).aspectRatio(63f / 88f).clip(RoundedCornerShape(4.dp))) {
                            if (style == CardBackStyle.Own && !hasPicture) {
                                Box(Modifier.fillMaxSize().background(AppColors.Accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                    Text("+", color = AppColors.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                BackPreview(style, saved.picture, choice.placement, previewColor)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(style.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                when {
                                    style == CardBackStyle.Own && !hasPicture -> "Choose a picture from your phone. It stays on this phone."
                                    style.followsBinder -> "${style.blurb}. Follows each binder's colour"
                                    else -> style.blurb
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Muted,
                            )
                        }
                        // The round "picked" marker.
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .border(if (selected) 6.dp else 2.dp, if (selected) AppColors.Accent else AppColors.Border, CircleShape),
                        )
                    }
                    if (i < CardBackStyle.entries.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Border))
                }
            }
            if (choice.picturePath != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = choose,
                        enabled = !reading,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.White),
                    ) { Text(if (reading) "Opening…" else "Change picture") }
                    OutlinedButton(onClick = {
                        draftPath = choice.picturePath
                        draftPlacement = choice.placement
                    }) { Text("Adjust", color = AppColors.Ink) }
                    OutlinedButton(onClick = onRemovePicture) { Text("Remove", color = AppColors.Ink) }
                }
            } else if (reading) {
                Text("Opening your picture…", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }

            Text("Preview", style = MaterialTheme.typography.titleMedium)
            val sample = binders.take(3).ifEmpty { listOf(CardBackBinder("Your binder", AppColors.Accent)) }
            sample.forEach { b ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.color).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("${b.name} · empty pockets", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        repeat(3) {
                            CompositionLocalProvider(LocalBinderColor provides b.color) {
                                CardBack(Modifier.weight(1f).aspectRatio(63f / 88f).clip(RoundedCornerShape(4.dp)))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    val draft = draftPicture
    if (draftPath != null && draft != null) {
        CardBackAdjustSheet(
            picture = draft,
            initial = draftPlacement,
            onDismiss = {
                if (draftPath != choice.picturePath) discardDraft(draftPath)
                draftPath = null
            },
            onDone = { placement ->
                draftPath?.let { onSavePicture(it, placement) }
                draftPath = null
            },
        )
    }
}

/** A small card back in one design, for the list. */
@Composable
private fun BackPreview(style: CardBackStyle, picture: Bitmap?, placement: CoverPlacement, binderColor: Color) {
    val choice = CardBackChoice(style = style, placement = placement)
    CompositionLocalProvider(
        LocalCardBack provides CardBackSetting(choice, picture),
        LocalBinderColor provides binderColor,
    ) {
        CardBack(Modifier.fillMaxSize())
    }
}

/**
 * Adjust picture: drag your picture to move it on the card back, and pinch or
 * use the slider to zoom. It always fills the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardBackAdjustSheet(
    picture: Bitmap,
    initial: CoverPlacement,
    onDismiss: () -> Unit,
    onDone: (CoverPlacement) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var place by remember { mutableStateOf(initial) }
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
            Canvas(
                Modifier
                    .size(200.dp, 200.dp * 88f / 63f)
                    .clip(RoundedCornerShape(10.dp))
                    .semantics { contentDescription = "Card back picture. Drag to move, pinch to zoom." }
                    .pointerInput(picture) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val w = size.width.toFloat(); val h = size.height.toFloat()
                            val z = (place.zoom * zoom).coerceIn(1f, CoverPlacement.MAX_ZOOM)
                            val fit = max(w / picture.width, h / picture.height) * z
                            val overX = picture.width * fit - w
                            val overY = picture.height * fit - h
                            place = CoverPlacement(
                                x = if (overX > 0.5f) place.x - pan.x / overX else place.x,
                                y = if (overY > 0.5f) place.y - pan.y / overY else place.y,
                                zoom = z,
                            ).clamped()
                        }
                    },
            ) {
                drawIntoCanvas { CardBackArt.draw(it.nativeCanvas, size.width, size.height, CardBackStyle.Own, 0, picture, place) }
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
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", color = AppColors.Ink) }
                Button(
                    onClick = { onDone(place) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.White),
                ) { Text("Use this back") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
