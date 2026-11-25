package com.hybrid87sg.bindertracker.ui.screens

import com.hybrid87sg.bindertracker.ui.theme.appBackground
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.data.Checklist
import com.hybrid87sg.bindertracker.data.ChecklistData
import com.hybrid87sg.bindertracker.prices.SgdRates
import java.util.Locale
import com.hybrid87sg.bindertracker.tcgdex.CardDetails
import com.hybrid87sg.bindertracker.tcgdex.MarketPrice
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.CardImage
import com.hybrid87sg.bindertracker.ui.components.holoTilt
import com.hybrid87sg.bindertracker.ui.components.isShinyRarity
import com.hybrid87sg.bindertracker.ui.components.rememberPhoneTilt
import com.hybrid87sg.bindertracker.ui.components.rememberTouchTilt
import com.hybrid87sg.bindertracker.ui.components.touchTilt
import com.hybrid87sg.bindertracker.ui.components.Motion
import com.hybrid87sg.bindertracker.ui.components.PrimaryButton
import com.hybrid87sg.bindertracker.ui.components.cardFlight
import com.hybrid87sg.bindertracker.ui.components.CardFlight
import com.hybrid87sg.bindertracker.ui.components.CardBack
import androidx.compose.runtime.derivedStateOf
import com.hybrid87sg.bindertracker.ui.components.formatDate
import com.hybrid87sg.bindertracker.ui.components.formatMoney
import com.hybrid87sg.bindertracker.ui.theme.AppColors

/**
 * The cards you can swipe through on the card screen, in order: a binder's
 * cards in pocket order, the cards on the Missing cards page, or search results.
 */
data class CardBrowse(
    val items: List<BrowseItem>,
    // The card you tapped.
    val start: Int = 0,
    // Shown under the card, e.g. "in Kanto Favourites".
    val listName: String? = null,
    // The Missing cards page of this binder, for its "Put it in" box.
    val checklistBinderId: String? = null,
)

data class BrowseItem(
    val cardId: String,
    // Which pocket or tile the card flies from and back to (see CardFlight).
    val flightKey: String? = null,
    // Where it is, e.g. "Page 1, pocket 5".
    val where: String? = null,
    // The page it's on, so going back turns the binder to that page.
    val pageId: String? = null,
)

/** Where a card stands on the Missing cards page (null when it wasn't opened from there). */
data class MissingStatus(
    val owned: Boolean,
    // For a card you have: "Page 2, pocket 4".
    val where: String? = null,
    // For a missing card: the pocket it should go in.
    val spot: Checklist.Spot? = null,
    val canEdit: Boolean = false,
    // The binder's page size, for the little page picture.
    val columns: Int = 3,
    val rows: Int = 3,
)

/**
 * The card screen: loads each card and its prices from TCGdex as you swipe,
 * with a retry if it fails.
 */
