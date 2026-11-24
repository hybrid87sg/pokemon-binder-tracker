package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.CardPictures
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.ui.components.CARD_ASPECT
import com.hybrid87sg.bindertracker.ui.components.CardBack
import com.hybrid87sg.bindertracker.ui.components.CardImage
import com.hybrid87sg.bindertracker.ui.components.PrimaryButton
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import kotlinx.coroutines.CancellationException

/**
 * A pocket's contents sliding into place from somewhere else: [from] is how
 * far away it starts (in pixels), [scale] how big. Each one gets its own [id]
 * so the same pocket can play it again.
 */
internal data class Arrival(val from: Offset, val scale: Float, val id: Long = System.nanoTime())

/** Something to show in the little message bar at the bottom, e.g. "Moved Pikachu · Undo". */
internal data class Notice(val text: String, val actions: List<Pair<String, () -> Unit>> = emptyList(), val id: Long = System.nanoTime())

/** A card flying off its pocket to the dot of the page it's moving to. */
internal data class Flight(val card: PocketWithCard, val from: Rect, val to: Offset)

/**
 * Everything about a card being held or dragged on a binder page. The
 * numbers are pocket positions. Rects and offsets are measured in the page
 * screen's own coordinates, so the dragged card can be drawn on top of everything.
 */
internal class PocketDrag {
    /** A finger is resting on this pocket (the glow builds up). */
    var pressed by mutableStateOf<Int?>(null)
    /** Held long enough: the card has lifted, waiting to see if the finger moves. */
    var lifted by mutableStateOf<Int?>(null)
    /** The pocket whose card is being dragged. */
    var origin by mutableStateOf<Int?>(null)
    /** The pocket the dragged card is over right now. */
    var hover by mutableStateOf<Int?>(null)
    /** A pocket whose card is hidden because it's flying to another page. */
    var hidden by mutableStateOf<Int?>(null)
    var card by mutableStateOf<PocketWithCard?>(null)
    var finger by mutableStateOf(Offset.Zero)
    /** Where on the card the finger is, so the card doesn't jump under the finger. */
    var grab = Offset.Zero
    /** How far the card leans, in degrees, from how fast it's moving sideways. */
    var tilt by mutableFloatStateOf(0f)

    /** The card picture of each pocket. */
    val cardRects = mutableStateMapOf<Int, Rect>()
    /** The whole pocket, card plus its name underneath (for finding the pocket under a finger). */
    val tileRects = mutableMapOf<Int, Rect>()
    val arrivals = mutableStateMapOf<Int, Arrival>()
    /**
     * What pockets should show straight away after a move, before the
     * database has caught up (a few frames later). Without this the cards
     * would blink back to their old places for a moment.
     */
    var shown by mutableStateOf<Map<Int, PocketWithCard>>(emptyMap())

    var screen: LayoutCoordinates? = null
    var sheet: LayoutCoordinates? = null

    // ---- The open binder (two pages side by side) ----
    // The page you're on is the one above. These are for the other pages on
    // show, so a card can be dropped on them too. Pockets are (page ID, position).

    /** The other pages on show right now (so pockets of pages turned away are ignored). */
    var otherPages: Set<String> = emptySet()
    val otherTiles = mutableMapOf<Pair<String, Int>, Rect>()
    val otherCards = mutableStateMapOf<Pair<String, Int>, Rect>()
    /** The pocket on another page the dragged card is over right now. */
    var hoverOther by mutableStateOf<Pair<String, Int>?>(null)
    val otherArrivals = mutableStateMapOf<Pair<String, Int>, Arrival>()
    /** Like [shown], for the other pages. */
    var otherShown by mutableStateOf<Map<Pair<String, Int>, PocketWithCard>>(emptyMap())
    /** Where the open binder is on the screen, to tell when a card is held at its edge. */
    var stage: Rect? = null
    /** The dragged card is held at the binder's right edge (1), left edge (-1), or neither (0). */
    var edge by mutableIntStateOf(0)

    fun otherAt(point: Offset, slop: Float): Pair<String, Int>? =
        otherTiles.entries.firstOrNull { (k, r) -> k.first in otherPages && r.inflate(slop).contains(point) }?.key

    /** A point on the page sheet, in the screen's coordinates. */
    fun onScreen(local: Offset): Offset {
        val s = screen
        val p = sheet
        return if (s != null && p != null && s.isAttached && p.isAttached) s.localPositionOf(p, local) else local
    }

    fun rectOf(c: LayoutCoordinates): Rect? {
        val s = screen ?: return null
        return if (s.isAttached && c.isAttached) s.localBoundingBoxOf(c, clipBounds = false) else null
    }

    fun pocketAt(point: Offset, slop: Float): Int? =
        tileRects.entries.firstOrNull { (_, r) -> r.inflate(slop).contains(point) }?.key

