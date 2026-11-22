package com.hybrid87sg.bindertracker.ui.screens

import com.hybrid87sg.bindertracker.ui.theme.appBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.hybrid87sg.bindertracker.prices.SgdRates
import java.util.Locale
import com.hybrid87sg.bindertracker.tcgdex.CardDetails
import com.hybrid87sg.bindertracker.tcgdex.MarketPrice
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.CardImage
import com.hybrid87sg.bindertracker.ui.components.formatDate
import com.hybrid87sg.bindertracker.ui.components.formatMoney
import com.hybrid87sg.bindertracker.ui.theme.AppColors

/** Loads the card's details from TCGdex, with a retry if it fails. */
@Composable
fun CardRoute(
    card: Card,
    loadDetails: suspend (String) -> Result<CardDetails>,
    onBack: () -> Unit,
    // Today's rates for showing prices in Singapore dollars (null = couldn't get them).
    loadRates: suspend () -> SgdRates? = { null },
) {
    var details by remember { mutableStateOf<CardDetails?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(card.id, attempt) {
        failed = false
        loadDetails(card.id).onSuccess { details = it }.onFailure { failed = true }
    }
    var rates by remember { mutableStateOf<SgdRates?>(null) }
    var ratesLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        rates = runCatching { loadRates() }.getOrNull()
        ratesLoaded = true
    }
    CardScreen(card, details, failed, onRetry = { attempt++ }, onBack = onBack, rates = rates, ratesLoaded = ratesLoaded)
}

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
) {
    var fullScreen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().appBackground().statusBarsPadding()) {
        Box(Modifier.padding(start = 8.dp, top = 12.dp)) { BackButton("Back", onBack) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardImage(
                card.largeImageUrl,
                contentDescription = "${card.name} card",
                modifier = Modifier.width(240.dp).align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Show the card full screen") { fullScreen = true },
                corner = 10.dp,
                backupUrl = card.largeBackupUrl,
                cardId = card.id,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    if (fullScreen) FullScreenCard(card) { fullScreen = false }
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
private fun FullScreenCard(card: Card, onClose: () -> Unit) {
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
                    .fillMaxWidth().padding(horizontal = 8.dp)
                    .onSizeChanged { cardSize = it }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y + dragY
                    },
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