@Composable
fun CardRoute(
    browse: CardBrowse,
    loadCard: suspend (String) -> Card?,
    loadDetails: suspend (String) -> Result<CardDetails>,
    // Leaving, with the card showing at the time (so it flies back to its own pocket).
    onBack: (BrowseItem) -> Unit,
    // Today's rates for showing prices in Singapore dollars (null = couldn't get them).
    loadRates: suspend () -> SgdRates? = { null },
    // Opened from Missing cards: whether the card is in the binder, and where it goes.
    missingStatus: (Card) -> MissingStatus? = { null },
    onPutIn: (Card, Checklist.Spot) -> Unit = { _, _ -> },
    onScanInto: (Checklist.Spot) -> Unit = {},
) {
    val items = browse.items
    var index by rememberSaveable { mutableIntStateOf(browse.start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    val item = items.getOrNull(index) ?: return
    // Cards and prices already loaded, so swiping back is instant.
    val cards = remember { mutableStateMapOf<String, Card>() }
    val details = remember { mutableStateMapOf<String, CardDetails>() }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(index) {
        // This card, then its neighbours, ready for the next swipe.
        listOf(index, index + 1, index - 1).mapNotNull { items.getOrNull(it)?.cardId }.forEach { id ->
            if (id !in cards) loadCard(id)?.let { cards[id] = it }
        }
    }
    LaunchedEffect(item.cardId, attempt) {
        failed = false
        if (item.cardId !in details) {
            loadDetails(item.cardId).onSuccess { details[item.cardId] = it }.onFailure { failed = true }
        }
    }
    var rates by remember { mutableStateOf<SgdRates?>(null) }
    var ratesLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        rates = runCatching { loadRates() }.getOrNull()
        ratesLoaded = true
    }
    val card = cards[item.cardId] ?: return
    BackHandler { onBack(item) }
    CardScreen(
        card = card,
        details = details[card.id],
        failed = failed,
        onRetry = { attempt++ },
        onBack = { onBack(item) },
        rates = rates,
        ratesLoaded = ratesLoaded,
        flightKey = item.flightKey,
        position = if (items.size > 1) CardPosition(index, items.size, browse.listName, item.where) else null,
        onMove = { step -> (index + step).takeIf { it in items.indices }?.let { index = it; true } ?: false },
        missing = missingStatus(card),
        onPutIn = { spot -> onPutIn(card, spot) },
        onScanInto = onScanInto,
    )
}

/** Is [card] in the binder, and if not, which pocket it should go in (for the card screen). */
fun missingStatus(data: ChecklistData, entries: List<Checklist.Entry>, card: Card): MissingStatus {
    val binder = data.binder
    val pageNumbers = data.pockets.map { it.pageId }.distinct().withIndex().associate { it.value to it.index + 1 }
    return if (card.id in data.owned) {
        val at = data.pockets.firstOrNull { it.cardId == card.id }
        MissingStatus(
            owned = true,
            where = at?.let { "Page ${pageNumbers[it.pageId]}, pocket ${it.position + 1}" },
            columns = binder.columns,
            rows = binder.rows,
        )
    } else {
        MissingStatus(
            owned = false,
            spot = Checklist.suggestSpot(card, entries, data.pockets),
            canEdit = binder.canEdit,
            columns = binder.columns,
            rows = binder.rows,
        )
    }
}

/** "4 of 7 in Kanto Favourites · Page 1, pocket 5". */
data class CardPosition(val index: Int, val count: Int, val listName: String?, val where: String?)

@Composable
fun CardScreen(
    card: Card,
    details: CardDetails?,
    failed: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    rates: SgdRates? = null,
    // False while the rates are still being fetched.
    ratesLoaded: Boolean = true,
    flightKey: String? = null,
    // Where this card is in the list you can swipe through (null = just this card).
    position: CardPosition? = null,
    // Go to the next (1) or previous (-1) card. False at either end.
    onMove: (Int) -> Boolean = { false },
    missing: MissingStatus? = null,
    onPutIn: (Checklist.Spot) -> Unit = {},
    onScanInto: (Checklist.Spot) -> Unit = {},
) {
    var fullScreen by remember { mutableStateOf(false) }
    val config = LocalConfiguration.current
    // Unfolded (the Find N3's inner screen, or a tablet): card on the left, details on the right.
    // Same test as the two-page binder in PageScreen.
    val open = config.screenWidthDp >= 600 && config.screenHeightDp >= 480
    val scope = rememberCoroutineScope()
    // Swiping: the card follows your finger, then the next one slides in.
    val dragX = remember { Animatable(0f) }
    val enter = remember { Animatable(1f) }
    var enterFrom by remember { mutableFloatStateOf(0f) }
    val canPrev = position != null && position.index > 0
    val canNext = position != null && position.index < position.count - 1
    fun move(step: Int) {
        if (!onMove(step)) return
        scope.launch {
            dragX.snapTo(0f)
            enterFrom = step.toFloat()
            enter.snapTo(0f)
            enter.animateTo(1f, tween(260, easing = Motion.Ease))
        }
    }
    val slideIn = Modifier.graphicsLayer {
        translationX = (1f - enter.value) * enterFrom * 40.dp.toPx()
        alpha = enter.value
    }
    val cardMotion = Modifier.graphicsLayer {
        translationX = dragX.value + (1f - enter.value) * enterFrom * 40.dp.toPx()
        rotationZ = dragX.value / (60f * density)
        alpha = enter.value
    }
    val swipe = if (position == null) Modifier else Modifier.pointerInput(position.index, position.count) {
        val threshold = 60.dp.toPx()
        detectHorizontalDragGestures(
            onDragEnd = {
                val x = dragX.value
                if (abs(x) > threshold && ((x < 0 && canNext) || (x > 0 && canPrev))) move(if (x < 0) 1 else -1)
                else scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
            },
            onDragCancel = { scope.launch { dragX.animateTo(0f) } },
        ) { change, amount ->
            change.consume()
            val atEnd = (dragX.value + amount > 0 && !canPrev) || (dragX.value + amount < 0 && !canNext)
            scope.launch { dragX.snapTo(dragX.value + if (atEnd) amount * 0.3f else amount) }
        }
    }

    // Opened from Missing cards: the card turns round once in 3D as it flies in.
    // Only the first card; swiping to the next one doesn't spin.
    val spin = remember { Animatable(if (CardFlight.spins(flightKey) && !CardFlight.closing) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (spin.value < 1f) spin.animateTo(1f, tween(CardFlight.SpinMillis, easing = CardFlight.SpinEasing))
    }
    // Halfway round you see the card's back.
    val showingBack by remember { derivedStateOf { (spin.value * 360f).let { it > 90f && it < 270f } } }
    // Touch the card and drag to tilt it (up to 25°), once any spin-in is over.
    // Shiny cards also get the holo rainbow, following your finger.
    val tilt = rememberTouchTilt(card.id)
    val tiltState = tilt.asState()
    val tiltReady by remember { derivedStateOf { spin.value >= 1f } }
    val shiny = isShinyRarity(card.rarity.ifBlank { details?.rarity })
    val picture = @Composable { modifier: Modifier ->
        Box(
            modifier.cardFlight(flightKey).graphicsLayer {
                rotationY = spin.value * 360f
                // Far enough away that the turn looks natural, not stretched.
                cameraDistance = 16f * density
            }.touchTilt(tilt, scope, enabled = tiltReady),
        ) {
            CardImage(
                card.largeImageUrl,
                contentDescription = "${card.name} card",
                modifier = Modifier.fillMaxWidth()
                    .then(
                        if (tiltReady) {
                            Modifier.holoTilt(tiltState, maxDegrees = TOUCH_TILT_DEGREES, scale = 1f, shape = RoundedCornerShape(10.dp), shine = shiny)
                        } else {
                            Modifier
                        },
                    )
                    .graphicsLayer { alpha = if (showingBack) 0f else 1f }
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Show the card full screen") { fullScreen = true },
                corner = 10.dp,
                backupUrl = card.largeBackupUrl,
                cardId = card.id,
                // Missing cards are grey in lists and pockets, but here, in the
                // card view, you see them in full colour.
            )
            if (showingBack) {
                // Seen from behind the picture would be mirrored, so flip it back.
                CardBack(Modifier.matchParentSize().graphicsLayer { scaleX = -1f }.clip(RoundedCornerShape(10.dp)))
            }
            if (spin.value < 1f) SpinShine(spin.value, Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)))
        }
    }
    val info = @Composable {
        CardInfo(card, details, failed, onRetry, rates, ratesLoaded, missing, onPutIn, onScanInto, slideIn)
    }

    Column(Modifier.fillMaxSize().appBackground().statusBarsPadding().then(swipe)) {
        Box(Modifier.padding(start = 8.dp, top = 12.dp)) { BackButton("Back", onBack) }
        if (open) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // Left half: the card, as big as fits, standing still while the details scroll.
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp, end = 20.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val side = 46.dp
                        val cardWidth = min(maxWidth - side * 2, maxHeight * (63f / 88f))
                        picture(Modifier.width(cardWidth).then(cardMotion))
                        if (position != null) {
                            ArrowButton("Previous card", forward = false, enabled = canPrev, modifier = Modifier.align(Alignment.CenterStart)) { move(-1) }
                            ArrowButton("Next card", forward = true, enabled = canNext, modifier = Modifier.align(Alignment.CenterEnd)) { move(1) }
                        }
                    }
                    position?.let { PositionLine(it) }
                }
                // Right half: name, set and prices. Only this side scrolls.
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(start = 28.dp, end = 24.dp, top = 4.dp, bottom = 28.dp),
                ) {
                    Box(Modifier.widthIn(max = 440.dp)) { info() }
                }
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (position != null) ArrowButton("Previous card", forward = false, enabled = canPrev) { move(-1) }
                        picture(Modifier.width(220.dp).then(cardMotion))
                        if (position != null) ArrowButton("Next card", forward = true, enabled = canNext) { move(1) }
                    }
                    position?.let { PositionLine(it) }
                }
                info()
            }
        }
    }
    if (fullScreen) FullScreenCard(card, fitHeight = open, shiny = shiny) { fullScreen = false }
}