    fun clearDrag() {
        origin = null
        hover = null
        hoverOther = null
        edge = 0
        card = null
        lifted = null
        pressed = null
        tilt = 0f
    }
}

/** What the page screen does for each step of a touch on a pocket. */
internal interface PocketGestures {
    fun pocketAt(local: Offset): Int?
    fun press(position: Int)
    fun release()
    fun tap(position: Int)
    /** Held for the long-press time. Returns true if the card lifted (it can be dragged). */
    fun hold(position: Int): Boolean
    /** Let go of a lifted card without moving it. */
    fun openMenu(position: Int)
    fun startDrag(position: Int, local: Offset)
    fun dragTo(local: Offset)
    fun drop()
    fun cancel()
}

/**
 * Reads a finger on the page sheet:
 *  - moves before the long-press time: not ours (the page turns or scrolls),
 *  - lifts before it: a tap,
 *  - held: [PocketGestures.hold]; then moving drags the card and letting go opens the menu.
 * Once a card has lifted we "consume" every move, so the page can't turn under it.
 */
internal suspend fun PointerInputScope.detectPocketGestures(g: PocketGestures) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = g.pocketAt(down.position) ?: return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        g.press(start)
        var tapped = false
        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (change == null) return@withTimeoutOrNull Unit
                if (change.changedToUp()) {
                    tapped = !change.isConsumed
                    return@withTimeoutOrNull Unit
                }
                if (change.isConsumed || (change.position - down.position).getDistance() > slop) {
                    return@withTimeoutOrNull Unit
                }
            }
            @Suppress("UNREACHABLE_CODE")
            Unit
        }
        if (early != null) {
            g.release()
            if (tapped) g.tap(start)
            return@awaitEachGesture
        }
        if (!g.hold(start)) return@awaitEachGesture
        var dragging = false
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    g.cancel()
                    return@awaitEachGesture
                }
                if (change.changedToUp()) {
                    if (dragging) g.drop() else g.openMenu(start)
                    return@awaitEachGesture
                }
                if (!dragging && (change.position - down.position).getDistance() > slop) {
                    dragging = true
                    g.startDrag(start, change.position)
                }
                if (dragging) g.dragTo(change.position)
                change.consume()
            }
        } catch (e: CancellationException) {
            g.cancel()
            throw e
        }
    }
}

/** One page in the "Move to another page" picker. */
internal data class PickerPage(val id: String, val number: Int, val pockets: List<PocketWithCard>)

/**
 * The second step of a pocket's menu: pick a page, then a pocket on it.
 * [onMove] gets the page's ID (null = a new page at the end) and the pocket.
 */
