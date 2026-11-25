package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.Pocket
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.ui.components.CARD_ASPECT
import com.hybrid87sg.bindertracker.ui.components.CardBack
import com.hybrid87sg.bindertracker.ui.theme.AppColors

/*
 * Batch edit on the binder page. Tap Edit, tick several cards (on any page),
 * then mark them all as empty pockets or move them all to other pockets.
 * "?" pockets (not scanned yet) can't be ticked: they're where cards can go.
 * The last 5 changes can be undone; hold the undo button to see them.
 */

/** A pocket on any page of the binder: (page ID, position). */
internal typealias PocketRef = Pair<String, Int>

/** One change in the undo history. [before] is how the changed pockets were. */
internal data class BatchStep(
    val title: String,
    val detail: String,
    val before: List<Pocket>,
    val moved: Boolean,
    val at: Long = System.currentTimeMillis(),
)

/** How a pocket looks while batch editing. */
internal enum class BatchMark {
    /** A card (or empty pocket) you can tick. */
    Pickable,
    /** Ticked: it lifts and its neon glow pulses. */
    Picked,
    /** A "?" pocket, while no cards are ticked: can't be ticked, so it's faded. */
    Free,
    /** A "?" pocket while cards are ticked: tap it to move a card in. */
    Target,
}

/** What's ticked, the pockets picked to move into, and the undo history. Kept while the binder is open. */
internal class BatchEdit {
    val picked = mutableStateListOf<PocketRef>()
    /** "?" pockets tapped as places for the ticked cards, in the order tapped. */
    val slots = mutableStateListOf<PocketRef>()
    val history = mutableStateListOf<BatchStep>()

    fun toggle(ref: PocketRef) {
        if (ref in picked) picked.remove(ref) else picked.add(ref)
        // Which slots you'd picked depended on how many cards were ticked.
        slots.clear()
    }

    fun clear() {
        picked.clear()
        slots.clear()
    }

    fun record(step: BatchStep) {
        history.add(step)
        while (history.size > MAX_UNDO) history.removeAt(0)
    }

    companion object {
        const val MAX_UNDO = 5
    }
}

/** A "?" pocket: no card yet and not marked empty. Cards can be moved into these. */
internal fun PocketWithCard.isFreeSlot() = cardId == null && !isEmpty

/**
 * The bright neon version of the app colour, for ticked cards. It follows the
 * colour the person chose for the app.
 */
internal val BatchNeon: Color
    get() {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(AppColors.Accent.toArgb(), hsv)
        return Color.hsv(hsv[0], 0.68f, 1f)
    }

/** Dark writing on the neon colour (it's always bright). */
internal val OnNeon = Color(0xFF160F3A)

/** The breathing neon glow round a ticked card, 0 to 1 and back (C "Pulse and lift"). */
@Composable
internal fun rememberPickPulse(): Float {
    val t = rememberInfiniteTransition(label = "pickPulse")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pickPulse")
    return v
}

/** Draws the neon glow round a card's edge; [strength] 0 to 1 makes it bigger and brighter. */
internal fun DrawScope.neonGlow(color: Color, corner: Float, strength: Float) {
    val steps = 5
    for (i in steps downTo 1) {
        val spread = (2f + 9f * strength) * i / steps * density
        drawRoundRect(
            color.copy(alpha = 0.10f + 0.10f * strength),
            topLeft = Offset(-spread, -spread),
            size = Size(size.width + spread * 2, size.height + spread * 2),
            cornerRadius = CornerRadius(corner + spread),
        )
    }
    drawRoundRect(color, cornerRadius = CornerRadius(corner), style = Stroke(width = 3f * density))
}

/** The dashed neon outline on a "?" pocket you can move a card into. */
internal fun DrawScope.neonDash(color: Color, corner: Float) {
    drawRoundRect(
        color,
        cornerRadius = CornerRadius(corner),
        style = Stroke(width = 2f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * density, 4f * density))),
    )
}

/** The round tick in the corner of a card while batch editing (filled when ticked). */
@Composable
internal fun BatchTick(on: Boolean, modifier: Modifier = Modifier) {
    val neon = BatchNeon
    Box(
        modifier.size(22.dp)
            .drawBehind { if (on) drawCircle(neon.copy(alpha = 0.45f), radius = size.minDimension * 0.75f) }
            .clip(CircleShape)
            .background(if (on) neon else Color(0x73000000))
            .border(2.dp, if (on) neon else Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = OnNeon, modifier = Modifier.size(13.dp))
    }
}

