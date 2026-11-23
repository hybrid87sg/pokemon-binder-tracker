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
) {
    var collecting by rememberSaveable { mutableStateOf(binder.followedSetIds) }
    var secret by rememberSaveable { mutableStateOf(binder.countSecretRares) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    BinderForm(
        title = "Edit binder",
        initialName = binder.name,
        initialColorHex = binder.colorHex,
        initialSize = BinderSize.of(binder.columns, binder.rows),
        saveText = "Save changes",
        onCancel = onCancel,
        onSave = { name, colorHex, size -> onSave(name, colorHex, size, collecting, secret) },
        sizeNote = { size -> SizeChangeNote(binder, size, pageCount, filledPositions) },
        extra = {
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
