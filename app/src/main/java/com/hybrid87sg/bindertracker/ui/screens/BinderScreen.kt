package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.PageWithPockets
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.BottomBar
import com.hybrid87sg.bindertracker.ui.components.PrimaryButton
import com.hybrid87sg.bindertracker.ui.components.colorFromHex
import com.hybrid87sg.bindertracker.ui.theme.AppColors

@Composable
fun BinderScreen(
    binder: Binder,
    pages: List<PageWithPockets>,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onScan: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(AppColors.Paper).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton("Shelf", onBack)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_trash), contentDescription = "Delete binder ${binder.name}", tint = AppColors.Ink)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(34.dp).height(56.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                    .background(colorFromHex(binder.colorHex)),
            )
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(binder.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${binder.sizeLabel} pockets · ${pagesLabel(pages.size)}", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
            }
        }

        if (pages.isEmpty()) {
            Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                Column(
                    Modifier.fillMaxWidth().dashedOutline().padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("No pages yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Scan your first page. The camera reads all ${binder.pocketsPerPage} cards on it at once.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            // A "LazyColumn" only draws the pages currently on screen, so even
            // a binder with 50 pages scrolls smoothly.
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(pages, key = { it.page.id }) { page -> PageCard(page, binder.columns) }
            }
        }

        BottomBar {
            PrimaryButton(text = "Scan a page", onClick = onScan, icon = R.drawable.ic_camera)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${binder.name}”?") },
            text = { Text("This removes the binder from your shelf. You can't undo this.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger),
                ) { Text("Delete binder") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep binder") }
            },
        )
    }
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

private fun pagesLabel(count: Int) = if (count == 1) "1 page" else "$count pages"

/** One saved page, drawn as a small grid of its pockets. */
@Composable
private fun PageCard(page: PageWithPockets, columns: Int) {
    val filled = page.pockets.count { !it.isEmpty }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Page ${page.page.pageNumber} · $filled of ${page.pockets.size} filled",
            style = MaterialTheme.typography.titleMedium,
        )
        page.ordered.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { pocket ->
                    val shape = RoundedCornerShape(8.dp)
                    Column(
                        Modifier.weight(1f).aspectRatio(63f / 88f).clip(shape)
                            .background(if (pocket.isEmpty) AppColors.ShelfBackground else AppColors.White)
                            .border(1.dp, if (pocket.isEmpty) AppColors.Border else AppColors.BorderStrong, shape)
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (pocket.isEmpty) {
                            Text("Empty", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                        } else {
                            Text(
                                pocket.name.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.weight(1f))
                            pocket.number?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, maxLines = 1)
                            }
                        }
                    }
                }
                // Keep the grid even when the last row is short.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
