package com.hybrid87sg.bindertracker.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.Binder
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
    onOpenPage: (PageSummary) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var addingPages by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(AppColors.Paper).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton("Shelf", onBack)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { addingPages = true }, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_plus), contentDescription = "Add pages", tint = AppColors.Ink)
            }
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
                Text(
                    "${binder.sizeLabel} pockets · ${pages.size} ${if (pages.size == 1) "page" else "pages"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                )
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pages.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().dashedOutline().padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("No pages yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap + at the top to add pages. Each page has ${binder.pocketsPerPage} pockets; tap a pocket to scan its card.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                Surface(
                    onClick = { onOpenPage(page) },
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.White,
                    border = BorderStroke(1.dp, AppColors.Border),
                    modifier = Modifier.fillMaxWidth(),
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
                    }
                }
            }
        }

        if (pages.isEmpty()) {
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

/** Asks how many empty pages to add, from 1 to [MAX_NEW_PAGES]. */
@Composable
private fun AddPagesDialog(onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
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
