package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.background
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
    onSave: (name: String, colorHex: String, size: BinderSize) -> Unit,
) {
    BinderForm(
        title = "Edit binder",
        initialName = binder.name,
        initialColorHex = binder.colorHex,
        initialSize = BinderSize.of(binder.columns, binder.rows),
        saveText = "Save changes",
        onCancel = onCancel,
        onSave = onSave,
        sizeNote = { size -> SizeChangeNote(binder, size, pageCount, filledPositions) },
    )
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
