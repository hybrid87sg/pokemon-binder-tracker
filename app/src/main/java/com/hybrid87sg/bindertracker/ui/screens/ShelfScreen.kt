package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.ui.components.ShelfPlank
import com.hybrid87sg.bindertracker.ui.components.SpineArt
import com.hybrid87sg.bindertracker.ui.components.colorFromHex
import com.hybrid87sg.bindertracker.ui.components.verticalText
import com.hybrid87sg.bindertracker.ui.theme.AppColors

private const val SPINES_PER_ROW = 4
private val SpineHeight = 280.dp

/** One slot on a shelf row: either a binder, or the "Add binder" button. */
private sealed interface ShelfItem {
    data class BinderItem(val binder: Binder) : ShelfItem
    data object AddItem : ShelfItem
}

@Composable
fun ShelfScreen(
    binders: List<Binder>,
    onOpenBinder: (Binder) -> Unit,
    onAddBinder: () -> Unit,
    onOpenAccount: () -> Unit,
    onSearch: () -> Unit,
) {
    // All binders, then the "Add binder" slot, cut into rows of 4.
    val items: List<ShelfItem> = binders.map { ShelfItem.BinderItem(it) } + ShelfItem.AddItem
    val rows = items.chunked(SPINES_PER_ROW)

    Column(Modifier.fillMaxSize().background(AppColors.ShelfBackground).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("My binders", style = MaterialTheme.typography.headlineLarge)
                Text(
                    when (binders.size) {
                        0 -> "No binders yet · add your first one"
                        1 -> "1 binder · tap a spine to open"
                        else -> "${binders.size} binders · tap a spine to open"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                )
            }
            Surface(
                onClick = onSearch,
                shape = RoundedCornerShape(14.dp),
                color = AppColors.White,
                contentColor = AppColors.Ink,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderStrong),
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Search cards" },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = onOpenAccount,
                shape = CircleShape,
                color = AppColors.Accent,
                contentColor = AppColors.White,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Account" },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_person), contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
        }

        // LazyColumn only builds the rows that are on screen, so a shelf
        // with many binders still scrolls smoothly.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items(rows) { row ->
                Column {
                    Row(
                        Modifier.fillMaxWidth().height(SpineHeight).padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        row.forEach { item ->
                            when (item) {
                                is ShelfItem.BinderItem -> BinderSpine(item.binder) { onOpenBinder(item.binder) }
                                ShelfItem.AddItem -> AddBinderSpine(onAddBinder)
                            }
                        }
                    }
                    ShelfPlank()
                }
            }
        }
    }
}

@Composable
private fun BinderSpine(binder: Binder, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp),
        color = colorFromHex(binder.colorHex),
        modifier = Modifier.semantics { contentDescription = "Open binder ${binder.name}, ${binder.sizeLabel}" },
    ) {
        SpineArt(
            name = binder.name,
            tag = binder.sizeLabel,
            color = colorFromHex(binder.colorHex),
            width = 64.dp,
            height = SpineHeight,
        )
    }
}

@Composable
private fun AddBinderSpine(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        // Transparent, so the dashed outline drawn underneath shows through.
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = Color4A,
        modifier = Modifier
            .width(64.dp)
            .height(SpineHeight)
            .dashedBorder()
            .semantics { contentDescription = "Add binder" },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(14.dp))
            Text("Add binder", modifier = Modifier.verticalText(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private val Color4A = androidx.compose.ui.graphics.Color(0xFF4A4843)

/** Draws a dashed outline, like the empty slot in the prototype. */
private fun Modifier.dashedBorder(): Modifier = drawBehind {
    val stroke = 2.dp.toPx()
    drawRoundRect(
        color = AppColors.DashedBorder,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
}
