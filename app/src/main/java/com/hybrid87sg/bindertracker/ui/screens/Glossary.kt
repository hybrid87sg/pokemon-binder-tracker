package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.ChecklistProgress
import com.hybrid87sg.bindertracker.data.PageSummary
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.ui.components.pressSquish
import com.hybrid87sg.bindertracker.ui.theme.AppColors

/**
 * The Glossary: every page of the binder, in a pop-up window over the card page.
 * Tap a page to jump to it. The binder's own buttons (Share, Edit, Add pages,
 * Missing cards, Delete) live here too, now that the page list isn't a screen.
 */
@Composable
fun GlossaryPopup(
    binder: Binder,
    pages: List<PageSummary>,
    // Each page's pockets, for the tiny drawing of the page.
    pockets: Map<String, List<PocketWithCard>>,
    currentPageId: String,
    progress: ChecklistProgress?,
    collectingLabel: String,
    onDismiss: () -> Unit,
    onJump: (pageId: String) -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onAddPages: (count: Int) -> Unit,
    onOpenMissing: () -> Unit,
    onDelete: () -> Unit,
) {
    var addingPages by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // The window pops in: a little small and low, growing into place.
        val pop = remember { Animatable(0f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, tween(260, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))) }

        BoxWithConstraints(
            Modifier.fillMaxSize()
                // Tapping outside the window closes it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AppColors.Paper,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.88f)
                    .graphicsLayer {
                        val t = pop.value
                        alpha = t
                        val s = 0.92f + 0.08f * t
                        scaleX = s; scaleY = s
                        translationY = (1f - t) * 16.dp.toPx()
                    }
                    // Taps inside the window stay inside.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .semantics { contentDescription = "Glossary of ${binder.name}" },
            ) {
                Column {
                    GlossaryHeader(
                        binder = binder,
                        pages = pages,
                        onShare = onShare,
                        onEdit = onEdit,
                        onDismiss = onDismiss,
                    )
                    HorizontalDivider(color = AppColors.Border)
                    val listState = rememberLazyListState(
                        // Start with the page you're on in view.
                        initialFirstVisibleItemIndex = pages.indexOfFirst { it.id == currentPageId }.coerceAtLeast(0),
                    )
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    ) {
                        if (progress != null || binder.isOwner) {
                            item(key = "missing") { MissingStrip(progress, collectingLabel, onOpenMissing) }
                        }
                        itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                            GlossaryRow(
                                number = index + 1,
                                pockets = pockets[page.id].orEmpty(),
                                filled = page.identified,
                                total = binder.pocketsPerPage,
                                columns = binder.columns,
                                here = page.id == currentPageId,
                                onClick = { onJump(page.id) },
                            )
                        }
                        if (binder.canEdit) {
                            item(key = "add") {
                                Surface(
                                    onClick = { addingPages = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = androidx.compose.ui.graphics.Color.Transparent,
                                    border = BorderStroke(1.dp, AppColors.BorderStrong),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = AppColors.AccentText, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add pages", fontWeight = FontWeight.Bold, color = AppColors.AccentText)
                                    }
                                }
                            }
                        }
                        item(key = "delete") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = { confirmDelete = true }) {
                                    Text(
                                        if (binder.isOwner) "Delete binder" else "Leave binder",
                                        color = AppColors.Danger,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
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
}

@Composable
private fun GlossaryHeader(
    binder: Binder,
    pages: List<PageSummary>,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "GLOSSARY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.9.sp,
                    color = AppColors.AccentText,
                )
                Text(
                    binder.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Only the owner can share or change the binder.
            if (binder.isOwner) {
                IconButton(onClick = onShare, modifier = Modifier.size(44.dp)) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = "Share binder ${binder.name}", tint = AppColors.Ink, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(44.dp)) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit binder ${binder.name}", tint = AppColors.Ink, modifier = Modifier.size(22.dp))
                }
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = "Close the Glossary", onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 18.sp, color = AppColors.Muted)
            }
        }
        val cards = pages.sumOf { it.identified }
        val slots = pages.size * binder.pocketsPerPage
        Text(
            buildList {
                add("${pages.size} ${if (pages.size == 1) "page" else "pages"}")
                if (pages.isNotEmpty()) add("$cards of $slots cards")
                sharedLabel(binder)?.let { add(it) }
            }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One page in the Glossary: a tiny drawing of it, its number and how full it is. */
@Composable
private fun GlossaryRow(
    number: Int,
    pockets: List<PocketWithCard>,
    filled: Int,
    total: Int,
    columns: Int,
    here: Boolean,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = press,
        shape = RoundedCornerShape(12.dp),
        color = if (here) AppColors.Surface2 else AppColors.Surface,
        border = BorderStroke(if (here) 2.dp else 1.dp, if (here) AppColors.AccentText else AppColors.Border),
        modifier = Modifier.fillMaxWidth().pressSquish(press, 0.97f).semantics {
            contentDescription = "Page $number, $filled of $total cards" + if (here) ", you're here" else ""
        },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(44.dp)) {
                if (pockets.isNotEmpty()) MiniGrid(pockets.sortedBy { it.position }, columns)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Page $number", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                Text("$filled of $total cards", fontSize = 12.sp, color = AppColors.Muted)
            }
            if (here) {
                Text(
                    "You're here",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.AccentText,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }
}

/** The "Glossary" pill next to Tips: four little squares and the word. */
@Composable
internal fun GlossaryButton(onClick: () -> Unit) {
    Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = AppColors.AccentText.copy(alpha = 0.12f),
            modifier = Modifier.semantics { contentDescription = "Glossary: all pages" },
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_glossary), contentDescription = null, tint = AppColors.AccentText, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Glossary", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.AccentText)
            }
        }
    }
}