@Composable
internal fun MovePicker(
    cardName: String,
    pages: List<PickerPage>,
    currentPageId: String,
    columns: Int,
    onBack: () -> Unit,
    onMove: (pageId: String?, position: Int) -> Unit,
) {
    fun freeIn(p: PickerPage) = p.pockets.indexOfFirst { it.cardId == null }
    val current = pages.indexOfFirst { it.id == currentPageId }
    // Start on the next page that has room, and its first free pocket.
    val first = remember(currentPageId) {
        pages.withIndex().firstOrNull { (i, p) -> i > current && freeIn(p) >= 0 }?.value
            ?: pages.firstOrNull { it.id != currentPageId && freeIn(it) >= 0 }
            ?: pages.firstOrNull { it.id != currentPageId }
    }
    var pageId by remember(currentPageId) { mutableStateOf(first?.id) }
    var slot by remember(currentPageId) { mutableStateOf(first?.let { freeIn(it).coerceAtLeast(0) } ?: 0) }
    val chosen = pages.firstOrNull { it.id == pageId }
    val target = chosen?.pockets?.getOrNull(slot)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "Back to the pocket menu" },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null, modifier = Modifier.size(20.dp)) }
            Text("Move $cardName to…", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Choose a page", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pages.forEach { p ->
                val here = p.id == currentPageId
                val free = p.pockets.count { it.cardId == null }
                PageChip(
                    selected = p.id == pageId,
                    enabled = !here,
                    onClick = { pageId = p.id; slot = freeIn(p).coerceAtLeast(0) },
                ) {
                    MiniGrid(p.pockets, columns)
                    Text("Page ${p.number}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
                    Text(
                        when {
                            here -> "This page"
                            free == 0 -> "Full: swaps"
                            else -> "$free free"
                        },
                        fontSize = 11.sp, color = AppColors.Muted, maxLines = 1,
                    )
                }
            }
            PageChip(selected = pageId == null, enabled = true, onClick = { pageId = null; slot = 0 }) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(20.dp), tint = AppColors.Ink)
                }
                Text("New page", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
                Text("At the end", fontSize = 11.sp, color = AppColors.Muted)
            }
        }
        if (chosen != null) {
            Text("Then tap a pocket to move it there", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
            val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
            // Kept small enough (about 260dp tall) that the Move button below stays on screen.
            val cols = columns.coerceAtLeast(1)
            val rows = ((chosen.pockets.size + cols - 1) / cols).coerceAtLeast(1)
            val cardHeight = (260f - 8f * (rows - 1)) / rows
            val gridWidth = (cardHeight * CARD_ASPECT * cols + 8f * (cols - 1) + 30f).dp
            Column(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = gridWidth).fillMaxWidth().clip(shape).background(AppColors.Surface).border(1.dp, AppColors.Border, shape)
                    .padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chosen.pockets.chunked(columns.coerceAtLeast(1)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { pocket ->
                            val on = pocket.position == slot
                            Box(
                                Modifier.weight(1f).aspectRatio(CARD_ASPECT).clip(RoundedCornerShape(6.dp))
                                    .then(if (on) Modifier.border(3.dp, AppColors.AccentText, RoundedCornerShape(6.dp)) else Modifier)
                                    // One tap moves the card there (the message bar has Undo).
                                    .clickable(onClickLabel = "Put it in pocket ${pocket.position + 1}") { slot = pocket.position; onMove(chosen.id, pocket.position) },
                                contentAlignment = Alignment.Center,
                            ) {
                                PocketPicture(pocket, Modifier.fillMaxSize())
                                if (on) {
                                    Text(
                                        if (pocket.cardId != null) "Swap" else "Here",
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.White,
                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.Accent).padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Text(
                "It goes in the first pocket of a new, empty page at the end of the binder.",
                style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted,
            )
        }
        PrimaryButton(
            text = when {
                chosen == null -> "Move to a new page"
                target?.cardName != null -> "Swap with ${target.cardName}"
                else -> "Move to page ${chosen.number}, pocket ${slot + 1}"
            },
            onClick = { onMove(chosen?.id, slot) },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PageChip(selected: Boolean, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AppColors.AccentText else AppColors.Border),
        modifier = Modifier.width(84.dp),
    ) {
        Column(
            Modifier.padding(7.dp).then(if (enabled) Modifier else Modifier.background(AppColors.Surface.copy(alpha = 0.5f))),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) { content() }
    }
}

/** A tiny drawing of a page: coloured blocks for cards, blue for empty pockets, outlines for "to scan". */
@Composable
internal fun MiniGrid(pockets: List<PocketWithCard>, columns: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        pockets.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { p ->
                    val shape = RoundedCornerShape(2.dp)
                    Box(
                        Modifier.weight(1f).aspectRatio(CARD_ASPECT).clip(shape).then(
                            when {
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

/** What a pocket looks like: its card, our card back if marked empty, or a "?". */
@Composable
internal fun PocketPicture(pocket: PocketWithCard, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            pocket.isEmpty -> CardBack(Modifier.fillMaxSize())
            pocket.cardId != null -> CardImage(pocket.imageUrl?.let { "$it/low.webp" }, contentDescription = pocket.cardName, modifier = Modifier.fillMaxSize(), backupUrl = CardPictures.backupUrl(pocket.cardId, large = false), cardId = pocket.cardId)
            else -> Box(
                Modifier.fillMaxSize().border(2.dp, AppColors.Check, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.Check) }
        }
    }
}

/**
 * "Go to page": every page of the binder as a tile showing how full it is.
 * Tapping a tile goes straight to that page.
 */
@Composable
internal fun PageJumpPicker(
    pages: List<PickerPage>,
    currentPageId: String,
    columns: Int,
    onPick: (pageId: String) -> Unit,
    // Opens the Glossary pop-up instead (null = no button).
    onGlossary: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Go to page", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            onGlossary?.let { GlossaryButton(it) }
        }
        Column(
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pages.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { p ->
                        val here = p.id == currentPageId
                        val filled = p.pockets.count { it.cardId != null }
                        Surface(
                            onClick = { onPick(p.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (here) AppColors.Surface2 else AppColors.Surface,
                            border = BorderStroke(if (here) 2.dp else 1.dp, if (here) AppColors.AccentText else AppColors.Border),
                            modifier = Modifier.weight(1f).semantics {
                                contentDescription = "Page ${p.number}, $filled of ${p.pockets.size} cards" + if (here) ", this page" else ""
                            },
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                MiniGrid(p.pockets, columns)
                                Text("${p.number}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                                Text("$filled/${p.pockets.size}", fontSize = 11.sp, color = AppColors.Muted, maxLines = 1)
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (pages.size > 8) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JumpButton("First page", Modifier.weight(1f)) { pages.firstOrNull()?.let { onPick(it.id) } }
                JumpButton("Last page", Modifier.weight(1f)) { pages.lastOrNull()?.let { onPick(it.id) } }
            }
        }
    }
}

@Composable
private fun JumpButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.BorderStrong),
        modifier = modifier,
    ) {
        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
        }
    }
}