/** Edit mode's top bar: Select all, how many are ticked, the undo button and Done. */
@Composable
internal fun BatchTopBar(
    status: String,
    allTicked: Boolean,
    onSelectAll: () -> Unit,
    history: List<BatchStep>,
    pageNumberOf: (BatchStep) -> String?,
    onUndo: () -> Unit,
    onUndoTo: (index: Int) -> Unit,
    onDone: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 12.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (allTicked) "Clear" else "Select all",
            style = MaterialTheme.typography.labelLarge,
            color = AppColors.Ink,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onSelectAll).padding(horizontal = 10.dp, vertical = 12.dp),
        )
        Text(
            status,
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        UndoButton(history, pageNumberOf, onUndo, onUndoTo)
        Spacer(Modifier.width(8.dp))
        val neon = BatchNeon
        Surface(onClick = onDone, shape = RoundedCornerShape(50), color = neon, contentColor = OnNeon) {
            Text("Done", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
        }
    }
}

/**
 * Round undo arrow with how many steps are left. Tap: undo the last change.
 * Hold: a little menu of the history; tap a step to go back to before it.
 */
@Composable
private fun UndoButton(
    history: List<BatchStep>,
    pageNumberOf: (BatchStep) -> String?,
    onUndo: () -> Unit,
    onUndoTo: (index: Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val undoNow by androidx.compose.runtime.rememberUpdatedState(onUndo)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val count = history.size
    val enabled = count > 0
    val neon = BatchNeon
    Box {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(AppColors.Ink.copy(alpha = if (enabled) 0.10f else 0.05f))
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onTap = { undoNow() },
                        onLongPress = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            open = true
                        },
                    )
                }
                .semantics {
                    contentDescription = if (enabled) "Undo, $count steps you can undo" else "Nothing to undo"
                    if (enabled) {
                        onClick(label = "Undo") { onUndo(); true }
                        onLongClick(label = "Show undo history") { open = true; true }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_undo),
                contentDescription = null,
                tint = AppColors.Ink.copy(alpha = if (enabled) 1f else 0.35f),
                modifier = Modifier.size(20.dp),
            )
        }
        if (enabled) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(18.dp).clip(CircleShape).background(neon),
                contentAlignment = Alignment.Center,
            ) { Text("$count", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnNeon) }
        }
        if (open && enabled) {
            val px = with(androidx.compose.ui.platform.LocalDensity.current) { 46.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, px),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                UndoHistoryMenu(history, pageNumberOf) { i -> open = false; onUndoTo(i) }
            }
        }
    }
}

@Composable
private fun UndoHistoryMenu(history: List<BatchStep>, pageNumberOf: (BatchStep) -> String?, onPick: (Int) -> Unit) {
    val neon = BatchNeon
    val now = System.currentTimeMillis()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AppColors.Paper,
        border = BorderStroke(2.dp, neon),
        shadowElevation = 12.dp,
        modifier = Modifier.width(300.dp),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Undo history", style = MaterialTheme.typography.titleMedium, color = AppColors.Ink, modifier = Modifier.weight(1f))
                Text("${history.size} of ${BatchEdit.MAX_UNDO}", fontSize = 12.sp, color = AppColors.Muted)
            }
            // Newest first.
            history.withIndex().reversed().forEachIndexed { row, (index, step) ->
                Surface(
                    onClick = { onPick(index) },
                    shape = RoundedCornerShape(16.dp),
                    color = AppColors.Surface,
                    border = BorderStroke(1.dp, AppColors.Border),
                ) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.Surface2), contentAlignment = Alignment.Center) {
                            if (step.moved) {
                                Icon(painterResource(R.drawable.ic_move), contentDescription = null, tint = AppColors.Ink, modifier = Modifier.size(18.dp))
                            } else {
                                CardBack(Modifier.width(14.dp).aspectRatio(CARD_ASPECT).clip(RoundedCornerShape(2.dp)))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(step.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val where = pageNumberOf(step)
                            Text(
                                listOfNotNull(step.detail.takeIf { it.isNotBlank() }, where, ago(now - step.at)).joinToString(" · "),
                                fontSize = 12.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(Modifier.clip(RoundedCornerShape(50)).background(neon).padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text(if (row == 0) "Undo" else "Undo ${row + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OnNeon)
                        }
                    }
                }
            }
            Text(
                "Tap a step to go back to before it. Newer steps are undone too.",
                fontSize = 12.sp, color = AppColors.Muted,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private fun ago(millis: Long): String {
    val s = millis / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        else -> "${s / 3600} h ago"
    }
}

/** Edit mode's buttons under the page: Mark empty, Move to page, and the small Delete page link. */
@Composable
internal fun BatchActionBar(
    tickedPockets: Int,
    tickedCards: Int,
    canMarkEmpty: Boolean,
    onMarkEmpty: () -> Unit,
    onMove: () -> Unit,
    onDeletePage: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BatchActionButton(
                title = "Mark empty",
                count = tickedPockets,
                enabled = canMarkEmpty,
                onClick = onMarkEmpty,
                modifier = Modifier.weight(1f),
            ) { CardBack(Modifier.width(11.dp).aspectRatio(CARD_ASPECT).clip(RoundedCornerShape(2.dp))) }
            BatchActionButton(
                title = "Move to page",
                count = tickedCards,
                enabled = tickedCards > 0,
                onClick = onMove,
                modifier = Modifier.weight(1f),
            ) { Icon(painterResource(R.drawable.ic_move), contentDescription = null, modifier = Modifier.size(16.dp)) }
        }
        Text(
            "Delete page",
            color = AppColors.Danger,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onDeletePage)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BatchActionButton(
    title: String,
    count: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: @Composable () -> Unit,
) {
    // A slim pill: small icon, the action, and how many are picked (if any).
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = AppColors.Surface,
        contentColor = AppColors.Ink,
        border = BorderStroke(1.dp, AppColors.BorderStrong),
        modifier = modifier.height(40.dp).graphicsLayer { alpha = if (enabled) 1f else 0.45f },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { icon() }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.Ink, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
            if (count > 0) Text(" · $count", fontSize = 13.sp, color = AppColors.Muted, maxLines = 1)
        }
    }
}