/** A round arrow button beside the card. Faded at the first or last card. */
@Composable
private fun ArrowButton(label: String, forward: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(40.dp).graphicsLayer { alpha = if (enabled) 1f else 0.3f }
            .clip(CircleShape).background(AppColors.Surface).border(1.dp, AppColors.Border, CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (forward) "›" else "‹", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun PositionLine(p: CardPosition) {
    val tail = listOfNotNull(p.listName?.let { " $it" }, p.where?.let { " · $it" }).joinToString("")
    Text(
        "${p.index + 1} of ${p.count}$tail",
        style = MaterialTheme.typography.bodySmall,
        color = AppColors.Muted,
        textAlign = TextAlign.Center,
    )
}

/** Name, set, illustrator, the Missing cards box (if any) and the prices. */
@Composable
private fun CardInfo(
    card: Card,
    details: CardDetails?,
    failed: Boolean,
    onRetry: () -> Unit,
    rates: SgdRates?,
    ratesLoaded: Boolean,
    missing: MissingStatus?,
    onPutIn: (Checklist.Spot) -> Unit,
    onScanInto: (Checklist.Spot) -> Unit,
    slideIn: Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(slideIn, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.name, style = MaterialTheme.typography.headlineMedium)
            Text(
                listOfNotNull(card.setName, "#${card.number}", details?.rarity?.takeUnless { it.equals("None", ignoreCase = true) }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Muted,
            )
            details?.illustrator?.let {
                Text("Illustrated by $it", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
        }
        missing?.let { MissingBox(it, onPutIn, onScanInto) }


        Text("Market price", style = MaterialTheme.typography.titleLarge)
        when {
            failed -> Column {
                Text("Couldn't load prices. Check your internet connection.", style = MaterialTheme.typography.bodyMedium, color = AppColors.Danger)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
            details == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("  Loading prices…", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
            }
            details.prices.isEmpty() -> Text(
                "No market price is listed for this card yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Muted,
            )
            else -> {
                PriceTable(details.prices, rates)
                if (ratesLoaded) RateNote(details.prices, rates)
            }
        }
        Text(
            "Market prices are what the card typically sells for on each marketplace, not a guaranteed sale.",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.Muted,
        )
    }
}

/** Opened from Missing cards: is it in the binder, and if not, where it goes. */
@Composable
private fun MissingBox(status: MissingStatus, onPutIn: (Checklist.Spot) -> Unit, onScanInto: (Checklist.Spot) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val box = Modifier.fillMaxWidth().clip(shape).background(AppColors.Surface2).border(1.dp, AppColors.Border, shape).padding(12.dp)
    val spot = status.spot
    when {
        status.owned -> Column(box, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("In your binder", fontWeight = FontWeight.SemiBold, color = AppColors.SuccessText)
            status.where?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted) }
        }
        spot == null -> Text(
            "Not in your binder yet. Every pocket is full, so add a page first, then come back to put it in.",
            style = MaterialTheme.typography.bodyMedium, color = AppColors.Ink, modifier = box,
        )
        else -> Column(box, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniPage(status.columns, status.rows, spot.position)
                Column(Modifier.weight(1f)) {
                    Text("Not in your binder yet", fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
                    Text(
                        "Goes on page ${spot.pageNumber}, pocket ${spot.position + 1}" +
                            (spot.after?.let { ", right after #${it.number} ${it.name}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
                    )
                }
            }
            if (status.canEdit) {
                PrimaryButton(text = "Put it in", onClick = { onPutIn(spot) })
                OutlinedButton(
                    onClick = { onScanInto(spot) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AppColors.BorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
                ) { Text("Scan it in to check") }
            }
        }
    }
}

/** "US$1 = S$1.294" for one currency. */
private fun rateText(currency: String, sgd: Double): String {
    val one = when (currency) {
        "USD" -> "US$1"
        "EUR" -> "€1"
        else -> "1 $currency"
    }
    return one + " = S$" + "%.3f".format(Locale.UK, sgd)
}

/** Says which exchange rates the S$ prices use, or that we couldn't get any. */
@Composable
private fun RateNote(prices: List<MarketPrice>, rates: SgdRates?) {
    val currencies = prices.map { it.currency }.distinct().filter { it != "SGD" }
    if (currencies.isEmpty()) return
    val known = currencies.mapNotNull { c -> rates?.toSgd?.get(c)?.let { rateText(c, it) } }
    val text = if (rates == null || known.isEmpty()) {
        "Couldn't get today's exchange rate, so prices are in their original currency. Connect to the internet to see them in Singapore dollars."
    } else {
        val day = formatDate(rates.date + "T12:00:00Z")?.let { " ($it)" }.orEmpty()
        "Shown in Singapore dollars using the European Central Bank's rates" + day + ": " +
            known.joinToString(", ") + ". The small number is the original price."
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = AppColors.Muted,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Surface2).padding(12.dp),
    )
}

/**
 * The card on its own, as wide as the phone, on black.
 *
 * Not zoomed: tap anywhere, swipe down, press Back or ✕ to close.
 * Zoom: pinch (up to 5×) or double-tap (2.5× where you tapped, again to zoom out).
 * While zoomed: drag moves around the card, a single tap does nothing,
 * Back zooms out first, and ✕ always closes.
 */
@Composable
private fun FullScreenCard(card: Card, fitHeight: Boolean = false, shiny: Boolean = false, onClose: () -> Unit) {
    // Shiny cards: the holo rainbow and glare slide across the card as you
    // tilt the phone. The card itself stays flat, so zooming, moving around
    // and swiping down to close all work as before (and the shine works zoomed in).
    val phoneTilt = rememberPhoneTilt(enabled = shiny)
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    var anim by remember { mutableStateOf<Job?>(null) }

    // Keeps the card from being dragged off screen: you can only move as far as the zoomed card reaches.
    fun clamped(s: Float, p: Offset): Offset {
        val maxX = max(0f, (cardSize.width * s - boxSize.width) / 2f)
        val maxY = max(0f, (cardSize.height * s - boxSize.height) / 2f)
        return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
    }
    // The zoom and position that keep the spot under your finger (point) in place.
    fun zoomedAt(newScale: Float, point: Offset, s: Float = scale, p: Offset = pan): Pair<Float, Offset> {
        val target = newScale.coerceIn(1f, MAX_ZOOM)
        val fromCentre = point - Offset(boxSize.width / 2f, boxSize.height / 2f)
        return target to clamped(target, fromCentre - (fromCentre - p) * (target / s))
    }
    fun animateTo(targetScale: Float, targetPan: Offset, targetDragY: Float = 0f) {
        anim?.cancel()
        val s0 = scale; val p0 = pan; val d0 = dragY
        anim = scope.launch {
            animate(0f, 1f, animationSpec = tween(260, easing = FastOutSlowInEasing)) { t, _ ->
                scale = s0 + (targetScale - s0) * t
                pan = p0 + (targetPan - p0) * t
                dragY = d0 + (targetDragY - d0) * t
            }
        }
    }

    Dialog(
        // Back: zoom out first, close when not zoomed.
        onDismissRequest = { if (scale > 1.01f) animateTo(1f, Offset.Zero) else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val density = LocalDensity.current
        val closeAfter = with(density) { 120.dp.toPx() }
        Box(
            Modifier.fillMaxSize()
                .onSizeChanged { boxSize = it }
                .background(Color.Black.copy(alpha = (1f - dragY / (closeAfter * 3)).coerceIn(0.4f, 1f)))
                .semantics { onClick(label = "Close") { onClose(); true } }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scale <= 1.01f) onClose() },
                        onDoubleTap = { point ->
                            if (scale > 1.01f) animateTo(1f, Offset.Zero)
                            else zoomedAt(DOUBLE_TAP_ZOOM, point).let { (s, p) -> animateTo(s, p) }
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        anim?.cancel()
                        // What this gesture turned out to be: 0 = not decided / ignored, 1 = zoom or move, 2 = swipe down to close.
                        var mode = 0
                        var pastSlop = false
                        var moved = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            if (fingers == 0) break
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (!pastSlop) {
                                moved += panChange
                                if (fingers >= 2 || moved.getDistance() > viewConfiguration.touchSlop) {
                                    pastSlop = true
                                    mode = when {
                                        fingers >= 2 || scale > 1.01f -> 1
                                        moved.y > abs(moved.x) -> 2
                                        else -> 0
                                    }
                                }
                            }
                            if (fingers >= 2) mode = 1
                            if (!pastSlop) continue
                            when (mode) {
                                1 -> {
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val (s, p) = zoomedAt(scale * zoomChange, centroid, scale, pan + panChange)
                                    scale = s; pan = p
                                }
                                2 -> dragY = (dragY + panChange.y).coerceAtLeast(0f)
                            }
                            // Moving cancels the tap detector, so a drag never counts as a tap.
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        when {
                            mode == 2 && dragY > closeAfter -> onClose()
                            mode == 2 -> animateTo(scale, pan, 0f)
                            mode == 1 && scale <= 1.01f -> animateTo(1f, Offset.Zero)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            CardImage(
                card.largeImageUrl,
                contentDescription = "${card.name} card, full screen",
                modifier = Modifier
                    // Folded: as wide as the phone. Unfolded: as tall as the screen, so the whole card shows.
                    .then(if (fitHeight) Modifier.fillMaxHeight().padding(vertical = 20.dp) else Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                    .onSizeChanged { cardSize = it }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y + dragY
                    }
                    .then(if (shiny) Modifier.holoTilt(phoneTilt, maxDegrees = 0f, scale = 1f, shape = RoundedCornerShape(14.dp)) else Modifier),
                corner = 14.dp,
                backupUrl = card.largeBackupUrl,
                cardId = card.id,
            )
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                    .size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                    .clickable(onClickLabel = "Close", onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

private const val MAX_ZOOM = 5f
// The most the card view's card tilts under your finger, in any direction.
private const val TOUCH_TILT_DEGREES = 25f
private const val DOUBLE_TAP_ZOOM = 2.5f

@Composable
private fun PriceTable(prices: List<MarketPrice>, rates: SgdRates?) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(AppColors.Surface).border(1.dp, AppColors.Border, shape)) {
        prices.groupBy { it.source }.entries.forEachIndexed { index, (source, rows) ->
            if (index > 0) HorizontalDivider(color = AppColors.Border)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(source, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    formatDate(rows.first().updated)?.let {
                        Text("updated $it", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                }
                rows.forEach { price ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(price.variant, style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted, modifier = Modifier.weight(1f))
                        val sgd = rates?.convert(price.amount, price.currency)
                        if (sgd == null || price.currency == "SGD") {
                            Text(formatMoney(price.amount, price.currency), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        } else {
                            // S$ big, the original price small underneath.
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatMoney(sgd, "SGD"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(formatMoney(price.amount, price.currency), style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The light catching a spinning card: a bright band sweeping across, with a
 * faint holo rainbow, strongest when the card is side-on to you.
 */
@Composable
private fun SpinShine(progress: Float, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val turn = progress * 2f * kotlin.math.PI.toFloat()
        val strength = kotlin.math.abs(kotlin.math.sin(turn))
        if (strength < 0.02f) return@Canvas
        val x = size.width * (1.4f - 1.8f * ((progress * 2f) % 1f))
        drawRect(
            androidx.compose.ui.graphics.Brush.linearGradient(
                0f to androidx.compose.ui.graphics.Color.Transparent,
                0.5f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f * strength),
                1f to androidx.compose.ui.graphics.Color.Transparent,
                start = androidx.compose.ui.geometry.Offset(x - size.width * 0.35f, 0f),
                end = androidx.compose.ui.geometry.Offset(x + size.width * 0.35f, size.height),
            ),
        )
        drawRect(
            androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(
                    androidx.compose.ui.graphics.Color(0xFFFF7AB6), androidx.compose.ui.graphics.Color(0xFFFFD36E),
                    androidx.compose.ui.graphics.Color(0xFF8CF0C8), androidx.compose.ui.graphics.Color(0xFF7FB2FF),
                    androidx.compose.ui.graphics.Color(0xFFC49BFF),
                ),
                start = androidx.compose.ui.geometry.Offset(0f, size.height * (1f - progress)),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height * (0.4f - progress)),
            ),
            alpha = 0.22f * strength,
            blendMode = androidx.compose.ui.graphics.BlendMode.Screen,
        )
    }
}
