package com.hybrid87sg.bindertracker.ui.screens

import com.hybrid87sg.bindertracker.ui.theme.appBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import com.hybrid87sg.bindertracker.ui.components.HoloBar
import com.hybrid87sg.bindertracker.ui.components.arrive
import com.hybrid87sg.bindertracker.ui.components.pressSquish
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.ChecklistProgress
import com.hybrid87sg.bindertracker.ui.components.holoBrush
import com.hybrid87sg.bindertracker.data.PageSummary
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Surface
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.BottomBar
import com.hybrid87sg.bindertracker.ui.components.PrimaryButton
import com.hybrid87sg.bindertracker.ui.components.colorFromHex
import com.hybrid87sg.bindertracker.ui.theme.AppColors

@Composable
fun BinderScreen(
    binder: Binder,
    pages: List<PageSummary>,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    // Adds this many empty pages to the end of the binder.
    onAddPages: (count: Int) -> Unit,
    onEdit: () -> Unit,
    onOpenPage: (PageSummary) -> Unit,
    // Opens the Share screen (your own binders only).
    onShare: () -> Unit = {},
    // Your cover picture, shown as a mini cover next to the name. Null = plain colour.
    coverPicture: androidx.compose.ui.graphics.ImageBitmap? = null,
    // Missing cards: progress if the binder collects an expansion, else null.
    progress: ChecklistProgress? = null,
    // The names of the expansions it collects, e.g. "Base Set + 151".
    collectingLabel: String = "",
    onOpenMissing: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var addingPages by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().appBackground().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton("Shelf", onBack)
            Spacer(Modifier.weight(1f))
            // Only the owner can share or delete a binder. Editors can rename,
            // recolour, resize and add pages like the owner; viewers can only look.
            if (binder.isOwner) {
                IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = "Share binder ${binder.name}", tint = AppColors.Ink)
                }
            }
            if (binder.canEdit) {
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit binder ${binder.name}", tint = AppColors.Ink)
                }
                IconButton(onClick = { addingPages = true }, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_plus), contentDescription = "Add pages", tint = AppColors.Ink)
                }
            }
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(48.dp)) {
                Icon(
                    painterResource(R.drawable.ic_trash),
                    contentDescription = if (binder.isOwner) "Delete binder ${binder.name}" else "Leave binder ${binder.name}",
                    tint = AppColors.Ink,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (coverPicture != null) {
                // A tiny cover: your picture with a darker strip down the hinge side.
                Box(
                    Modifier.width(48.dp).height(62.dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 7.dp, bottomEnd = 7.dp))
                        .background(colorFromHex(binder.colorHex))
                        .semantics { contentDescription = "Cover picture" },
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = coverPicture,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(Modifier.matchParentSize().drawBehind {
                        drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.28f), size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height))
                    })
                }
            } else {
                Box(
                    Modifier.width(34.dp).height(56.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(colorFromHex(binder.colorHex)),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(binder.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${binder.sizeLabel} pockets · ${pages.size} ${if (pages.size == 1) "page" else "pages"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                )
                sharedLabel(binder)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.AccentText, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The way into the Missing cards page. Hidden from people who can't
            // choose an expansion when the binder doesn't collect one yet.
            if (progress != null || binder.canEdit) {
                item(key = "missing") { MissingStrip(progress, collectingLabel, onOpenMissing) }
            }
            if (pages.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().dashedOutline().padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("No pages yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (binder.canEdit) {
                                "Tap + at the top to add pages. Each page has ${binder.pocketsPerPage} pockets; tap a pocket to scan its card."
                            } else {
                                "Pages appear here when ${binder.ownerName ?: "the owner"} adds them."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                // The pages pop in one after another when the binder opens.
                val inT = arrive(play = index < 12, delayMillis = 60 + index * 45, durationMillis = 340)
                val press = remember { MutableInteractionSource() }
                Surface(
                    onClick = { onOpenPage(page) },
                    interactionSource = press,
                    shape = RoundedCornerShape(16.dp),
                    color = AppColors.Surface,
                    border = BorderStroke(1.dp, AppColors.Border),
                    modifier = Modifier.fillMaxWidth()
                        .graphicsLayer {
                            alpha = inT
                            translationY = (1f - inT) * 14.dp.toPx()
                            val s = 0.94f + 0.06f * inT
                            scaleX = s; scaleY = s
                        }
                        .pressSquish(press, 0.97f),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Page ${index + 1}", style = MaterialTheme.typography.titleMedium)
                        val toCheck = binder.pocketsPerPage - page.identified - page.empty
                        Text(
                            buildList {
                                add("${page.identified} ${if (page.identified == 1) "card" else "cards"}")
                                if (page.empty > 0) add("${page.empty} empty")
                                if (toCheck > 0) add("$toCheck to check")
                            }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Muted,
                        )
                        Spacer(Modifier.height(6.dp))
                        HoloBar(page.identified.toFloat() / binder.pocketsPerPage.coerceAtLeast(1))
                    }
                }
            }
        }

        if (pages.isEmpty() && binder.canEdit) {
            BottomBar {
                PrimaryButton(text = "Add pages", onClick = { addingPages = true }, icon = R.drawable.ic_plus)
            }
        } else {
            // Keep the list clear of the phone's own navigation bar.
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (addingPages) {
        AddPagesDialog(
            onDismiss = { addingPages = false },
            onAdd = { count -> addingPages = false; onAddPages(count) },
        )
    }

    if (confirmDelete) {
        DeleteBinderDialog(binder, onDismiss = { confirmDelete = false }, onConfirm = { confirmDelete = false; onDelete() })
    }
}

/** "Delete this binder?" for the owner, "Leave this binder?" for someone it's shared with. */
@Composable
internal fun DeleteBinderDialog(binder: Binder, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (binder.isOwner) "Delete “${binder.name}”?" else "Leave “${binder.name}”?") },
        text = {
            Text(
                when {
                    !binder.isOwner -> "This takes the binder off your shelf. It stays on ${binder.ownerName ?: "the owner"}'s shelf, and they can invite you again."
                    binder.memberCount > 1 -> "This removes the binder from your shelf, and from the shelf of everyone you shared it with. You can't undo this."
                    else -> "This removes the binder from your shelf. You can't undo this."
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger),
            ) { Text(if (binder.isOwner) "Delete binder" else "Leave binder") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep binder") }
        },
    )
}

/** "38 cards missing · 64% of Base Set + 151", or an invitation to start tracking. */
@Composable
internal fun MissingStrip(progress: ChecklistProgress?, collectingLabel: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = press,
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Border),
        modifier = Modifier.fillMaxWidth().pressSquish(press, 0.97f),
    ) {
        Row(
            Modifier.drawBehind { drawRect(holoBrush(size.height), size = Size(4.dp.toPx(), size.height)) }
                .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    when {
                        progress == null -> "Track missing cards"
                        progress.total == 0 -> "Missing cards"
                        progress.missing == 0 -> "Complete! Every card collected"
                        progress.missing == 1 -> "1 card missing"
                        else -> "${progress.missing} cards missing"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (progress == null) "Pick an expansion this binder collects" else "${(progress.fraction * 100).toInt()}% of $collectingLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null && progress.total > 0) {
                    Spacer(Modifier.height(4.dp))
                    HoloBar(progress.fraction)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Asks how many empty pages to add, from 1 to [MAX_NEW_PAGES]. */
@Composable
internal fun AddPagesDialog(onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var count by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add pages") },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { count-- }, enabled = count > 1, modifier = Modifier.size(56.dp)) {
                    Text("−", style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    "$count",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(64.dp),
                )
                TextButton(onClick = { count++ }, enabled = count < MAX_NEW_PAGES, modifier = Modifier.size(56.dp)) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(count) }) { Text(if (count == 1) "Add 1 page" else "Add $count pages") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private const val MAX_NEW_PAGES = 50

/** "Shared by Alex · You can edit", "Shared with 2 people", or null when not shared. */
internal fun sharedLabel(binder: Binder): String? = when {
    binder.role == Binder.ROLE_EDITOR -> "Shared by ${binder.ownerName ?: "a friend"} · You can edit"
    binder.role == Binder.ROLE_VIEWER -> "Shared by ${binder.ownerName ?: "a friend"} · View only"
    binder.memberCount > 1 -> "Shared with ${binder.memberCount - 1} ${if (binder.memberCount == 2) "person" else "people"}"
    else -> null
}

private fun Modifier.dashedOutline(): Modifier = drawBehind {
    val stroke = 2.dp.toPx()
    drawRoundRect(
        color = AppColors.BorderStrong,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(14.dp.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
}