/**
 * "Main | Master" switch in the binder page's header, with a neon thumb that
 * slides across. Master counts secret rares too. Just on this phone.
 */
@Composable
internal fun MasterSetToggle(master: Boolean, onChange: (Boolean) -> Unit) {
    val neon = BatchNeon
    val half = 58.dp
    val x by animateDpAsState(if (master) half else 0.dp, spring(dampingRatio = 0.6f), label = "masterThumb")
    Box(
        Modifier.height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(AppColors.Ink.copy(alpha = 0.08f))
            .border(1.dp, AppColors.BorderStrong, RoundedCornerShape(50))
            .toggleable(value = master, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = if (master) "Master set, secret rares count" else "Main set, secret rares don't count" }
            .padding(3.dp),
    ) {
        Box(
            Modifier.offset(x = x).width(half).height(30.dp)
                .drawBehind { drawRoundRect(neon.copy(alpha = 0.35f), topLeft = Offset(-3f * density, -3f * density), size = Size(size.width + 6f * density, size.height + 6f * density), cornerRadius = CornerRadius(size.height)) }
                .clip(RoundedCornerShape(50)).background(neon),
        )
        Row {
            listOf("Main" to false, "Master" to true).forEach { (label, value) ->
                Box(Modifier.width(half).height(30.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (value == master) OnNeon else AppColors.Muted,
                    )
                }
            }
        }
    }
}

/**
 * "Move N cards": first pick a page (pages without enough "?" pockets are greyed),
 * then tap the "?" pockets on it in the order the cards should go. The cards move
 * as soon as there's one pocket for each. Or "Fill first free" for the quick way.
 */
