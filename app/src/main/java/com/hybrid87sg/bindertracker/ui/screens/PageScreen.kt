package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.GuideRect
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.BottomBar
import com.hybrid87sg.bindertracker.ui.components.CARD_ASPECT
import com.hybrid87sg.bindertracker.ui.components.CardImage
import com.hybrid87sg.bindertracker.ui.components.PrimaryButton
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import java.net.URLDecoder

private val CheckBorder = Color(0xFFB7791F)
private val CheckFill = Color(0xFFFBE3A6)

/**
 * One binder page: the photo cut into pockets. Tap a pocket to say which
 * card it holds, or that it's empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
    binderName: String,
    pageNumber: Int,
    columns: Int,
    rows: Int,
    pockets: List<PocketWithCard>,
    photo: ImageBitmap?,
    guide: GuideRect,
    // What automatic card reading found, e.g. "Recognised 6 of 9 cards", or null.
    readingStatus: String? = null,
    onBack: () -> Unit,
    onChooseCard: (position: Int) -> Unit,
    onViewCard: (cardId: String) -> Unit,
    onMarkEmpty: (position: Int) -> Unit,
    onClear: (position: Int) -> Unit,
    onDeletePage: () -> Unit,
    // Take a new photo of this page; pockets update with any new cards read.
    onRescan: () -> Unit = {},
    // Photograph just this pocket's card and fill the pocket with it.
    onScanCard: (position: Int) -> Unit = {},
) {
    var selected by remember { mutableStateOf<PocketWithCard?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val identified = pockets.count { it.cardId != null }
    val empty = pockets.count { it.isEmpty }
    val toDo = pockets.size - identified - empty

    Column(Modifier.fillMaxSize().background(AppColors.Paper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(binderName, onBack)
            Spacer(Modifier.weight(1f))
            // Rescanning and deleting the page live behind Edit.
            TextButton(onClick = { editing = true }, modifier = Modifier.height(48.dp)) {
                Text("Edit", style = MaterialTheme.typography.labelLarge, color = AppColors.Ink)
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Page $pageNumber", style = MaterialTheme.typography.headlineMedium)
            Text(
                buildList {
                    add("$identified of ${pockets.size} identified")
                    if (empty > 0) add("$empty empty")
                    if (toDo > 0) add("$toDo to check")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Muted,
            )
            // Automatic card reading: "Reading the cards…", then what it found.
            readingStatus?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.Accent, fontWeight = FontWeight.Medium)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.White)
                    .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pockets.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { pocket ->
                            PocketTile(
                                pocket = pocket,
                                onClick = { selected = pocket },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keep tiles the same width on a short last row.
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text(
                "Tap a pocket to choose its card, or to change one the camera recognised.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Muted,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            )
        }

        BottomBar {
            PrimaryButton(text = "Done", onClick = onBack)
        }
    }

    if (editing) {
        ModalBottomSheet(onDismissRequest = { editing = false }, containerColor = AppColors.Paper) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Edit page $pageNumber", style = MaterialTheme.typography.titleLarge)
                SheetButton("Rescan page") { editing = false; onRescan() }
                Text(
                    "Take a new photo of this page. Pockets update to the cards the camera recognises now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                SheetButton("Delete page", color = AppColors.Danger) { editing = false; confirmDelete = true }
            }
        }
    }

    selected?.let { pocket ->
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = AppColors.Paper) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pocket.cardName?.let { "Pocket ${pocket.position + 1}: $it" } ?: "Pocket ${pocket.position + 1}",
                    style = MaterialTheme.typography.titleLarge,
                )
                SheetButton("Scan this card") { selected = null; onScanCard(pocket.position) }
                SheetButton(if (pocket.cardId == null) "Choose card from list" else "Change card") { selected = null; onChooseCard(pocket.position) }
                pocket.cardId?.let { id -> SheetButton("View card and price") { selected = null; onViewCard(id) } }
                if (!pocket.isEmpty) SheetButton("Mark as empty pocket") { selected = null; onMarkEmpty(pocket.position) }
                if (pocket.cardId != null || pocket.isEmpty) SheetButton("Clear pocket") { selected = null; onClear(pocket.position) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete page $pageNumber?") },
            text = { Text("This removes the page and its photo from $binderName. You can't undo this.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDeletePage() },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger),
                ) { Text("Delete page") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep page") } },
        )
    }
}

@Composable
private fun SheetButton(label: String, color: Color = AppColors.Ink, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = AppColors.White,
        border = BorderStroke(1.dp, AppColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
    }
}

@Composable
private fun PocketTile(
    pocket: PocketWithCard,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val label = when {
        pocket.cardName != null -> pocket.cardName
        pocket.isEmpty -> "Empty pocket"
        else -> "Tap to identify"
    }
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val shape = RoundedCornerShape(6.dp)
            Box(
                Modifier.fillMaxWidth().aspectRatio(CARD_ASPECT).clip(shape)
                    .then(
                        when {
                            pocket.isEmpty -> Modifier.dashed(AppColors.BorderStrong)
                            pocket.cardId == null -> Modifier.border(2.dp, CheckBorder, shape)
                            else -> Modifier.border(1.dp, AppColors.Border, shape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    pocket.isEmpty -> Text("Empty", fontSize = 13.sp, color = AppColors.Muted, textAlign = TextAlign.Center)
                    // Identified: the official card picture from TCGdex, not the photo.
                    pocket.cardId != null -> CardImage(pocket.imageUrl?.let { "$it/low.webp" }, contentDescription = pocket.cardName, modifier = Modifier.fillMaxSize())
                    else -> Text("?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CheckBorder)
                }
            }
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (pocket.cardId == null && !pocket.isEmpty) AppColors.Ink else AppColors.Ink,
                modifier = if (pocket.cardId == null && !pocket.isEmpty) {
                    Modifier.clip(RoundedCornerShape(6.dp)).background(CheckFill).padding(horizontal = 6.dp, vertical = 2.dp)
                } else Modifier,
            )
            pocket.cardNumber?.let {
                val number = runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
                Text("#$number · ${pocket.setName.orEmpty()}", fontSize = 12.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun Modifier.dashed(color: Color): Modifier = drawBehind {
    val stroke = 2.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
    )
}
