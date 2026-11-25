package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.data.BinderMissing
import com.hybrid87sg.bindertracker.data.Checklist
import com.hybrid87sg.bindertracker.ui.components.Eyebrow
import com.hybrid87sg.bindertracker.ui.components.holoBrush
import com.hybrid87sg.bindertracker.ui.components.openingMoment
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.appBackground

/**
 * The Missing tab: the Missing cards lists of every collecting binder in
 * one place. Chips at the top narrow it to one binder. Tap a card to see it
 * (and put it in); tap "Open list" for that binder's full Missing cards page.
 */
@Composable
fun MissingTabScreen(
    binders: List<BinderMissing>,
    onOpenCard: (binder: BinderMissing, index: Int) -> Unit,
    onOpenList: (binderId: String) -> Unit,
) {
    // Which binder the chips show: null = all of them. Remembered by ID so it survives rotating.
    var onlyId by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = binders.filter { onlyId == null || it.binder.id == onlyId }
    val config = LocalConfiguration.current
    val unfolded = config.screenWidthDp >= 600 && config.screenHeightDp >= 480
    val grid = rememberLazyGridState()
    val opening = openingMoment()

    Column(Modifier.fillMaxSize().appBackground().statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Eyebrow("Still to find")
            Text("Missing", style = MaterialTheme.typography.headlineLarge)
        }
        if (binders.isEmpty()) {
            Text(
                "None of your binders is collecting an expansion yet. Open a binder, tap Missing cards and choose the expansion it collects. Its missing cards then show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            return@Column
        }
        if (binders.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Chip("All binders", selected = onlyId == null) { onlyId = null } }
                items(binders, key = { it.binder.id }) { b ->
                    Chip(b.binder.name, selected = onlyId == b.binder.id) { onlyId = b.binder.id }
                }
            }
        }
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Fixed(if (unfolded) 8 else 4),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp + com.hybrid87sg.bindertracker.ui.components.LocalTabBarClearance.current),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            shown.forEach { b ->
                item(key = "head-${b.binder.id}", span = { GridItemSpan(maxLineSpan) }) {
                    BinderHeading(b) { onOpenList(b.binder.id) }
                }
                if (b.entries.isEmpty()) {
                    item(key = "done-${b.binder.id}", span = { GridItemSpan(maxLineSpan) }) {
                        Text("Complete! Nothing missing.", fontWeight = FontWeight.SemiBold, color = AppColors.SuccessText)
                    }
                }
                items(b.entries, key = { "${b.binder.id}/${it.card.id}" }, contentType = { "card" }) { e: Checklist.Entry ->
                    Tile(e, pop = opening) { onOpenCard(b, b.entries.indexOf(e)) }
                }
            }
        }
    }
}

@Composable
private fun BinderHeading(b: BinderMissing, onOpenList: () -> Unit) {
    val have = b.progress.total - b.progress.missing
    // A big rounded card: binder name, percent done, a foil progress bar. Tap anywhere for the full list.
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = AppColors.Shadow, spotColor = AppColors.Shadow)
            .clip(RoundedCornerShape(24.dp)).background(AppColors.Surface)
            .clickable(onClickLabel = "Open the full list", onClick = onOpenList)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(com.hybrid87sg.bindertracker.ui.components.colorFromHex(b.binder.colorHex)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.binder.name, style = MaterialTheme.typography.titleLarge, color = AppColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$have of ${b.progress.total} · ${b.progress.missing} to go", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
            Text(
                "${(b.progress.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.Ink,
            )
            Spacer(Modifier.width(4.dp))
            com.hybrid87sg.bindertracker.ui.components.SmallIcon(com.hybrid87sg.bindertracker.R.drawable.ic_chevron_right, AppColors.Muted)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(AppColors.Surface2)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(b.progress.fraction.coerceIn(0f, 1f)).clip(RoundedCornerShape(4.dp)).drawBehind { drawRect(holoBrush(size.width.coerceAtLeast(1f))) })
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (selected) AppColors.Paper else AppColors.Ink,
        maxLines = 1,
        modifier = Modifier.clip(shape)
            .background(if (selected) AppColors.Ink else AppColors.Group)
            .clickable(onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
