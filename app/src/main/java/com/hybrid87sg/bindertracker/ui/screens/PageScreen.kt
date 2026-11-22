package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hybrid87sg.bindertracker.R
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.hybrid87sg.bindertracker.ui.components.HoloBar
import com.hybrid87sg.bindertracker.ui.components.Motion
import com.hybrid87sg.bindertracker.ui.components.PageTurnStage
import com.hybrid87sg.bindertracker.ui.components.RingSpots
import com.hybrid87sg.bindertracker.ui.components.arrive
import com.hybrid87sg.bindertracker.ui.components.holoBrush
import com.hybrid87sg.bindertracker.ui.components.pressSquish
import com.hybrid87sg.bindertracker.ui.components.rememberPageTurnState
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.GuideRect
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.data.CardPictures
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.BottomBar
import com.hybrid87sg.bindertracker.ui.components.CARD_ASPECT
import com.hybrid87sg.bindertracker.ui.components.CardBack
import com.hybrid87sg.bindertracker.ui.components.CardImage
import com.hybrid87sg.bindertracker.ui.components.PrimaryButton
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import java.net.URLDecoder

// The "?" on a pocket still to identify, and its pale label. Deeper amber in
// light mode and brighter in dark, so it reads clearly on both.
private val CheckBorder get() = AppColors.Check
private val CheckFill get() = AppColors.CheckFill

