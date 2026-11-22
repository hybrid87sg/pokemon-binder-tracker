package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * One binder page, shown as its pockets. Tap a pocket to say which
 * card it holds, or that it's empty.
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
    // Called once a page turn finishes: 1 = went to the next page, -1 = back a page.
    onTurn: (Int) -> Unit = {},
) {
    val turnState = rememberPageTurnState()
    var selected by remember { mutableStateOf<PocketWithCard?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(openPosition, pockets) {
        val pocket = openPosition?.let { p -> pockets.firstOrNull { it.position == p } } ?: return@LaunchedEffect
        selected = pocket
        onPocketOpened()
    }
    val identified = pockets.count { it.cardId != null }
    val empty = pockets.count { it.isEmpty }
    val toDo = pockets.size - identified - empty

    Column(Modifier.fillMaxSize().background(AppColors.Paper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(binderName, onBack)
            Spacer(Modifier.weight(1f))
            // Deleting the page lives behind Edit, with a Done button to leave.
            if (!editing) {
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
                modifier = Modifier.fillMaxWidth(),
            ) { which ->
                when (which) {
                    0 -> BinderSheet(pockets, columns, pageKey = pageId, live = true, onPocket = { selected = it })
                    -1 -> BinderSheet(prevPockets.orEmpty(), columns, pageKey = "$pageId-prev", live = false, onPocket = {})
                    else -> BinderSheet(nextPockets.orEmpty(), columns, pageKey = "$pageId-next", live = false, onPocket = {})
                }
            }
            Text(
                "Tap a pocket to scan its card, or to choose it from the list.",
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
            )
        } else {
            // Keep the pockets clear of the phone's own navigation bar.
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    selected?.let { pocket ->
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = AppColors.Paper) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                CardImage(card.thumbnailUrl, contentDescription = "${card.name}, ${card.setName} ${card.number}", modifier = Modifier.fillMaxWidth())
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
                if (!pocket.isEmpty) SheetButton("Mark as empty pocket") { selected = null; onMarkEmpty(pocket.position) }
                if (pocket.cardId != null || pocket.isEmpty) SheetButton("Clear pocket") { selected = null; onClear(pocket.position) }
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
) {
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(AppColors.Surface)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { i, pocket ->
                        PocketTile(
                            pocket = pocket,
                            pageKey = pageKey,
                            live = live,
                            order = rowIndex * columns + i,
                            onClick = { onPocket(pocket) },
                            modifier = Modifier.weight(1f),
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
private fun PageArrows(pageNumber: Int, pageCount: Int, canPrev: Boolean, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton(R.drawable.ic_chevron_left, "Previous page", canPrev, onPrev)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Page $pageNumber of $pageCount", style = MaterialTheme.typography.titleMedium, color = AppColors.Ink)
            if (pageCount <= 12) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(pageCount) { i ->
                        val on = i == pageNumber - 1
                        val w by animateDpAsState(if (on) 18.dp else 6.dp, tween(Motion.Short, easing = Motion.Ease), label = "dot")
                        Box(Modifier.width(w).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (on) AppColors.AccentText else AppColors.BorderStrong))
                    }
                }
            }
        }
        ArrowButton(R.drawable.ic_chevron_right, "Next page", canNext, onNext)
    }
}

@Composable
private fun ArrowButton(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = press,
        shape = CircleShape,
        color = AppColors.Surface,
        contentColor = if (enabled) AppColors.Ink else AppColors.BorderStrong,
        border = BorderStroke(1.dp, AppColors.BorderStrong),
        modifier = Modifier.size(48.dp).pressSquish(press).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PocketTile(
    pocket: PocketWithCard,
    pageKey: String,
    live: Boolean,
    order: Int,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val label = when {
        pocket.cardName != null -> pocket.cardName
        pocket.isEmpty -> "Empty pocket"
        else -> "Tap to identify"
    }
    // Cards pop into their pockets one after another when the page opens.
    val popIn = arrive(play = live, delayMillis = 80 + order * 45, durationMillis = 340)

    // When a card gets identified while you watch (or while you were on the
    // camera screen), it flips over into place with a little sparkle.
    var seenCard by rememberSaveable(pageKey, pocket.position) { mutableStateOf(pocket.cardId) }
    val flip = remember { Animatable(1f) }
    val sparkle = remember { Animatable(1f) }
    LaunchedEffect(pocket.cardId) {
        if (live && seenCard == null && pocket.cardId != null) {
            flip.snapTo(0f)
            sparkle.snapTo(0f)
            launch { flip.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
            sparkle.animateTo(1f, tween(750, easing = Motion.Ease))
        }
        seenCard = pocket.cardId
    }

    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = press,
        color = Color.Transparent,
        modifier = modifier
            .graphicsLayer {
                alpha = popIn
                val s = 0.82f + 0.18f * popIn
                scaleX = s; scaleY = s
                translationY = (1f - popIn) * 10.dp.toPx()
            }
            .pressSquish(press, 0.96f),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val shape = RoundedCornerShape(6.dp)
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(CARD_ASPECT)
                        .graphicsLayer {
                            rotationY = (1f - flip.value) * 90f
                            cameraDistance = 12f * density
                        }
                        .then(if (pocket.cardId != null && live) Modifier.holoTouch() else Modifier)
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
                        pocket.cardId != null -> CardImage(pocket.imageUrl?.let { "$it/low.webp" }, contentDescription = pocket.cardName, modifier = Modifier.fillMaxSize())
                        else -> Text("?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CheckBorder)
                    }
                }
                if (sparkle.value < 1f) Sparkles(sparkle.value, Modifier.matchParentSize())
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