@Composable
internal fun BatchMovePicker(
    cardCount: Int,
    pages: List<PickerPage>,
    currentPageId: String,
    columns: Int,
    // Pockets that are ticked (they can't be places to move into).
    ticked: Set<PocketRef>,
    onMove: (List<PocketRef>) -> Unit,
    onNewPage: () -> Unit,
) {
    var chosen by remember { mutableStateOf<String?>(null) }
    val picks = remember { mutableStateListOf<Int>() }
    fun freeIn(p: PickerPage) = p.pockets.filter { it.isFreeSlot() && (p.id to it.position) !in ticked }.map { it.position }
    val neon = BatchNeon
    val plural = if (cardCount == 1) "" else "s"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val page = pages.firstOrNull { it.id == chosen }
        if (page == null) {
            Text("Move $cardCount card$plural", style = MaterialTheme.typography.titleLarge)
            Text("Tap a page, then pick the slots. Purple shows the quick fill.", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val tiles: List<PickerPage?> = pages + listOf(null)
                tiles.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { p ->
                            if (p == null) {
                                PageTile(enabled = true, onClick = onNewPage, modifier = Modifier.weight(1f)) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(1.05f), contentAlignment = Alignment.Center) {
                                        Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = AppColors.Ink, modifier = Modifier.size(22.dp))
                                    }
                                    Text("New page", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
                                    Text("Adds page ${pages.size + 1}", fontSize = 11.sp, color = AppColors.Muted, maxLines = 1)
                                }
                            } else {
                                val free = freeIn(p)
                                val ok = free.size >= cardCount
                                PageTile(enabled = ok, onClick = { chosen = p.id; picks.clear() }, modifier = Modifier.weight(1f)) {
                                    BatchMiniGrid(p.pockets, columns, if (ok) free.take(cardCount).toSet() else emptySet(), ticked.filter { it.first == p.id }.map { it.second }.toSet())
                                    Text("Page ${p.number}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
                                    Text(
                                        when {
                                            p.id == currentPageId -> "This page · ${free.size} free"
                                            ok -> "${free.size} free"
                                            free.isEmpty() -> "Full"
                                            else -> "Only ${free.size} free"
                                        },
                                        fontSize = 11.sp, color = AppColors.Muted, maxLines = 1,
                                    )
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            val free = freeIn(page)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { chosen = null; picks.clear() }.semantics { contentDescription = "Back to pages" },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null, modifier = Modifier.size(20.dp)) }
                Text("Page ${page.number}: pick $cardCount slot$plural", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Tap the \"?\" slots in the order your cards should go. They move as soon as you've picked $cardCount.",
                style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted,
            )
            val cols = columns.coerceAtLeast(1)
            val rows = ((page.pockets.size + cols - 1) / cols).coerceAtLeast(1)
            val cardHeight = (300f - 8f * (rows - 1)) / rows
            val gridWidth = (cardHeight * CARD_ASPECT * cols + 8f * (cols - 1)).dp
            Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = gridWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                page.pockets.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { pocket ->
                            val shape = RoundedCornerShape(8.dp)
                            val canPick = pocket.position in free
                            val order = picks.indexOf(pocket.position) + 1
                            Box(
                                Modifier.weight(1f).aspectRatio(CARD_ASPECT).clip(shape)
                                    .then(
                                        if (canPick) {
                                            Modifier
                                                .background(if (order > 0) neon else AppColors.Surface)
                                                .drawBehind { if (order == 0) neonDash(AppColors.BorderStrong, 8f * density) }
                                                .clickable(onClickLabel = "Pick slot ${pocket.position + 1}") {
                                                    if (order > 0) picks.remove(pocket.position) else picks.add(pocket.position)
                                                    if (picks.size >= cardCount) onMove(picks.take(cardCount).map { page.id to it })
                                                }
                                        } else Modifier.graphicsLayer { alpha = 0.4f },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    canPick -> Text(if (order > 0) "$order" else "?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (order > 0) OnNeon else AppColors.Muted)
                                    else -> PocketPicture(pocket, Modifier.fillMaxSize())
                                }
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${picks.size} of $cardCount picked", fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.weight(1f))
                Surface(
                    onClick = { onMove(free.take(cardCount).map { page.id to it }) },
                    enabled = free.size >= cardCount,
                    shape = RoundedCornerShape(50),
                    color = AppColors.Surface,
                    border = BorderStroke(1.dp, AppColors.BorderStrong),
                ) {
                    Text("Fill first free", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.Ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun PageTile(enabled: Boolean, onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Border),
        modifier = modifier.graphicsLayer { alpha = if (enabled) 1f else 0.45f },
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { content() }
    }
}

/** A tiny drawing of a page, with the pockets the cards would land in shown in neon. */
@Composable
private fun BatchMiniGrid(pockets: List<PocketWithCard>, columns: Int, landing: Set<Int>, ticked: Set<Int>) {
    val neon = BatchNeon
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        pockets.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { p ->
                    val shape = RoundedCornerShape(2.dp)
                    Box(
                        Modifier.weight(1f).aspectRatio(CARD_ASPECT).clip(shape).then(
                            when {
                                p.position in landing -> Modifier.background(neon)
                                p.position in ticked -> Modifier.border(1.dp, AppColors.BorderStrong, shape)
                                p.cardId != null -> Modifier.background(Brush.linearGradient(AppColors.palette.holo))
                                p.isEmpty -> Modifier.background(AppColors.palette.cardBack)
                                else -> Modifier.border(1.dp, AppColors.BorderStrong, shape)
                            },
                        ),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