/**
 * One binder page, shown as its pockets. Tap a card to open it, tap an
 * empty pocket to scan a card into it, or press and hold any pocket for its menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
    binderName: String,
    pageNumber: Int,
    columns: Int,
    rows: Int,
    pockets: List<PocketWithCard>,
    photo: ImageBitmap?,
    guide: GuideRect,
    // What automatic card reading found, e.g. "Recognised 6 of 9 cards", or null.
    readingStatus: String? = null,
    onBack: () -> Unit,
    onChooseCard: (position: Int) -> Unit,
    onViewCard: (cardId: String) -> Unit,
    onMarkEmpty: (position: Int) -> Unit,
    onClear: (position: Int) -> Unit,
    onDeletePage: () -> Unit,
    // Photograph just this pocket's card and fill the pocket with it.
    onScanCard: (position: Int) -> Unit = {},
    // Best guesses for pockets the app wasn't sure about, by pocket position.
    suggestions: Map<Int, List<Card>> = emptyMap(),
    // Tapping a guess puts that card in the pocket.
    onPickSuggestion: (position: Int, cardId: String) -> Unit = { _, _ -> },
    // A pocket whose menu should open by itself, then [onPocketOpened] is called.
    openPosition: Int? = null,
    onPocketOpened: () -> Unit = {},
    // The bit of the photo the app looked at for each pocket, if scanned just now.
    scanPictures: Map<Int, ImageBitmap> = emptyMap(),
    // Which page this is (keeps each page's animations apart), and how many the binder has.
    pageId: String = "",
    pageCount: Int = 0,
    // The pockets of the pages before and after, shown while a page is turning. Null = none.
    prevPockets: List<PocketWithCard>? = null,
    nextPockets: List<PocketWithCard>? = null,
    prevPageId: String? = null,
    nextPageId: String? = null,
    // Called once a page turn finishes: 1 = went to the next page, -1 = back a page.
    onTurn: (Int) -> Unit = {},
    // False in a binder shared with you as "View only": tapping a card just shows it.
    canEdit: Boolean = true,
    // Every page of the binder in order, and their pockets (for "Move to another page").
    pageIds: List<String> = emptyList(),
    pageCards: Map<String, List<PocketWithCard>> = emptyMap(),
    // Swaps what two pockets hold (dragging a card, or moving it to another page).
    // Doing the same swap again puts things back, which is how Undo works.
    onSwap: (pageA: String, positionA: Int, pageB: String, positionB: Int) -> Unit = { _, _, _, _ -> },
    // Moves a card into the first pocket of a new page at the end; then gives the new page's ID.
    onMoveToNewPage: (position: Int, onCreated: (String) -> Unit) -> Unit = { _, _ -> },
    // Shows another page of this binder straight away.
    onGoToPage: (pageId: String) -> Unit = {},
) {
    val turnState = rememberPageTurnState()
    var selected by remember { mutableStateOf<PocketWithCard?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    // The "Go to page" panel, opened by tapping the page number.
    var jumping by remember { mutableStateOf(false) }
    LaunchedEffect(openPosition, pockets) {
        val pocket = openPosition?.let { p -> pockets.firstOrNull { it.position == p } } ?: return@LaunchedEffect
        selected = pocket
        onPocketOpened()
    }
    // ---- Holding, dragging and moving cards ----
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val drag = remember { PocketDrag() }
    var moving by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    val flightProgress = remember { Animatable(0f) }
    // Where each page's dot (and the two arrows) sit, so a moving card can fly there.
    val dotCenters = remember { mutableStateMapOf<Int, Offset>() }
    var ping by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    LaunchedEffect(pageId) {
        drag.shown = emptyMap()
        drag.clearDrag()
        drag.arrivals.clear()
    }
    // A move shows at once. Let go of that as soon as the saved pockets match it
    // (or after 3 seconds, in case saving didn't work).
    LaunchedEffect(pockets, drag.shown) {
        val want = drag.shown
        if (want.isEmpty()) return@LaunchedEffect
        val saved = pockets.associateBy { it.position }
        val caughtUp = want.all { (pos, p) -> saved[pos]?.let { it.cardId == p.cardId && it.isEmpty == p.isEmpty } == true }
        if (!caughtUp) delay(3000)
        drag.shown = emptyMap()
    }
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            delay(5000)
            notice = null
        }
    }
    val shownPockets = pockets.map { drag.shown[it.position] ?: it }

    // The gesture code below is created once, so it reads everything through
    // these "updated state" holders to always see the latest values.
    val latestPockets by rememberUpdatedState(shownPockets)
    val editable by rememberUpdatedState(canEdit)
    val currentPageId by rememberUpdatedState(pageId)
    val ids by rememberUpdatedState(pageIds)
    val cards by rememberUpdatedState(pageCards)
    val swapCb by rememberUpdatedState(onSwap)
    val viewCard by rememberUpdatedState(onViewCard)
    val scanCard by rememberUpdatedState(onScanCard)
    val newPage by rememberUpdatedState(onMoveToNewPage)
    val goToPage by rememberUpdatedState(onGoToPage)

    fun shownPocket(position: Int) = latestPockets.firstOrNull { it.position == position }

    // Swaps two pockets on this page, with both cards sliding to their new places.
    // [draggedFrom] is where the dragged card was let go, if it was dragged.
    fun swapHere(from: Int, to: Int, draggedFrom: Offset?) {
        val a = shownPocket(from) ?: return
        val b = shownPocket(to) ?: return
        val ra = drag.cardRects[from]
        val rb = drag.cardRects[to]
        drag.shown = drag.shown + (from to b.copy(position = from)) + (to to a.copy(position = to))
        if (ra != null && rb != null) {
            drag.arrivals[to] = Arrival((draggedFrom ?: ra.topLeft) - rb.topLeft, if (draggedFrom != null) 1.1f else 1f)
            drag.arrivals[from] = Arrival(rb.topLeft - ra.topLeft, 1f)
        }
        swapCb(currentPageId, from, currentPageId, to)
    }

    fun undoSwap(page: String, from: Int, to: Int) {
        if (page == currentPageId) swapHere(from, to, null) else swapCb(page, from, page, to)
        notice = Notice("Undone. The cards are back where they were.")
    }

    fun goTo(id: String) {
        val here = ids.indexOf(currentPageId)
        val there = ids.indexOf(id)
        when {
            there < 0 || there == here -> Unit
            there == here + 1 -> turnState.turn(1)
            there == here - 1 -> turnState.turn(-1)
            else -> goToPage(id)
        }
    }

    fun undoMove(page: String, from: Int, otherPage: String, slot: Int) {
        swapCb(page, from, otherPage, slot)
        if (page == currentPageId) drag.arrivals[from] = Arrival(Offset.Zero, 0.85f)
        notice = Notice("Undone. The cards are back where they were.")
    }

    // "Move to another page": the card lifts, flies to that page's dot, and
    // whatever was in the other pocket comes back the other way.
    fun moveToPage(from: Int, target: String?, slot: Int) {
        val card = shownPocket(from) ?: return
        val rect = drag.cardRects[from] ?: return
        val here = ids.indexOf(currentPageId)
        val index = target?.let { ids.indexOf(it) } ?: ids.size
        val dest = dotCenters[index]
            ?: dotCenters[if (index > here) NEXT_ARROW else PREV_ARROW]
            ?: Offset(rect.center.x, rect.center.y + 600f)
        val incoming = target?.let { cards[it]?.firstOrNull { p -> p.position == slot } }
        val page = currentPageId
        scope.launch {
            delay(250) // let the menu slide away first
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            drag.hidden = from
            flightProgress.snapTo(0f)
            flight = Flight(card, rect, dest)
            flightProgress.animateTo(1f, tween(620, easing = Motion.Ease))
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            ping = index to System.nanoTime()
            val left = incoming?.copy(position = from) ?: PocketWithCard(from, false, null, null, null, null, null)
            if (page == currentPageId) {
                drag.shown = drag.shown + (from to left)
                drag.arrivals[from] = if (incoming?.cardId != null) Arrival(dest - rect.center, 0.2f) else Arrival(Offset.Zero, 0.85f)
            }
            flight = null
            drag.hidden = null
            if (target != null) {
                swapCb(page, from, target, slot)
                val number = index + 1
                notice = Notice(
                    if (incoming?.cardId != null) {
                        "Swapped ${card.cardName} with ${incoming.cardName} on page $number"
                    } else {
                        "Moved ${card.cardName} to page $number, pocket ${slot + 1}"
                    },
                    listOf("Go there" to { goTo(target) }, "Undo" to { undoMove(page, from, target, slot) }),
                )
            } else {
                newPage(from) { id ->
                    notice = Notice(
                        "Moved ${card.cardName} to new page ${index + 1}",
                        listOf("Go there" to { goToPage(id) }, "Undo" to { undoMove(page, from, id, 0) }),
                    )
                }
            }
        }
    }

    val gestures = remember(drag) {
        object : PocketGestures {
            override fun pocketAt(local: Offset): Int? = drag.pocketAt(drag.onScreen(local), 0f)
            override fun press(position: Int) { drag.pressed = position }
            override fun release() { drag.pressed = null }
            override fun tap(position: Int) {
                val p = shownPocket(position) ?: return
                p.cardId?.let(viewCard) ?: run { if (editable) scanCard(position) }
            }
            override fun hold(position: Int): Boolean {
                drag.pressed = null
                val p = shownPocket(position) ?: return false
                if (!editable) {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    notice = Notice("This binder is view only, so cards stay where they are.")
                    return false
                }
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                // Nothing to drag in an empty pocket: open its menu straight away, as before.
                if (p.cardId == null) {
                    selected = p
                    return false
                }
                drag.lifted = position
                return true
            }
            override fun openMenu(position: Int) {
                drag.lifted = null
                shownPocket(position)?.let { selected = it }
            }
            override fun startDrag(position: Int, local: Offset) {
                val rect = drag.cardRects[position] ?: return
                drag.card = shownPocket(position)
                drag.origin = position
                drag.lifted = null
                drag.finger = drag.onScreen(local)
                drag.grab = drag.finger - rect.topLeft
            }
            override fun dragTo(local: Offset) {
                val origin = drag.origin ?: return
                val f = drag.onScreen(local)
                // Lean the card the way it's moving, as if it had a little weight.
                drag.tilt = (drag.tilt * 0.6f + (f.x - drag.finger.x) * 0.5f).coerceIn(-12f, 12f)
                drag.finger = f
                val over = drag.pocketAt(f, with(density) { 6.dp.toPx() })?.takeIf { it != origin }
                if (over != drag.hover) {
                    drag.hover = over
                    // A tiny tick each time the card passes over a new pocket.
                    if (over != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            }
            override fun drop() {
                val from = drag.origin
                val to = drag.hover
                if (from == null || to == null) return cancel()
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                val at = drag.finger - drag.grab
                val a = shownPocket(from)
                val b = shownPocket(to)
                val page = currentPageId
                drag.clearDrag()
                swapHere(from, to, at)
                if (a != null && b != null) {
                    val text = if (b.cardId != null) "Swapped ${a.cardName} and ${b.cardName}" else "Moved ${a.cardName} to pocket ${to + 1}"
                    notice = Notice(text, listOf("Undo" to { undoSwap(page, from, to) }))
                }
            }
            override fun cancel() {
                // Let go away from any pocket: the card floats back home.
                val from = drag.origin
                val rect = from?.let { drag.cardRects[it] }
                if (from != null && rect != null && drag.card != null) {
                    drag.arrivals[from] = Arrival(drag.finger - drag.grab - rect.topLeft, 1.1f)
                }
                drag.clearDrag()
            }
        }
    }

    val identified = shownPockets.count { it.cardId != null }
    val empty = shownPockets.count { it.isEmpty }
    val toDo = shownPockets.size - identified - empty

    Box(Modifier.fillMaxSize().background(AppColors.Paper).onGloballyPositioned { drag.screen = it }) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(binderName, onBack)
            Spacer(Modifier.weight(1f))
            // Deleting the page lives behind Edit, with a Done button to leave.
            if (!editing && canEdit) {
                TextButton(onClick = { editing = true }, modifier = Modifier.height(48.dp)) {
                    Text("Edit", style = MaterialTheme.typography.labelLarge, color = AppColors.Ink)
                }
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Page $pageNumber", style = MaterialTheme.typography.headlineMedium)
            Text(
                buildList {
                    add("$identified of ${pockets.size} filled")
                    if (empty > 0) add("$empty empty")
                    if (toDo > 0) add("$toDo to identify")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Muted,
            )
            HoloBar(if (pockets.isEmpty()) 0f else identified.toFloat() / pockets.size, Modifier.padding(top = 4.dp))
            // Automatic card reading: "Reading the cards…", then what it found.
            readingStatus?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.AccentText, fontWeight = FontWeight.Medium)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 14.dp)) {
            // Drag the sheet sideways to turn it like a real binder page.
            PageTurnStage(
                state = turnState,
                canPrev = prevPockets != null,
                canNext = nextPockets != null,
                onTurned = onTurn,
                // Each sheet is known by its page, so the page you turn to keeps
                // its cards exactly as they were drawn during the turn.
                keyOf = { which ->
                    when (which) {
                        0 -> pageId
                        -1 -> prevPageId ?: "prev"
                        else -> nextPageId ?: "next"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { which ->
                when (which) {
                    // Tap: a card opens straight away, an empty pocket goes straight to the camera.
                    // Press and hold: the pocket's menu with every option.
                    0 -> BinderSheet(
                        shownPockets, columns, pageKey = pageId, live = true,
                        onPocket = { gestures.tap(it.position) },
                        onPocketHold = { if (canEdit) selected = it },
                        drag = drag,
                        gestures = gestures,
                    )
                    -1 -> BinderSheet(prevPockets.orEmpty(), columns, pageKey = prevPageId ?: "prev", live = false, onPocket = {}, onPocketHold = {})
                    else -> BinderSheet(nextPockets.orEmpty(), columns, pageKey = nextPageId ?: "next", live = false, onPocket = {}, onPocketHold = {})
                }
            }
            Text(
                if (canEdit) {
                    "Tap a card to see it, or an empty pocket to scan a card into it. Hold a card until it lifts, then drag it to another pocket. Hold and let go for more options."
                } else {
                    "Tap a card to see it and its price. This binder is view only."
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Muted,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            )
        }

        if (editing) {
            BottomBar {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetButton("Delete page", color = AppColors.Danger) { confirmDelete = true }
                    PrimaryButton(text = "Done", onClick = { editing = false }, modifier = Modifier.fillMaxWidth())
                }
            }
        } else if (pageCount > 1) {
            PageArrows(
                pageNumber = pageNumber,
                pageCount = pageCount,
                canPrev = prevPockets != null,
                canNext = nextPockets != null,
                onPrev = { turnState.turn(-1) },
                onNext = { turnState.turn(1) },
                onJump = if (pageIds.size > 1) ({ jumping = true }) else null,
                ping = ping,
                onPlaced = { k, c -> drag.rectOf(c)?.let { dotCenters[k] = it.center } },
            )
        } else {
            // Keep the pockets clear of the phone's own navigation bar.
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    // The card under your finger while dragging, drawn above everything.
    val dragged = drag.card
    val origin = drag.origin
    val originRect = origin?.let { drag.cardRects[it] }
    if (dragged != null && originRect != null) {
        val tl = drag.finger - drag.grab
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }
                .size(with(density) { originRect.width.toDp() }, with(density) { originRect.height.toDp() })
                .graphicsLayer {
                    scaleX = 1.1f; scaleY = 1.1f
                    rotationZ = drag.tilt
                    shadowElevation = 18.dp.toPx()
                    this.shape = shape
                    clip = true
                }
                .border(2.dp, AppColors.AccentText, shape),
        ) { PocketPicture(dragged, Modifier.fillMaxSize()) }
    }

    // A card flying off to another page's dot.
    flight?.let { f ->
        val t = flightProgress.value
        val start = f.from.center
        val x = start.x + (f.to.x - start.x) * t
        val y = start.y + (f.to.y - start.y) * t - 90f * sin(t * PI.toFloat())
        val grow = if (t < 0.25f) 1f + 0.12f * (t / 0.25f) else 1.12f - 1f * ((t - 0.25f) / 0.75f)
        Box(
            Modifier
                .offset { IntOffset((x - f.from.width / 2).roundToInt(), (y - f.from.height / 2).roundToInt()) }
                .size(with(density) { f.from.width.toDp() }, with(density) { f.from.height.toDp() })
                .graphicsLayer {
                    scaleX = grow; scaleY = grow
                    rotationZ = -4f + 12f * t
                    alpha = 1f - 0.6f * t
                    shadowElevation = 12.dp.toPx()
                    shape = RoundedCornerShape(6.dp)
                    clip = true
                },
        ) { PocketPicture(f.card, Modifier.fillMaxSize()) }
    }

    notice?.let { n ->
        key(n.id) {
            NoticeBar(
                n,
                onAction = { action -> notice = null; action() },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = if (pageCount > 1) 84.dp else 16.dp),
            )
        }
    }
    }

    selected?.let { pocket ->
        ModalBottomSheet(onDismissRequest = { selected = null; moving = false }, containerColor = AppColors.Paper) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              if (moving && pocket.cardId != null) {
                MovePicker(
                    cardName = pocket.cardName ?: "this card",
                    pages = pageIds.mapIndexed { i, id -> PickerPage(id, i + 1, if (id == pageId) shownPockets else pageCards[id].orEmpty()) },
                    currentPageId = pageId,
                    columns = columns,
                    onBack = { moving = false },
                    onMove = { target, slot ->
                        selected = null
                        moving = false
                        moveToPage(pocket.position, target, slot)
                    },
                )
              } else {
                Text(
                    pocket.cardName?.let { "Pocket ${pocket.position + 1}: $it" } ?: "Pocket ${pocket.position + 1}",
                    style = MaterialTheme.typography.titleLarge,
                )
                scanPictures[pocket.position]?.let { seen ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(seen, contentDescription = "What the camera saw", modifier = Modifier.height(96.dp).clip(RoundedCornerShape(6.dp)))
                        Text(
                            "What the camera saw. If this isn't just the card, scan again with the card filling the frame.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Muted,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                suggestions[pocket.position]?.takeIf { it.isNotEmpty() }?.let { guesses ->
                    Text("Best guesses: tap the right card", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        guesses.take(5).forEach { card ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .clickable { selected = null; onPickSuggestion(pocket.position, card.id) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CardImage(card.thumbnailUrl, contentDescription = "${card.name}, ${card.setName} ${card.number}", modifier = Modifier.fillMaxWidth(), backupUrl = card.thumbnailBackupUrl, cardId = card.id)
                                Text(card.setName, style = MaterialTheme.typography.labelSmall, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(5 - guesses.take(5).size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                SheetButton("Scan this card") { selected = null; onScanCard(pocket.position) }
                SheetButton(if (pocket.cardId == null) "Choose card from list" else "Change card") { selected = null; onChooseCard(pocket.position) }
                pocket.cardId?.let { id -> SheetButton("View card and price") { selected = null; onViewCard(id) } }
                if (pocket.cardId != null) SheetButton("Move to another page") { moving = true }
                if (!pocket.isEmpty) SheetButton("Mark as empty pocket") { selected = null; onMarkEmpty(pocket.position) }
                if (pocket.cardId != null || pocket.isEmpty) SheetButton("Clear pocket") { selected = null; onClear(pocket.position) }
              }
            }
        }
    }

    if (jumping) {
        ModalBottomSheet(onDismissRequest = { jumping = false }, containerColor = AppColors.Paper) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                PageJumpPicker(
                    pages = pageIds.mapIndexed { i, id -> PickerPage(id, i + 1, if (id == pageId) shownPockets else pageCards[id].orEmpty()) },
                    currentPageId = pageId,
                    columns = columns,
                    onPick = { id ->
                        jumping = false
                        if (id != pageId) goToPage(id)
                    },
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete page $pageNumber?") },
            text = { Text("This removes the page and its cards from $binderName. You can't undo this.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDeletePage() },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger),
                ) { Text("Delete page") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep page") } },
        )
    }
}

@Composable
private fun SheetButton(label: String, color: Color = AppColors.Ink, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
    }
}

/**
 * One binder sheet: a plastic page with ring holes down its left edge and
 * the pockets laid out in rows. [live] is false for the neighbour pages that
 * only show while a page is turning.
 */
