package com.hybrid87sg.bindertracker.ui.screens

import com.hybrid87sg.bindertracker.ui.components.rememberPhoneTilt
import com.hybrid87sg.bindertracker.ui.components.pocketSleeve
import com.hybrid87sg.bindertracker.ui.components.pageExpansion
import com.hybrid87sg.bindertracker.ui.components.isShinyRarity
import com.hybrid87sg.bindertracker.ui.components.holoTilt
import com.hybrid87sg.bindertracker.ui.components.expansionPattern
import com.hybrid87sg.bindertracker.ui.components.accentFor
import com.hybrid87sg.bindertracker.ui.components.ExpansionBadge
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.State
import com.hybrid87sg.bindertracker.ui.theme.windowBackground
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInWindow
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.hybrid87sg.bindertracker.ui.components.CardFlight
import com.hybrid87sg.bindertracker.ui.components.cardFlight
import kotlin.math.roundToInt
import com.hybrid87sg.bindertracker.ui.components.HoloBar
import com.hybrid87sg.bindertracker.ui.components.Motion
import com.hybrid87sg.bindertracker.ui.components.PageTurnStage
import com.hybrid87sg.bindertracker.ui.components.SpreadTurnStage
import com.hybrid87sg.bindertracker.ui.components.SpineGap
import com.hybrid87sg.bindertracker.ui.components.FoldedSpine
import androidx.compose.ui.platform.LocalConfiguration
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
    // Open the card in a pocket: (page, pocket position).
    onViewCard: (pageId: String, position: Int) -> Unit,
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
    // Pockets of the pages around this one, for the open binder on a big screen.
    spreadPockets: Map<String, List<PocketWithCard>> = emptyMap(),
    // The binder's colour, round the pages of the open binder.
    coverColor: Color? = null,
    // Adds an empty page at the end of the binder (null = can't).
    onAddPage: (() -> Unit)? = null,
    // Missing cards shown greyed out in free pockets, by page then pocket position
    // (a binder collecting expansions: see Checklist.ghosts).
    ghosts: Map<String, Map<Int, Card>> = emptyMap(),
    // Opens the Glossary pop-up (every page of the binder). Null = no Glossary button.
    onOpenGlossary: (() -> Unit)? = null,
    // The binder's ID: its first page leaves a picture of itself for the 3D binder
    // on the shelf to open onto (see PageLanding). Null = don't.
    landingBinderId: String? = null,
) {
    val turnState = rememberPageTurnState()
    var selected by remember { mutableStateOf<PocketWithCard?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showTips by remember { mutableStateOf(false) }
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
    val viewCardLatest by rememberUpdatedState(onViewCard)

    // Opening a card: it slides up out of its sleeve, then flies into the card screen (see CardFlight).
    fun openCard(page: String, position: Int) {
        val key = CardFlight.pocketKey(page, position)
        if (CardFlight.lifting != null) return
        CardFlight.lifting = key
        scope.launch {
            // The binder leaves the screen while the card flies, which cancels this
            // job. "finally" still runs then, so the flag is always cleared (if it
            // stayed set, every later tap on a card would be ignored).
            try {
                delay(CardFlight.LiftMillis.toLong())
                viewCardLatest(page, position)
                delay(CardFlight.FlyMillis + 200L)
            } finally {
                if (CardFlight.lifting == key) CardFlight.lifting = null
            }
        }
    }
    // Back from the card screen: nothing is lifted any more (the card is simply in its pocket).
    LaunchedEffect(Unit) { CardFlight.lifting = null }
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
    // (A card just dropped here from another page of the open binder shows at once too.)
    val shownPockets = pockets.map { drag.shown[it.position] ?: drag.otherShown[pageId to it.position] ?: it }

    // ---- The open binder: two pages side by side on a big screen ----
    // On an unfolded phone (or a tablet) the pages pair up 1–2, 3–4 and so on,
    // like an open binder. The page you're on is one of the two; folded, you
    // carry on from that page.
    val config = LocalConfiguration.current
    val here = pageIds.indexOf(pageId)
    val open = config.screenWidthDp >= 600 && config.screenHeightDp >= 480 && here >= 0

    // Note where this page's cards sit on the screen once it has settled, so the
    // 3D binder on the shelf can open with its cards in exactly these spots and
    // hand over to this screen without a jump (see PageLanding). Folded, every
    // page sits in the same place; unfolded, only the first page is the one it opens onto.
    val landingContext = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // (The pockets can arrive after the screen first appears, so read the latest ones.)
    val landingPockets by rememberUpdatedState(pockets)
    // The binder area (cover, rings, page) drawn through a layer, so a picture of it can be taken.
    val shotLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    var shotRect by remember { mutableStateOf<Rect?>(null) }
    val shotArea = Modifier
        .drawWithContent {
            shotLayer.record { this@drawWithContent.drawContent() }
            drawLayer(shotLayer)
        }
        .onGloballyPositioned { shotRect = it.boundsInWindow() }
    // (A new picture whenever the cards on the page change.)
    val pageLook = pockets.map { Triple(it.cardId, it.isEmpty, it.position) }.hashCode()
    LaunchedEffect(pageId, columns, rows, config.screenWidthDp, config.screenHeightDp, open, editing, pageLook) {
        if (open && pageId != pageIds.firstOrNull()) return@LaunchedEffect
        lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
        delay(1200)
        val count = columns * rows
        if (count <= 0) return@LaunchedEffect
        // The pockets (and so the cards' places) can still be loading: keep looking
        // for a little while, and note the spots once the page is ready and still.
        var rects: List<Float>? = null
        for (attempt in 0 until 30) {
            if (attempt > 0) delay(300)
            // (Not while editing: the Delete page bar makes the page smaller.)
            if (editing || turnState.direction != 0 || drag.origin != null || landingPockets.size != count) continue
            val screen = drag.screen?.takeIf { it.isAttached } ?: continue
            val o = screen.localToWindow(Offset.Zero)
            val cards = (0 until count).map { drag.cardRects[it] }
            if (cards.any { it == null }) continue
            rects = cards.filterNotNull().flatMap { r -> listOf(r.left + o.x, r.top + o.y, r.right + o.x, r.bottom + o.y) }
            break
        }
        if (rects == null) return@LaunchedEffect
        com.hybrid87sg.bindertracker.ui.three.PageLanding.record(
            landingContext,
            com.hybrid87sg.bindertracker.ui.three.PageLanding.key(columns, rows, config.screenWidthDp, config.screenHeightDp),
            rects,
        )
        // The first page: keep a picture of the whole binder area for the 3D binder.
        if (landingBinderId != null && pageId == pageIds.firstOrNull()) {
            val area = shotRect ?: return@LaunchedEffect
            if (area.width < 1f || area.height < 1f) return@LaunchedEffect
            val picture = runCatching { shotLayer.toImageBitmap().asAndroidBitmap() }.getOrNull() ?: return@LaunchedEffect
            val key = com.hybrid87sg.bindertracker.ui.three.PageLanding.shotKey(landingBinderId, config.screenWidthDp, config.screenHeightDp, AppColors.isDark)
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                val soft = if (picture.config == android.graphics.Bitmap.Config.HARDWARE) picture.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else picture
                if (soft != null) {
                    com.hybrid87sg.bindertracker.ui.three.PageLanding.saveShot(landingContext, key, soft, listOf(area.left, area.top, area.right, area.bottom), open)
                }
            }
        }
    }
    // While a card is being dragged, the pages can turn under it (hold it at the
    // binder's edge) without leaving the page it came from: this is then the
    // left page of the pair on show. Null = the pair of the page you're on.
    var dragSpread by remember { mutableStateOf<Int?>(null) }
    val leftIndex = dragSpread ?: if (here >= 0) here - here % 2 else 0
    fun pageAt(i: Int): String? = if (i >= 0) pageIds.getOrNull(i) else null
    fun pocketsOf(id: String): List<PocketWithCard> =
        if (id == pageId) shownPockets else (spreadPockets[id] ?: pageCards[id].orEmpty()).map { drag.otherShown[id to it.position] ?: it }
    // The other pages on show, where a dragged card can be dropped too.
    drag.otherPages = if (open) setOfNotNull(pageAt(leftIndex), pageAt(leftIndex + 1)) - pageId else emptySet()
    // Moves onto other pages show at once too, until the saved pockets catch up.
    LaunchedEffect(spreadPockets, drag.otherShown) {
        val want = drag.otherShown
        if (want.isEmpty()) return@LaunchedEffect
        val caughtUp = want.all { (k, p) ->
            spreadPockets[k.first]?.firstOrNull { it.position == k.second }?.let { it.cardId == p.cardId && it.isEmpty == p.isEmpty } == true
        }
        if (!caughtUp) delay(3000)
        drag.otherShown = emptyMap()
    }
    val leftId = pageAt(leftIndex)
    val rightId = pageAt(leftIndex + 1)
    val spreadShown = if (open) listOfNotNull(leftId, rightId).flatMap { pocketsOf(it) } else shownPockets
    val isOpen by rememberUpdatedState(open)

    // The gesture code below is created once, so it reads everything through
    // these "updated state" holders to always see the latest values.
    val latestPockets by rememberUpdatedState(shownPockets)
    val editable by rememberUpdatedState(canEdit)
    val currentPageId by rememberUpdatedState(pageId)
    val ids by rememberUpdatedState(pageIds)
    val cards by rememberUpdatedState(pageCards)
    val swapCb by rememberUpdatedState(onSwap)
    val scanCard by rememberUpdatedState(onScanCard)
    val newPage by rememberUpdatedState(onMoveToNewPage)
    val goToPage by rememberUpdatedState(onGoToPage)
    val nearbyCards by rememberUpdatedState(spreadPockets)
    val latestGhosts by rememberUpdatedState(ghosts)
    val latestGuesses by rememberUpdatedState(suggestions)

    fun shownPocket(position: Int) = latestPockets.firstOrNull { it.position == position }

    // The missing card shown greyed out in a free pocket, if any. Not in a pocket
    // the app just scanned and wasn't sure about: a real card is in that one.
    fun ghostAt(page: String, position: Int): Card? =
        if (page == currentPageId && latestGuesses[position].orEmpty().isNotEmpty()) null
        else latestGhosts[page]?.get(position)

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
        val now = ids.indexOf(currentPageId)
        val there = ids.indexOf(id)
        // Open binder: pages turn two at a time.
        val step = if (isOpen) 2 else 1
        when {
            there < 0 || there == now -> Unit
            there / step == now / step -> goToPage(id)
            there / step == now / step + 1 -> turnState.turn(1)
            there / step == now / step - 1 -> turnState.turn(-1)
            else -> goToPage(id)
        }
    }

    // Open binder: a tap or hold on the other page makes it the page you're on,
    // then does what it would do there.
    fun tapOther(id: String, p: PocketWithCard) {
        goToPage(id)
        if (p.cardId != null || ghostAt(id, p.position) != null) openCard(id, p.position) else if (editable) scanCard(p.position)
    }

    fun holdOther(id: String, p: PocketWithCard) {
        if (!editable) {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            notice = Notice("This binder is view only, so cards stay where they are.")
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        goToPage(id)
        selected = p
    }

    fun undoMove(page: String, from: Int, otherPage: String, slot: Int) {
        drag.otherShown = emptyMap()
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

    // A drag in the open binder has ended. If the pages were turned under the
    // card, carry on from the pages on show ([landed] = the page it was dropped on).
    fun endSpreadDrag(landed: String?) {
        val at = dragSpread ?: return
        dragSpread = null
        val onShow = listOfNotNull(ids.getOrNull(at), ids.getOrNull(at + 1))
        if (currentPageId !in onShow) (landed ?: onShow.firstOrNull())?.let { goToPage(it) }
    }

    // Drops the dragged card on a pocket of the other page on show: they swap
    // (or the card simply moves in, if that pocket is empty).
    fun dropOnOther(from: Int, target: Pair<String, Int>) {
        val (otherPage, slot) = target
        val a = shownPocket(from) ?: return
        val b = (nearbyCards[otherPage] ?: cards[otherPage].orEmpty())
            .map { drag.otherShown[otherPage to it.position] ?: it }
            .firstOrNull { it.position == slot } ?: PocketWithCard(slot, false, null, null, null, null, null)
        val at = drag.finger - drag.grab
        val fromRect = drag.cardRects[from]
        val toRect = drag.otherCards[target]
        drag.shown = drag.shown + (from to b.copy(position = from))
        drag.otherShown = drag.otherShown + (target to a.copy(position = slot))
        if (toRect != null) drag.otherArrivals[target] = Arrival(at - toRect.topLeft, 1.1f)
        if (fromRect != null) {
            drag.arrivals[from] = if (b.cardId != null && toRect != null) Arrival(toRect.topLeft - fromRect.topLeft, 1f) else Arrival(Offset.Zero, 0.85f)
        }
        val page = currentPageId
        swapCb(page, from, otherPage, slot)
        val number = ids.indexOf(otherPage) + 1
        notice = Notice(
            if (b.cardId != null) "Swapped ${a.cardName} with ${b.cardName} on page $number" else "Moved ${a.cardName} to page $number, pocket ${slot + 1}",
            listOf("Undo" to { undoMove(page, from, otherPage, slot) }),
        )
    }

    val gestures = remember(drag) {
        object : PocketGestures {
            override fun pocketAt(local: Offset): Int? = drag.pocketAt(drag.onScreen(local), 0f)
            override fun press(position: Int) { drag.pressed = position }
            override fun release() { drag.pressed = null }
            override fun tap(position: Int) {
                val p = shownPocket(position) ?: return
                // A grey missing card opens too: its card screen has "Put it in" and "Scan it in".
                if (p.cardId != null || ghostAt(currentPageId, position) != null) openCard(currentPageId, position)
                else if (editable) scanCard(position)
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
                val slop = with(density) { 6.dp.toPx() }
                // Nothing to drop on while the pages are turning.
                val turning = turnState.direction != 0
                val over = if (turning) null else drag.pocketAt(f, slop)?.takeIf { it != origin }
                // Open binder: the other page on show is a place to drop it too.
                val overOther = if (turning || over != null || !isOpen) null else drag.otherAt(f, slop)
                if (over != drag.hover || overOther != drag.hoverOther) {
                    drag.hover = over
                    drag.hoverOther = overOther
                    // A tiny tick each time the card passes over a new pocket.
                    if (over != null || overOther != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                // Held at the binder's outer edge: the pages turn after a moment.
                val stage = drag.stage
                val edgeWidth = with(density) { 36.dp.toPx() }
                drag.edge = when {
                    !isOpen || stage == null -> 0
                    f.x > stage.right - edgeWidth -> 1
                    f.x < stage.left + edgeWidth -> -1
                    else -> 0
                }
            }
            override fun drop() {
                val from = drag.origin
                // Pages turning under the card: nothing to drop on yet.
                if (turnState.direction != 0) return cancel()
                // Work out the target again where the finger is now, in the pages on show now
                // (the pages may have turned since the finger last moved).
                val slop = with(density) { 6.dp.toPx() }
                val to = drag.pocketAt(drag.finger, slop)?.takeIf { it != from }
                val toOther = if (to == null && isOpen) drag.otherAt(drag.finger, slop) else null
                if (from != null && to == null && toOther != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    dropOnOther(from, toOther)
                    drag.clearDrag()
                    endSpreadDrag(toOther.first)
                    return
                }
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
                endSpreadDrag(null)
            }
            override fun cancel() {
                // Let go away from any pocket: the card floats back home.
                val from = drag.origin
                val rect = from?.let { drag.cardRects[it] }
                if (from != null && rect != null && drag.card != null) {
                    drag.arrivals[from] = Arrival(drag.finger - drag.grab - rect.topLeft, 1.1f)
                }
                drag.clearDrag()
                endSpreadDrag(null)
            }
        }
    }

    // A dragged card held at the open binder's edge turns the pages, and keeps
    // turning every second or so while it stays there.
    LaunchedEffect(drag.edge) {
        val side = drag.edge
        if (side == 0) return@LaunchedEffect
        delay(650)
        while (drag.edge == side && drag.origin != null) {
            if (turnState.direction == 0) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                turnState.turn(side)
            }
            delay(1100)
        }
    }

    val identified = spreadShown.count { it.cardId != null }
    val empty = spreadShown.count { it.isEmpty }
    val toDo = spreadShown.size - identified - empty
    val canPrevTurn = if (open) leftIndex >= 2 else prevPockets != null
    val canNextTurn = if (open) leftIndex + 2 < pageIds.size else nextPockets != null

    Box(Modifier.fillMaxSize().windowBackground().onGloballyPositioned { drag.screen = it }) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(binderName, onBack)
            Spacer(Modifier.weight(1f))
            // The tips used to sit under the page as fine print (and made the page scroll).
            onOpenGlossary?.let { GlossaryButton(it) }
            TipsButton { showTips = true }
            // Deleting the page lives behind Edit, with a Done button to leave.
            if (!editing && canEdit) {
                TextButton(onClick = { editing = true }, modifier = Modifier.height(48.dp)) {
                    Text("Edit", style = MaterialTheme.typography.labelLarge, color = AppColors.Ink)
                }
            }
        }
        // (No "Page 3" heading: the page number shows once, in the navigation at the bottom.)
        Spacer(Modifier.height(4.dp))

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        // Folded: the page sits on the binder's cover, sized so the whole page fits
        // the screen's height without scrolling (see [foldedCoverWidth]).
        val coverWidth = if (open) spreadWidth(
            maxWidth = maxWidth - 32.dp,
            maxHeight = maxHeight,
            rows = (listOfNotNull(leftId, rightId).maxOfOrNull { pocketsOf(it).size } ?: shownPockets.size)
                .let { (it + columns.coerceAtLeast(1) - 1) / columns.coerceAtLeast(1) },
            columns = columns.coerceAtLeast(1),
        ) else foldedCoverWidth(
            maxWidth = maxWidth - 20.dp,
            maxHeight = maxHeight,
            rows = listOfNotNull(shownPockets.size, prevPockets?.size, nextPockets?.size)
                .maxOf { (it + columns.coerceAtLeast(1) - 1) / columns.coerceAtLeast(1) },
            columns = columns.coerceAtLeast(1),
        )
        // (Still scrolls if a page is too tall for a tiny screen, so nothing is ever cut off.)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = if (open) 18.dp else 10.dp, end = if (open) 14.dp else 10.dp),
        ) {
            if (open) {
                // Two pages side by side, rings down the middle; a whole spread turns at once.
                SpreadTurnStage(
                    state = turnState,
                    canPrev = canPrevTurn,
                    canNext = canNextTurn,
                    onTurned = { d ->
                        if (drag.origin != null) {
                            // Turned under a dragged card: keep hold of the page it came from.
                            dragSpread = leftIndex + 2 * d
                        } else {
                            pageAt(leftIndex + 2 * d)?.let { goToPage(it) }
                        }
                    },
                    isPage = { which, side -> pageAt(leftIndex + which * 2 + side) != null },
                    keyOf = { which, side -> pageAt(leftIndex + which * 2 + side) ?: "end:$which:$side" },
                    coverColor = coverColor ?: AppColors.CardBack,
                    // The page the card is being dragged from stays alive (out of sight) if it's turned away.
                    keep = if (dragSpread != null && here >= 0) Pair(Math.floorDiv(here - leftIndex, 2), here % 2) else null,
                    edgeGlow = when {
                        drag.origin == null -> 0
                        drag.edge > 0 && canNextTurn -> 1
                        drag.edge < 0 && canPrevTurn -> -1
                        else -> 0
                    },
                    // Sized so both pages fit the screen's height too (see [spreadWidth]).
                    modifier = Modifier.align(Alignment.CenterHorizontally).width(coverWidth)
                        .then(shotArea)
                        .onGloballyPositioned { drag.stage = drag.rectOf(it) },
                ) { which, side ->
                    val id = pageAt(leftIndex + which * 2 + side)
                    if (id == null) {
                        EndOfBinder(onAddPage = if (canEdit) onAddPage else null)
                    } else {
                        // Every page goes through this ONE BinderSheet call, so a page you turn to
                        // is never rebuilt (its cards would pop in all over again).
                        val live = id == pageId
                        val other = which == 0 && !live
                        BinderSheet(
                            pocketsOf(id), columns, pageKey = id, live = live,
                            ghostAt = { pos -> ghostAt(id, pos) },
                            // (Also what TalkBack's double tap / double tap and hold do.)
                            onPocket = { if (live) gestures.tap(it.position) else if (other) tapOther(id, it) },
                            onPocketHold = { if (live) { if (canEdit) selected = it } else if (other) holdOther(id, it) },
                            drag = if (live) drag else null,
                            gestures = if (live) gestures else null,
                            mirrored = side == 0,
                            onOtherTap = if (other) ({ p -> tapOther(id, p) }) else null,
                            onOtherHold = if (other) ({ p -> holdOther(id, p) }) else null,
                            peer = if (other) drag else null,
                        )
                    }
                }
            } else {
                // The binder's cover round the page, like the open binder's two-page spread.
                Box(
                    Modifier.align(Alignment.CenterHorizontally).width(coverWidth)
                        .then(shotArea)
                        .binderCover(coverColor ?: AppColors.CardBack)
                        .padding(start = CoverRingSide, top = CoverEdge, end = CoverEdge, bottom = CoverEdge),
                ) {
                // Drag the sheet sideways to turn it like a real binder page.
                PageTurnStage(
                    state = turnState,
                    canPrev = prevPockets != null,
                    canNext = nextPockets != null,
                    onTurned = onTurn,
                    // The back of the turning sheet shows this binder's own pocket layout.
                    columns = columns.coerceAtLeast(1),
                    rows = listOfNotNull(shownPockets.size, prevPockets?.size, nextPockets?.size)
                        .maxOf { (it + columns.coerceAtLeast(1) - 1) / columns.coerceAtLeast(1) },
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
                    // Every sheet is drawn by this ONE BinderSheet call. (Separate calls
                    // for "this page" and "next page" would count as different things,
                    // so when a turn ended the page you turned to was thrown away and
                    // rebuilt, and its cards popped in all over again.)
                    val live = which == 0
                    val sheetPockets = when (which) {
                        0 -> shownPockets
                        -1 -> prevPockets.orEmpty()
                        else -> nextPockets.orEmpty()
                    }
                    val sheetKey = when (which) {
                        0 -> pageId
                        -1 -> prevPageId ?: "prev"
                        else -> nextPageId ?: "next"
                    }
                    // Tap: a card opens straight away, an empty pocket goes straight to the camera.
                    // Press and hold: the pocket's menu with every option.
                    BinderSheet(
                        sheetPockets, columns, pageKey = sheetKey, live = live,
                        ghostAt = { pos -> ghostAt(sheetKey, pos) },
                        onPocket = { if (live) gestures.tap(it.position) },
                        onPocketHold = { if (live && canEdit) selected = it },
                        drag = if (live) drag else null,
                        gestures = if (live) gestures else null,
                    )
                }
                }
            }
            Spacer(Modifier.height(BelowPage))
        }
        }

        // How full the page is, just above the page navigation (under the binder).
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildList {
                        add("$identified of ${spreadShown.size} filled")
                        if (empty > 0) add("$empty empty")
                        if (toDo > 0) add("$toDo to identify")
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                    modifier = Modifier.weight(1f),
                )
                // The expansion most of this page's cards come from.
                pageExpansion(spreadShown)?.let { exp ->
                    accentFor(exp.seriesId)?.let { accent ->
                        if (exp.setName.isNotBlank()) ExpansionBadge(exp.setName, accent, Modifier.padding(start = 8.dp).widthIn(max = 180.dp))
                    }
                }
            }
            HoloBar(if (spreadShown.isEmpty()) 0f else identified.toFloat() / spreadShown.size)
            // Automatic card reading: "Reading the cards…", then what it found.
            readingStatus?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.AccentText, fontWeight = FontWeight.Medium)
            }
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
                canPrev = canPrevTurn,
                canNext = canNextTurn,
                open = open,
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
                // The grey missing card shown here: one tap if that's the card you're putting in.
                if (pocket.cardId == null) ghostAt(pageId, pocket.position)?.let { ghost ->
                    SheetButton("Put #${ghost.number} ${ghost.name} here") { selected = null; onPickSuggestion(pocket.position, ghost.id) }
                }
                SheetButton("Scan this card") { selected = null; onScanCard(pocket.position) }
                SheetButton(if (pocket.cardId == null) "Choose card from list" else "Change card") { selected = null; onChooseCard(pocket.position) }
                if (pocket.cardId != null) SheetButton("View card and price") { selected = null; onViewCard(pageId, pocket.position) }
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
                    onGlossary = onOpenGlossary?.let { open -> { jumping = false; open() } },
                )
            }
        }
    }
    if (showTips) {
        AlertDialog(
            onDismissRequest = { showTips = false },
            title = { Text("Tips") },
            text = {
                Text(
                    when {
                        open && canEdit -> "Swipe a page across the rings to turn it. Tap a card to see it, or an empty pocket to scan a card into it. Grey cards are ones you're still missing: tap one to see it and put it in. Hold a card until it lifts, then drag it to another pocket, on either page."
                        open -> "Swipe a page across the rings to turn it. Tap a card to see it and its price. This binder is view only."
                        canEdit -> "Swipe the page sideways to turn it. Tap a card to see it, or an empty pocket to scan a card into it. Grey cards are ones you're still missing: tap one to see it and put it in. Hold a card until it lifts, then drag it to another pocket. Hold and let go for more options."
                        else -> "Swipe the page sideways to turn it. Tap a card to see it and its price. This binder is view only."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { showTips = false }) { Text("Got it") } },
        )
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
    // The missing card shown greyed out in a free pocket, by pocket position.
    ghostAt: (Int) -> Card?,
    onPocket: (PocketWithCard) -> Unit,
    onPocketHold: (PocketWithCard) -> Unit,
    // Only for the page you're on: holding and dragging cards.
    drag: PocketDrag? = null,
    gestures: PocketGestures? = null,
    // The left page of an open binder: ring holes down its right edge instead.
    mirrored: Boolean = false,
    // The other page of an open binder: a tap or hold here makes it the page you're on.
    onOtherTap: ((PocketWithCard) -> Unit)? = null,
    onOtherHold: ((PocketWithCard) -> Unit)? = null,
    // The other page of an open binder: the card being dragged on the page you're on can be dropped here.
    peer: PocketDrag? = null,
) {
    val shape = if (mirrored) {
        RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp)
    }
    // A faint pattern from the page's expansion behind the pockets.
    val accent = accentFor(pageExpansion(pockets)?.seriesId)
    // Shiny cards tilt with the phone, so listen to its motion only when there are some.
    val tilt = rememberPhoneTilt(enabled = live && pockets.any { it.cardId != null && isShinyRarity(it.rarity) })
    Column(
        Modifier.fillMaxWidth()
            .then(
                if (live && drag != null && gestures != null) {
                    Modifier.onGloballyPositioned { drag.sheet = it }.pointerInput(gestures) { detectPocketGestures(gestures) }
                } else Modifier,
            )
            .clip(shape).background(AppColors.Surface)
            .expansionPattern(accent)
            .border(1.dp, AppColors.Border, shape)
            .drawBehind {
                // Punched holes for the rings.
                val x = if (mirrored) size.width - 12.dp.toPx() else 12.dp.toPx()
                RingSpots.forEach { f -> drawCircle(AppColors.Paper, radius = 4.5.dp.toPx(), center = Offset(x, size.height * f)) }
            }
            .padding(start = if (mirrored) 10.dp else 26.dp, end = if (mirrored) 26.dp else 10.dp, top = 10.dp, bottom = 10.dp),
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
                        val hovered = (drag != null && drag.hover == k && origin != null) ||
                            (peer != null && peer.hoverOther == (pageKey to k))
                        val shift = if (hovered && origin != null) {
                            val from = drag?.cardRects?.get(origin)
                            val to = drag?.cardRects?.get(k)
                            if (from != null && to != null) (from.center - to.center) * 0.22f else Offset.Zero
                        } else Offset.Zero
                        PocketTile(
                            pocket = pocket,
                            ghost = if (pocket.cardId == null) ghostAt(k) else null,
                            pageKey = pageKey,
                            live = live,
                            tilt = tilt,
                            order = rowIndex * columns + i,
                            onTapped = { onPocket(pocket) },
                            onHeld = { onPocketHold(pocket) },
                            modifier = Modifier.weight(1f).then(
                                if (!live && (onOtherTap != null || onOtherHold != null)) {
                                    Modifier.pointerInput(pocket) {
                                        detectTapGestures(
                                            onTap = { onOtherTap?.invoke(pocket) },
                                            onLongPress = { onOtherHold?.invoke(pocket) },
                                        )
                                    }
                                } else Modifier,
                            ),
                            pressed = drag?.pressed == k,
                            lifted = drag?.lifted == k,
                            hole = origin == k,
                            hidden = drag?.hidden == k,
                            target = if (hovered) (if (pocket.cardId != null) DropKind.Swap else DropKind.Here) else null,
                            shift = shift,
                            arrival = drag?.arrivals?.get(k) ?: (drag ?: peer)?.otherArrivals?.get(pageKey to k),
                            onArrived = { id ->
                                drag?.let { d -> if (d.arrivals[k]?.id == id) d.arrivals.remove(k) }
                                drag?.let { d -> if (d.otherArrivals[pageKey to k]?.id == id) d.otherArrivals.remove(pageKey to k) }
                                peer?.let { d -> if (d.otherArrivals[pageKey to k]?.id == id) d.otherArrivals.remove(pageKey to k) }
                            },
                            onCardPlaced = { c ->
                                drag?.let { d -> d.rectOf(c)?.let { d.cardRects[k] = it } }
                                peer?.let { d -> d.rectOf(c)?.let { d.otherCards[pageKey to k] = it } }
                            },
                            onTilePlaced = { c ->
                                drag?.let { d -> d.rectOf(c)?.let { d.tileRects[k] = it } }
                                peer?.let { d -> d.rectOf(c)?.let { d.otherTiles[pageKey to k] = it } }
                            },
                        )
                    }
                    // Keep tiles the same width on a short last row.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The space after the last page of an open binder, with a button to add a page. */
@Composable
private fun EndOfBinder(onAddPage: (() -> Unit)?) {
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp)
    val dash = AppColors.BorderStrong
    Box(
        Modifier.fillMaxSize().clip(shape)
            .background(AppColors.Surface.copy(alpha = 0.5f))
            .drawBehind {
                drawRoundRect(
                    dash,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .then(if (onAddPage != null) Modifier.clickable(onClickLabel = "Add a page", onClick = onAddPage) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onAddPage != null) {
                Text("+", fontSize = 32.sp, color = AppColors.AccentText, fontWeight = FontWeight.Medium)
                Text("Add a page", style = MaterialTheme.typography.titleMedium, color = AppColors.Muted)
            } else {
                Text("End of binder", style = MaterialTheme.typography.titleMedium, color = AppColors.Muted)
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
    // Open binder: one dot for each pair of pages.
    open: Boolean = false,
) {
    val dotCount = if (open) (pageCount + 1) / 2 else pageCount
    val current = if (open) (pageNumber - 1) / 2 else pageNumber - 1
    val label = if (open) {
        val first = current * 2 + 1
        if (first + 1 <= pageCount) "Pages $first–${first + 1} of $pageCount" else "Page $first of $pageCount"
    } else {
        "Page $pageNumber of $pageCount"
    }
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
                Text(label, style = MaterialTheme.typography.titleMedium, color = AppColors.Ink)
                if (onJump != null) Text("  ▾", fontSize = 11.sp, color = AppColors.Muted)
            }
            if (dotCount <= 12) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(dotCount) { i ->
                        val on = i == current
                        val w by animateDpAsState(if (on) 18.dp else 6.dp, tween(Motion.Short, easing = Motion.Ease), label = "dot")
                        val pop = remember { Animatable(1f) }
                        LaunchedEffect(ping) {
                            if (ping != null && (if (open) ping.first / 2 else ping.first) == i) {
                                pop.snapTo(2.4f)
                                pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                        Box(
                            Modifier.onGloballyPositioned {
                                if (open) {
                                    onPlaced(i * 2, it)
                                    onPlaced(i * 2 + 1, it)
                                } else {
                                    onPlaced(i, it)
                                }
                            }
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
    // A missing card to show greyed out, when the pocket has no card.
    ghost: Card? = null,
    pageKey: String,
    live: Boolean,
    order: Int,
    onTapped: () -> Unit,
    // The phone's tilt, for the shine on shiny cards.
    tilt: State<Offset>? = null,
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
        ghost != null -> ghost.name
        pocket.isEmpty -> "Empty pocket"
        else -> "Tap to scan"
    }
    // Flying to or from the card screen: lifted up out of the sleeve (see CardFlight).
    val flightKey = CardFlight.pocketKey(pageKey, pocket.position)
    val rise by animateFloatAsState(
        if ((pocket.cardId != null || ghost != null) && CardFlight.lifting == flightKey) 1f else 0f,
        tween(CardFlight.LiftMillis, easing = Motion.Ease),
        label = "rise",
    )
    // Cards pop into their pockets one after another when the page opens
    // (not when you come back from the card screen: then they're simply there).
    // (Nor when the 3D binder has just opened onto the page: its cards are already in place.)
    val popIn = arrive(
        play = live && !CardFlight.closing && !com.hybrid87sg.bindertracker.ui.three.PageLanding.arriving,
        delayMillis = 80 + order * 45,
        durationMillis = 340,
    )

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
        if (((pressed || lifted) && live) || target != null) 1f else 0f,
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
                onClick(label = if (pocket.cardId != null || ghost != null) "View card" else "Scan a card") { onTapped(); true }
                onLongClick(label = "More options") { onHeld(); true }
            },
    ) {
        Column {
            val shape = RoundedCornerShape(6.dp)
            val cardShadow = AppColors.Shadow
            val outline = AppColors.BorderStrong
            val holeFill = AppColors.Surface2
            Box(
                // The clear plastic sleeve the card sits in.
                Modifier.pocketSleeve().padding(3.dp).drawBehind {
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
                            translationY = t.y - lift * 4.dp.toPx() - rise * size.height * 0.34f
                            val k = scale * arriveScale.value * (1f + 0.04f * rise)
                            scaleX = k; scaleY = k
                            rotationZ = if (target == DropKind.Swap) -3f else 0f
                            rotationY = (1f - flip.value) * 90f
                            cameraDistance = 12f * density
                            alpha = if (hole || hidden) 0f else 1f
                            // A soft shadow lifts every card off the page (more while it's held up).
                            val resting = if ((pocket.cardId != null || (pocket.isEmpty && ghost == null)) && !hole && !hidden) 3.dp.toPx() else 0f
                            shadowElevation = resting + lift * 14.dp.toPx()
                            ambientShadowColor = cardShadow
                            spotShadowColor = cardShadow
                            this.shape = shape
                        }
                        .then(
                            when {
                                pocket.cardId != null && live && tilt != null && isShinyRarity(pocket.rarity) -> Modifier.holoTilt(tilt)
                                pocket.cardId != null && live -> Modifier.holoTouch()
                                else -> Modifier
                            },
                        )
                        .then(if (holdGlow > 0f) Modifier.border(3.dp, AppColors.AccentText.copy(alpha = holdGlow), shape) else Modifier)
                        .clip(shape)
                        .then(
                            when {
                                ghost != null -> Modifier
                                pocket.isEmpty -> Modifier
                                pocket.cardId == null -> Modifier.border(2.dp, CheckBorder, shape)
                                else -> Modifier.border(1.dp, AppColors.Border, shape)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        // A card you're missing from the binder's expansions: its picture greyed out
                        // (the Medium fade, as on the Missing cards page). Saved on the phone for offline use.
                        ghost != null -> CardImage(
                            ghost.thumbnailUrl,
                            contentDescription = "${ghost.name}, missing",
                            modifier = Modifier.fillMaxSize().cardFlight(flightKey),
                            backupUrl = ghost.thumbnailBackupUrl,
                            cardId = ghost.id,
                            grey = true,
                            noPicture = { CardBack(Modifier.fillMaxSize()) },
                        )
                        // Our own card-back picture, so an empty pocket looks like a card turned over.
                        pocket.isEmpty -> CardBack(Modifier.fillMaxSize())
                        // Identified: the official card picture from TCGdex, not the photo.
                        pocket.cardId != null -> CardImage(pocket.imageUrl?.let { "$it/low.webp" }, contentDescription = pocket.cardName, modifier = Modifier.fillMaxSize().cardFlight(flightKey), backupUrl = CardPictures.backupUrl(pocket.cardId, large = false), cardId = pocket.cardId)
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
            // The labels line up with the card's left edge (the card sits 3 in from the sleeve's edge).
            // Their lines are snug to the text (the default line is paragraph-sized and wasted height).
            Spacer(Modifier.height(LabelGap))
            Text(
                label,
                fontSize = 13.sp,
                lineHeight = NameLine,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    ghost != null -> AppColors.Muted
                    pocket.cardId == null && !pocket.isEmpty -> AppColors.Black
                    else -> AppColors.Ink
                },
                modifier = if (pocket.cardId == null && !pocket.isEmpty && ghost == null) {
                    Modifier.clip(RoundedCornerShape(6.dp)).background(CheckFill).padding(horizontal = 3.dp, vertical = 2.dp)
                } else Modifier.padding(horizontal = LabelInset, vertical = 2.dp),
            )
            // Just the card number under the name (the expansion is in card view).
            (pocket.cardNumber ?: ghost?.number)?.let { raw ->
                val number = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
                Spacer(Modifier.height(NumberGap))
                Text(
                    "#$number",
                    fontSize = 12.sp,
                    lineHeight = NumberLine,
                    color = AppColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = LabelInset),
                )
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

// The binder's cover round a folded page: a little cover shows on three sides,
// and a wider strip down the ring side, where the rings sit.
private val CoverEdge = 5.dp
private val CoverRingSide = FoldedSpine
// Under each card: the gap, the name's line, the gap, the number's line. Keep these
// in step with [pocketTextHeight], which sizes the page so it fits the screen.
private val LabelGap = 6.dp
private val NameLine = 17.sp
private val NumberGap = 1.dp
private val NumberLine = 15.sp
private val LabelInset = 3.dp
// Space between the binder and the "7 of 9 filled" line.
private val BelowPage = 8.dp

/**
 * The binder's cover, drawn behind a folded page: the binder's colour, with the
 * left edge drawn like the middle of the open two-page binder (its spine), as if
 * the left page is folded away behind. The rings come in from that edge
 * (see drawRings in PageTurn.kt).
 */
private fun Modifier.binderCover(color: Color): Modifier = drawBehind {
    val big = CornerRadius(14.dp.toPx())
    val small = CornerRadius(4.dp.toPx())
    val shape = Path().apply {
        addRoundRect(RoundRect(Rect(Offset.Zero, size), topLeft = small, topRight = big, bottomRight = big, bottomLeft = small))
    }
    drawPath(shape, color)
    val spine = CoverRingSide.toPx()
    // The right half of the open binder's spine: lighter at the fold, darker next to the page.
    clipPath(shape) {
        drawRect(
            Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0.08f),
                0.6f to Color.Black.copy(alpha = 0.3f),
                1f to Color.Transparent,
                startX = 0f,
                endX = spine + 8.dp.toPx(),
            ),
            size = Size(spine + 8.dp.toPx(), size.height),
        )
        drawRect(Color.Black.copy(alpha = 0.35f), size = Size(1.5.dp.toPx(), size.height))
    }
}

/** A small round "?" button that shows the page's tips. */
@Composable
private fun TipsButton(onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Show tips", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).border(1.5.dp, AppColors.Muted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.Muted)
        }
    }
}

/**
 * The height of the text under each card: the gap, the name (with room for its
 * "to identify" highlight), the gap and the "#25" line. Measured, so a bigger
 * font size on the phone is allowed for.
 */
@Composable
private fun pocketTextHeight(): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current
    return remember(density, base) {
        fun lineHeight(style: TextStyle) = with(density) {
            measurer.measure("Ag", base.merge(style), maxLines = 1).size.height.toDp()
        }
        LabelGap +
            lineHeight(TextStyle(fontSize = 13.sp, lineHeight = NameLine, fontWeight = FontWeight.SemiBold)) + 4.dp +
            NumberGap + lineHeight(TextStyle(fontSize = 12.sp, lineHeight = NumberLine))
    }
}

/**
 * How wide the folded binder (cover and page) can be so the whole page fits in
 * [maxHeight] without scrolling. Each pocket is a card picture (its height goes
 * with its width) plus two lines of text (a fixed height), so we work out the
 * card size that fills the height, and from that the width. It always allows for
 * both text lines in every pocket, so the size doesn't jump about as cards get
 * identified, and every page with the same number of pockets is the same size.
 * Keep the numbers here in step with [BinderSheet] and [PocketTile].
 */
@Composable
private fun foldedCoverWidth(maxWidth: Dp, maxHeight: Dp, rows: Int, columns: Int): Dp {
    val textHeight = pocketTextHeight()
    if (rows <= 0 || maxHeight == Dp.Infinity) return maxWidth
    // Per pocket, besides the card: sleeve (3 + 3), then the text under it.
    val pocketExtra = 6.dp + textHeight
    // Sheet padding (10 top and bottom), 10 between rows, and the cover round it.
    val fixed = 20.dp + 10.dp * (rows - 1) + pocketExtra * rows + CoverEdge * 2 + BelowPage + 4.dp
    val cardHeight = (maxHeight - fixed) / rows
    // Never shrink cards below a usable size; a tiny screen scrolls instead.
    val cardWidth = (cardHeight * CARD_ASPECT).coerceAtLeast(56.dp)
    val pocket = cardWidth + 6.dp
    // 8 between pockets, sheet padding 26 + 10, cover round it.
    val width = pocket * columns + 8.dp * (columns - 1) + 36.dp + CoverRingSide + CoverEdge
    return width.coerceAtMost(maxWidth)
}

/**
 * How wide the open two-page binder can be so both pages fit in [maxHeight]
 * without scrolling. Like [foldedCoverWidth]: before, the spread always filled
 * the screen's width, so on a tall binder its bottom row slipped off the screen.
 * Keep the numbers in step with [SpreadTurnStage] and [BinderSheet].
 */
@Composable
private fun spreadWidth(maxWidth: Dp, maxHeight: Dp, rows: Int, columns: Int): Dp {
    val textHeight = pocketTextHeight()
    if (rows <= 0 || maxHeight == Dp.Infinity) return maxWidth
    val pocketExtra = 6.dp + textHeight
    // The cover's 6 round the pages, sheet padding 10 top and bottom, 10 between rows.
    val fixed = 12.dp + 20.dp + 10.dp * (rows - 1) + pocketExtra * rows + BelowPage + 4.dp
    val cardWidth = ((maxHeight - fixed) / rows * CARD_ASPECT).coerceAtLeast(56.dp)
    val sheet = (cardWidth + 6.dp) * columns + 8.dp * (columns - 1) + 36.dp
    val width = sheet * 2 + SpineGap + 12.dp
    return width.coerceAtMost(maxWidth)
}