@Composable
private fun BinderSheet(
    pockets: List<PocketWithCard>,
    columns: Int,
    pageKey: String,
    live: Boolean,
    onPocket: (PocketWithCard) -> Unit,
    onPocketHold: (PocketWithCard) -> Unit,
    // Only for the page you're on: holding and dragging cards.
    drag: PocketDrag? = null,
    gestures: PocketGestures? = null,
) {
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp)
    Column(
        Modifier.fillMaxWidth()
            .then(
                if (live && drag != null && gestures != null) {
                    Modifier.onGloballyPositioned { drag.sheet = it }.pointerInput(gestures) { detectPocketGestures(gestures) }
                } else Modifier,
            )
            .clip(shape).background(AppColors.Surface)
            .border(1.dp, AppColors.Border, shape)
            .drawBehind {
                // Punched holes for the rings.
                RingSpots.forEach { f -> drawCircle(AppColors.Paper, radius = 4.5.dp.toPx(), center = Offset(12.dp.toPx(), size.height * f)) }
            }
            .padding(start = 26.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        key(pageKey) {
            pockets.chunked(columns.coerceAtLeast(1)).forEachIndexed { rowIndex, row ->
                // The row with a lifted or moving card is drawn above the others,
                // so the card slides over its neighbours, not under them.
                val busy = drag != null && row.any { p ->
                    val k = p.position
                    k == drag.lifted || k == drag.hover || k == drag.origin || drag.arrivals.containsKey(k)
                }
                Row(Modifier.zIndex(if (busy) 1f else 0f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { i, pocket ->
                        val k = pocket.position
                        val origin = drag?.origin
                        val hovered = drag != null && drag.hover == k && origin != null
                        val shift = if (hovered && origin != null) {
                            val from = drag?.cardRects?.get(origin)
                            val to = drag?.cardRects?.get(k)
                            if (from != null && to != null) (from.center - to.center) * 0.22f else Offset.Zero
                        } else Offset.Zero
                        PocketTile(
                            pocket = pocket,
                            pageKey = pageKey,
                            live = live,
                            order = rowIndex * columns + i,
                            onTapped = { onPocket(pocket) },
                            onHeld = { onPocketHold(pocket) },
                            modifier = Modifier.weight(1f),
                            pressed = drag?.pressed == k,
                            lifted = drag?.lifted == k,
                            hole = origin == k,
                            hidden = drag?.hidden == k,
                            target = if (hovered) (if (pocket.cardId != null) DropKind.Swap else DropKind.Here) else null,
                            shift = shift,
                            arrival = drag?.arrivals?.get(k),
                            onArrived = { id -> drag?.let { d -> if (d.arrivals[k]?.id == id) d.arrivals.remove(k) } },
                            onCardPlaced = { c -> drag?.let { d -> d.rectOf(c)?.let { d.cardRects[k] = it } } },
                            onTilePlaced = { c -> drag?.let { d -> d.rectOf(c)?.let { d.tileRects[k] = it } } },
                        )
                    }
                    // Keep tiles the same width on a short last row.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** "‹  Page 2 of 6  ›" with a dot for each page (when there aren't too many). */
@Composable
private fun PageArrows(
    pageNumber: Int,
    pageCount: Int,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    // Tapping "Page 2 of 6" opens the "Go to page" panel (null = can't).
    onJump: (() -> Unit)? = null,
    // A page's dot to pop (a card just moved to that page), with a fresh number each time.
    ping: Pair<Int, Long>? = null,
    // Tells the screen where each dot (and arrow) is, so a moving card can fly to it.
    onPlaced: (key: Int, LayoutCoordinates) -> Unit = { _, _ -> },
) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton(R.drawable.ic_chevron_left, "Previous page", canPrev, onPrev, Modifier.onGloballyPositioned { onPlaced(PREV_ARROW, it) })
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .then(if (onJump != null) Modifier.clickable(onClickLabel = "Go to another page", onClick = onJump) else Modifier)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Page $pageNumber of $pageCount", style = MaterialTheme.typography.titleMedium, color = AppColors.Ink)
                if (onJump != null) Text("  ▾", fontSize = 11.sp, color = AppColors.Muted)
            }
            if (pageCount <= 12) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(pageCount) { i ->
                        val on = i == pageNumber - 1
                        val w by animateDpAsState(if (on) 18.dp else 6.dp, tween(Motion.Short, easing = Motion.Ease), label = "dot")
                        val pop = remember { Animatable(1f) }
                        LaunchedEffect(ping) {
                            if (ping?.first == i) {
                                pop.snapTo(2.4f)
                                pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                        Box(
                            Modifier.onGloballyPositioned { onPlaced(i, it) }
                                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                                .width(w).height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(if (on || pop.value > 1.01f) AppColors.AccentText else AppColors.BorderStrong),
                        )
                    }
                }
            }
        }
        ArrowButton(R.drawable.ic_chevron_right, "Next page", canNext, onNext, Modifier.onGloballyPositioned { onPlaced(NEXT_ARROW, it) })
    }
}

@Composable
private fun ArrowButton(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = press,
        shape = CircleShape,
        color = AppColors.Surface,
        contentColor = if (enabled) AppColors.Ink else AppColors.BorderStrong,
        border = BorderStroke(1.dp, AppColors.BorderStrong),
        modifier = modifier.size(48.dp).pressSquish(press).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** What happens if the dragged card is let go over a pocket. */
private enum class DropKind { Swap, Here }

/** The dot keys for the two arrows (pages are 0, 1, 2…). */
private const val PREV_ARROW = -1
private const val NEXT_ARROW = -2

@Composable
private fun PocketTile(
    pocket: PocketWithCard,
    pageKey: String,
    live: Boolean,
    order: Int,
    onTapped: () -> Unit,
    onHeld: () -> Unit,
    modifier: Modifier,
    // A finger is resting on it (the glow builds up).
    pressed: Boolean = false,
    // Held long enough: the card has lifted off the page.
    lifted: Boolean = false,
    // Its card is being dragged away: show an outline where it was.
    hole: Boolean = false,
    // Its card is flying to another page.
    hidden: Boolean = false,
    // The dragged card is over this pocket.
    target: DropKind? = null,
    // How far this pocket's card shuffles towards the dragged card's pocket, to show they'll swap.
    shift: Offset = Offset.Zero,
    arrival: Arrival? = null,
    onArrived: (Long) -> Unit = {},
    onCardPlaced: (LayoutCoordinates) -> Unit = {},
    onTilePlaced: (LayoutCoordinates) -> Unit = {},
) {
    val label = when {
        pocket.cardName != null -> pocket.cardName
        pocket.isEmpty -> "Empty pocket"
        else -> "Tap to scan"
    }
    // Cards pop into their pockets one after another when the page opens.
    val popIn = arrive(play = live, delayMillis = 80 + order * 45, durationMillis = 340)

    // Sliding in from another pocket after a move (see [Arrival]).
    val arriveAt = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val arriveScale = remember { Animatable(1f) }
    LaunchedEffect(arrival?.id) {
        val a = arrival
        if (a == null) {
            arriveAt.snapTo(Offset.Zero)
            arriveScale.snapTo(1f)
            return@LaunchedEffect
        }
        arriveAt.snapTo(a.from)
        arriveScale.snapTo(a.scale)
        launch { arriveScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
        arriveAt.animateTo(Offset.Zero, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
        onArrived(a.id)
    }

    // When a card gets identified while you watch (or while you were on the
    // camera screen), it flips over into place with a little sparkle. Not when
    // it was just moved here, though: that card slides in instead.
    var seenCard by rememberSaveable(pageKey, pocket.position) { mutableStateOf(pocket.cardId) }
    val flip = remember { Animatable(1f) }
    val sparkle = remember { Animatable(1f) }
    LaunchedEffect(pocket.cardId) {
        if (live && seenCard == null && pocket.cardId != null && arrival == null) {
            flip.snapTo(0f)
            sparkle.snapTo(0f)
            launch { flip.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
            sparkle.animateTo(1f, tween(750, easing = Motion.Ease))
        }
        seenCard = pocket.cardId
    }

    // While a finger rests on the pocket, a violet glow fades in round the card,
    // so you can see a press and hold is on its way.
    val holdGlow by animateFloatAsState(
        if ((pressed || lifted || target != null) && live) 1f else 0f,
        if (pressed && !lifted) tween(450) else tween(Motion.Short),
        label = "holdGlow",
    )
    val scale by animateFloatAsState(
        when {
            lifted -> 1.09f
            target == DropKind.Swap -> 0.9f
            target == DropKind.Here -> 1.04f
            pressed -> 0.95f
            else -> 1f
        },
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "pocketScale",
    )
    val nudge by animateOffsetAsState(
        if (target == DropKind.Swap) shift else Offset.Zero,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "swapNudge",
    )
    val lift by animateFloatAsState(if (lifted) 1f else 0f, tween(Motion.Short), label = "lift")
    Box(
        modifier
            .onGloballyPositioned(onTilePlaced)
            .graphicsLayer {
                alpha = popIn
                val s = 0.82f + 0.18f * popIn
                scaleX = s; scaleY = s
                translationY = (1f - popIn) * 10.dp.toPx()
            }
            // For TalkBack: a double tap opens it, a double tap and hold opens the menu.
            .semantics {
                onClick(label = if (pocket.cardId != null) "View card" else "Scan a card") { onTapped(); true }
                onLongClick(label = "More options") { onHeld(); true }
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val shape = RoundedCornerShape(6.dp)
            val outline = AppColors.BorderStrong
            val holeFill = AppColors.Surface2
            Box(
                Modifier.drawBehind {
                    // An outline where a dragged card used to be.
                    if (hole) {
                        val r = CornerRadius(6.dp.toPx())
                        drawRoundRect(holeFill, cornerRadius = r)
                        drawRoundRect(
                            outline, cornerRadius = r,
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                        )
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(CARD_ASPECT)
                        .onGloballyPositioned(onCardPlaced)
                        .graphicsLayer {
                            val t = arriveAt.value + nudge
                            translationX = t.x
                            translationY = t.y - lift * 4.dp.toPx()
                            val k = scale * arriveScale.value
                            scaleX = k; scaleY = k
                            rotationZ = if (target == DropKind.Swap) -3f else 0f
                            rotationY = (1f - flip.value) * 90f
                            cameraDistance = 12f * density
                            alpha = if (hole || hidden) 0f else 1f
                            shadowElevation = lift * 14.dp.toPx()
                            this.shape = shape
                        }
                        .then(if (pocket.cardId != null && live) Modifier.holoTouch() else Modifier)
                        .then(if (holdGlow > 0f) Modifier.border(3.dp, AppColors.AccentText.copy(alpha = holdGlow), shape) else Modifier)
                        .clip(shape)
                        .then(
                            when {
                                pocket.isEmpty -> Modifier
                                pocket.cardId == null -> Modifier.border(2.dp, CheckBorder, shape)
                                else -> Modifier.border(1.dp, AppColors.Border, shape)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        // Our own card-back picture, so an empty pocket looks like a card turned over.
                        pocket.isEmpty -> CardBack(Modifier.fillMaxSize())
                        // Identified: the official card picture from TCGdex, not the photo.
                        pocket.cardId != null -> CardImage(pocket.imageUrl?.let { "$it/low.webp" }, contentDescription = pocket.cardName, modifier = Modifier.fillMaxSize(), backupUrl = CardPictures.backupUrl(pocket.cardId, large = false), cardId = pocket.cardId)
                        else -> Text("?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CheckBorder)
                    }
                }
                if (sparkle.value < 1f) Sparkles(sparkle.value, Modifier.matchParentSize())
                // "Swap" or "Move here" on the pocket under the dragged card.
                if (target != null) {
                    Text(
                        if (target == DropKind.Swap) "Swap" else "Move here",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.White,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.Accent)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (pocket.cardId == null && !pocket.isEmpty) AppColors.Black else AppColors.Ink,
                modifier = if (pocket.cardId == null && !pocket.isEmpty) {
                    Modifier.clip(RoundedCornerShape(6.dp)).background(CheckFill).padding(horizontal = 6.dp, vertical = 2.dp)
                } else Modifier,
            )
            pocket.cardNumber?.let {
                val number = runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
                Text("#$number · ${pocket.setName.orEmpty()}", fontSize = 12.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The little message bar at the bottom: what just happened, plus buttons like "Undo". */
@Composable
private fun NoticeBar(notice: Notice, onAction: (() -> Unit) -> Unit, modifier: Modifier) {
    val show = arrive(play = true, durationMillis = 260)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Ink,
        contentColor = AppColors.Paper,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().graphicsLayer {
            alpha = show
            translationY = (1f - show) * 20.dp.toPx()
        },
    ) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(notice.text, style = MaterialTheme.typography.bodyMedium, color = AppColors.Paper, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            notice.actions.forEach { (name, action) ->
                TextButton(onClick = { onAction(action) }) {
                    Text(name, fontWeight = FontWeight.Bold, color = if (AppColors.isDark) AppColors.Accent else AppColors.palette.holo[3])
                }
            }
        }
    }
}

/** Eight little gold stars bursting out from the middle of a card. [t] goes 0 to 1. */
@Composable
private fun Sparkles(t: Float, modifier: Modifier) {
    Canvas(modifier) {
        val c = center
        val reach = size.minDimension * 0.75f
        val star = 5.dp.toPx() * (1f - t * 0.4f)
        val gold = Color(0xFFFFD36E).copy(alpha = (1f - t).coerceIn(0f, 1f))
        repeat(8) { k ->
            val a = k / 8f * 2f * PI.toFloat()
            val p = Offset(c.x + cos(a) * reach * t, c.y + sin(a) * reach * t)
            val path = Path().apply {
                moveTo(p.x, p.y - star); lineTo(p.x + star * 0.3f, p.y - star * 0.3f)
                lineTo(p.x + star, p.y); lineTo(p.x + star * 0.3f, p.y + star * 0.3f)
                lineTo(p.x, p.y + star); lineTo(p.x - star * 0.3f, p.y + star * 0.3f)
                lineTo(p.x - star, p.y); lineTo(p.x - star * 0.3f, p.y - star * 0.3f); close()
            }
            drawPath(path, gold)
        }
    }
}

/**
 * Holo shine: a soft rainbow sheen over a card, which brightens and tilts
 * towards your finger while you touch it, like tilting a holo under a light.
 */
@Composable
private fun Modifier.holoTouch(): Modifier {
    var touch by remember { mutableStateOf<Offset?>(null) }
    val tx by animateFloatAsState(touch?.x?.minus(0.5f) ?: 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "tiltX")
    val ty by animateFloatAsState(touch?.y?.minus(0.5f) ?: 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "tiltY")
    val glow by animateFloatAsState(if (touch != null) 1f else 0f, tween(Motion.Medium), label = "glow")
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    // Only watch the finger; don't take the tap away from the pocket.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    touch = if (change.pressed && size.width > 0 && size.height > 0) {
                        Offset(
                            (change.position.x / size.width).coerceIn(0f, 1f),
                            (change.position.y / size.height).coerceIn(0f, 1f),
                        )
                    } else null
                }
            }
        }
        .graphicsLayer {
            rotationY = tx * 18f
            rotationX = -ty * 14f
            cameraDistance = 12f * density
        }
        .drawWithContent {
            drawContent()
            drawRect(holoBrush(size.width, shift = tx + 0.5f), alpha = 0.16f + 0.3f * glow, blendMode = BlendMode.Overlay)
        }
}
