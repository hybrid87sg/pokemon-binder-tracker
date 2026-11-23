package com.hybrid87sg.bindertracker.ui.screens

import com.hybrid87sg.bindertracker.ui.theme.appBackground
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.data.Checklist
import com.hybrid87sg.bindertracker.data.ChecklistData
import com.hybrid87sg.bindertracker.data.ExpansionCount
import com.hybrid87sg.bindertracker.data.ExpansionOption
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.CARD_ASPECT
import com.hybrid87sg.bindertracker.ui.components.CardImage
import com.hybrid87sg.bindertracker.ui.components.CardFlight
import com.hybrid87sg.bindertracker.ui.components.cardFlight
import com.hybrid87sg.bindertracker.ui.components.CollectingSheet
import com.hybrid87sg.bindertracker.ui.components.Eyebrow
import com.hybrid87sg.bindertracker.ui.components.Motion
import com.hybrid87sg.bindertracker.ui.components.PrimaryButton
import com.hybrid87sg.bindertracker.ui.components.arrive
import com.hybrid87sg.bindertracker.ui.components.countUp
import com.hybrid87sg.bindertracker.ui.components.holoBrush
import com.hybrid87sg.bindertracker.ui.components.holoTopStripe
import com.hybrid87sg.bindertracker.ui.components.pressSquish
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.DisplayFont
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Which cards the list shows. */
private enum class Show(val label: String) { Missing("Missing"), Have("Have"), All("All") }

/**
 * The Missing cards page for one binder: how complete it is, the cards
 * from its expansions that aren't in it yet, and a quick way to put one in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingCardsScreen(
    data: ChecklistData,
    // Every expansion (for names and for choosing what to collect).
    expansions: List<ExpansionOption>,
    // The expansion most of the binder's cards come from, to suggest when it collects nothing.
    suggestion: ExpansionCount?,
    // True while the card list is still downloading for the first time.
    cardListMissing: Boolean,
    onBack: () -> Unit,
    onSetCollecting: (setIds: List<String>) -> Unit,
    // Main set / Master set: this phone's own choice, anyone can change it.
    onSetMaster: (Boolean) -> Unit,
    onPutIn: (pageId: String, position: Int, cardId: String) -> Unit,
    onScanInto: (pageId: String, position: Int) -> Unit,
    // Open the card screen, able to swipe through [items] (the cards showing), starting at [start].
    onOpenCards: (items: List<BrowseItem>, start: Int, listName: String) -> Unit,
) {
    val binder = data.binder
    val followed = binder.followedSetIds
    var chosenFilter by rememberSaveable { mutableStateOf("") } // "" = all collected expansions
    val filter = chosenFilter.takeIf { it in followed }.orEmpty()
    var show by rememberSaveable { mutableStateOf(Show.All) }
    var chosenByRarity by rememberSaveable { mutableStateOf(false) }
    var chosenRarity by rememberSaveable { mutableStateOf<String?>(null) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var confetti by remember { mutableIntStateOf(0) }
    // The card just put in, and the counts from before, to celebrate once it shows up.
    var justPlaced by remember { mutableStateOf<Triple<Card, Int, Int>?>(null) }

    val setNames = remember(expansions, data.cards) {
        expansions.associate { it.id to it.name } + data.cards.associate { it.setId to it.setName }
    }
    val allEntries = remember(data, binder.countSecretRares) {
        Checklist.entries(data.cards, followed, data.owned, binder.countSecretRares)
    }
    val entries = if (filter.isEmpty()) allEntries else allEntries.filter { it.card.setId == filter }
    val have = entries.count { it.owned }
    val total = entries.size
    val missing = total - have
    val rarityKnown = entries.any { it.card.rarity.isNotEmpty() }
    val byRarity = chosenByRarity && rarityKnown
    // A rarity chip only filters while that rarity still has cards missing.
    val rarityFilter = chosenRarity?.takeIf { r -> entries.any { it.card.rarity == r && !it.owned } }

    // Celebrate once the card we put in has arrived in the binder.
    LaunchedEffect(data.owned) {
        val (card, beforeHave, rarityLeftBefore) = justPlaced ?: return@LaunchedEffect
        if (card.id !in data.owned) return@LaunchedEffect
        justPlaced = null
        val mark = Checklist.milestone(beforeHave, have, total)
        toast = when {
            missing == 0 -> { confetti++; "Complete! Every card collected." }
            rarityLeftBefore == 1 && card.rarity.isNotEmpty() -> { confetti++; "${card.rarity} complete!" }
            mark != null -> { confetti++; "$mark% of the way there!" }
            else -> "✦ ${card.name} is in the binder"
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2600)
            toast = null
        }
    }

    val context = LocalContext.current
    fun copyList() {
        val text = Checklist.copyText(binder.name, entries.filter { !it.owned }, setNames)
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Missing cards", text))
        toast = "Copied $missing ${if (missing == 1) "card" else "cards"}. Paste them into a message."
    }

    Box(Modifier.fillMaxSize().appBackground()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(binder.name, onBack, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                if (followed.isNotEmpty() && missing > 0) {
                    TextButton(onClick = ::copyList) {
                        Icon(painterResource(R.drawable.ic_copy), contentDescription = null, tint = AppColors.AccentText, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy list", color = AppColors.AccentText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (followed.isEmpty()) {
                NotCollecting(binder, suggestion, onChoose = { choosing = true }, onFollow = { onSetCollecting(listOf(it)) })
                return@Column
            }

            // Unfolded phone (same test as the binder's two-page spread): 6 cards per row, 3 each side of the fold.
            val config = LocalConfiguration.current
            val unfolded = config.screenWidthDp >= 600 && config.screenHeightDp >= 480
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (unfolded) 6 else 4),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                full("title") {
                    Column(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Eyebrow("Collecting " + (if (filter.isEmpty()) followed else listOf(filter)).joinToString(" + ") { setNames[it] ?: it })
                        Text("Missing cards", style = MaterialTheme.typography.headlineMedium, color = AppColors.Ink)
                    }
                }
                full("hero") {
                    Hero(
                        have = have,
                        total = total,
                        master = binder.countSecretRares,
                        canChange = true,
                        onMaster = onSetMaster,
                    )
                }
                if (cardListMissing || data.cards.isEmpty()) {
                    full("loading") {
                        Text(
                            "The card list is still downloading. The cards to collect appear here when it's done.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Muted,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                if (followed.size > 1) {
                    full("expansions") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 6.dp)) {
                            items(listOf("") + followed, key = { "x-$it" }) { id ->
                                val list = if (id.isEmpty()) allEntries else allEntries.filter { it.card.setId == id }
                                ExpansionChip(
                                    name = if (id.isEmpty()) "All" else setNames[id] ?: id,
                                    missing = list.count { !it.owned },
                                    fraction = if (list.isEmpty()) 0f else list.count { it.owned }.toFloat() / list.size,
                                    selected = filter == id,
                                    onClick = { chosenFilter = id; chosenRarity = null },
                                )
                            }
                        }
                    }
                }
                val near = Checklist.almostThere(entries)
                val done = Checklist.completeRarities(entries)
                if (near.isNotEmpty() || done.isNotEmpty()) {
                    full("near") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, contentPadding = PaddingValues(top = 6.dp)) {
                            item { Text("Almost there", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted) }
                            items(near, key = { "n-${it.first}" }) { (rarity, left) ->
                                Pill(
                                    text = "${rarityMark(rarity)}  $rarity: $left left",
                                    selected = rarityFilter == rarity,
                                    onClick = { chosenRarity = if (rarityFilter == rarity) null else rarity; show = Show.Missing },
                                )
                            }
                            items(done, key = { "d-$it" }) { rarity -> Pill("✓ $rarity done", foil = true) }
                        }
                    }
                }
                full("tools") {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tabs(
                            Modifier.weight(1f),
                            listOf("Missing $missing" to Show.Missing, "Have $have" to Show.Have, "All" to Show.All),
                            show,
                        ) { show = it; if (it != Show.Missing) chosenRarity = null }
                        if (rarityKnown) {
                            Pill(if (byRarity) "By rarity" else "By number", onClick = { chosenByRarity = !byRarity })
                        }
                    }
                }

                val list = entries.filter {
                    (show == Show.All || (show == Show.Missing) != it.owned) && (rarityFilter == null || it.card.rarity == rarityFilter)
                }
                val groups: List<Pair<String, List<Checklist.Entry>>> = if (byRarity) {
                    list.groupBy { it.card.rarity.ifEmpty { "Other" } }.toList().sortedBy { Checklist.rarityRank(it.first) }
                } else {
                    (if (filter.isEmpty()) followed else listOf(filter)).map { id -> (setNames[id] ?: id) to list.filter { it.card.setId == id } }
                        .filter { it.second.isNotEmpty() }
                }
                if (groups.isEmpty() && data.cards.isNotEmpty()) {
                    full("empty") {
                        Text(
                            if (show == Show.Missing) "Nothing missing here. You have every card in this list!" else "No cards from this list in the binder yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )
                    }
                }
                groups.forEach { (title, cards) ->
                    val groupAll = if (byRarity) entries.filter { it.card.rarity.ifEmpty { "Other" } == title } else entries.filter { setNames[it.card.setId] == title }
                    full("g-$title") {
                        GroupHeading(title, groupAll.count { it.owned }, groupAll.size)
                    }
                    items(cards, key = { "c-${it.card.id}" }) { e ->
                        Tile(e, onClick = {
                            // Every card showing, in order, so the card screen can swipe through them.
                            val showing = groups.flatMap { it.second }
                            val listName = when (show) {
                                Show.Missing -> "missing"
                                Show.Have -> "in your binder"
                                Show.All -> "in this list"
                            }
                            onOpenCards(
                                showing.map { BrowseItem(it.card.id, CardFlight.missingKey(it.card.id)) },
                                showing.indexOfFirst { it.card.id == e.card.id }.coerceAtLeast(0),
                                listName,
                            )
                        })
                    }
                }
            }
        }

        Confetti(confetti)

        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        ) {
            Text(
                toast.orEmpty(),
                color = AppColors.Paper,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Ink).padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    if (choosing) {
        CollectingSheet(
            expansions = expansions,
            selected = followed,
            onDismiss = { choosing = false },
            onDone = { choosing = false; onSetCollecting(it) },
        )
    }
}

/** A grid item that takes the whole width (headings, the ring, the chips). */
private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** The big ring that shows how complete the binder is, with the counts beside it. */
@Composable
private fun Hero(have: Int, total: Int, master: Boolean, canChange: Boolean, onMaster: (Boolean) -> Unit) {
    val fraction = if (total == 0) 0f else have.toFloat() / total
    val ring = remember { Animatable(0f) }
    LaunchedEffect(fraction) { ring.animateTo(fraction, tween(1000, easing = Motion.Ease)) }
    val shownPct = countUp((fraction * 100).roundToInt())
    val shownHave = countUp(have)
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(AppColors.Surface).holoTopStripe().border(1.dp, AppColors.Border, shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val track = AppColors.Surface2
        val holo = AppColors.Holo
        Box(
            Modifier.size(108.dp).semantics { contentDescription = "${(fraction * 100).roundToInt()} percent complete, $have of $total cards" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 11.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                rotate(-90f) {
                    drawArc(
                        Brush.sweepGradient(holo + holo.first()),
                        0f, 360f * ring.value, false,
                        topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$shownPct%", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = AppColors.Ink)
                Text("$shownHave of $total", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (total > 0 && have == total) "All done!" else "${total - have} to go",
                fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AppColors.Ink,
            )
            Text(
                if (master) "Master set · with secret rares" else "Main set · no secret rares",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Muted,
            )
            Segmented(
                options = listOf("Main set" to false, "Master set" to true),
                selected = master,
                enabled = canChange,
                onSelect = onMaster,
            )
        }
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, enabled: Boolean, onSelect: (T) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.clip(shape).border(1.dp, AppColors.BorderStrong, shape).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (label, value) ->
            val on = value == selected
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (on) AppColors.White else AppColors.Ink,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (on) AppColors.Accent else AppColors.Surface)
                    .clickable(enabled = enabled && !on) { onSelect(value) }
                    .heightIn(min = 32.dp)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun Tabs(modifier: Modifier, tabs: List<Pair<String, Show>>, selected: Show, onSelect: (Show) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(AppColors.Surface2).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        tabs.forEach { (label, value) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (on) AppColors.Surface else AppColors.Surface2)
                    .clickable { onSelect(value) }
                    .heightIn(min = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (on) AppColors.Ink else AppColors.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean = false, foil: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = when {
            foil -> AppColors.Black
            selected -> AppColors.White
            else -> AppColors.Ink
        },
        maxLines = 1,
        modifier = Modifier.clip(shape)
            .then(
                when {
                    foil -> Modifier.drawBehind { drawRect(holoBrush(size.width)) }
                    selected -> Modifier.background(AppColors.Accent)
                    else -> Modifier.background(AppColors.Surface2).border(1.dp, AppColors.Border, shape)
                },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun ExpansionChip(name: String, missing: Int, fraction: Float, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(112.dp).clip(shape).background(AppColors.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) AppColors.Accent else AppColors.BorderStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (missing == 0) "Complete" else "$missing missing", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(AppColors.Surface2)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).drawBehind { drawRect(holoBrush(size.width.coerceAtLeast(1f))) })
        }
    }
}

@Composable
private fun GroupHeading(title: String, have: Int, total: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(AppColors.Surface2)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(if (total == 0) 0f else have.toFloat() / total).drawBehind { drawRect(holoBrush(size.width.coerceAtLeast(1f))) })
        }
        Text("$have of $total", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted)
    }
}

/** One card in the grid: its picture if you have it, or a dashed outline with its number if not. */
@Composable
internal fun Tile(e: Checklist.Entry, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val inT = arrive(play = true, durationMillis = Motion.Short)
    Column(
        Modifier.graphicsLayer { alpha = inT; val s = 0.85f + 0.15f * inT; scaleX = s; scaleY = s }
            .pressSquish(press)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .semantics { contentDescription = "${e.card.name}, number ${e.card.number}, ${if (e.owned) "in the binder" else "missing"}" },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box {
            // A card you don't have yet shows its picture in grey, with its number in the corner.
            // No picture anywhere (a brand-new set, or no internet)? The dashed outline instead.
            CardImage(
                e.card.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().cardFlight(CardFlight.missingKey(e.card.id)),
                backupUrl = e.card.thumbnailBackupUrl,
                cardId = e.card.id,
                grey = !e.owned,
                noPicture = if (e.owned) null else ({ MissingOutline(e.card, Modifier.fillMaxSize()) }),
            )
            if (!e.owned) {
                Text(
                    e.card.number,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                        .clip(RoundedCornerShape(5.dp)).background(Color(0xB8141223))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        Text(e.card.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The dashed "still to find" card: big number, rarity mark underneath. */
@Composable
private fun MissingOutline(card: Card, modifier: Modifier = Modifier) {
    val dash = AppColors.DashedBorder
    Box(
        modifier.aspectRatio(CARD_ASPECT).clip(RoundedCornerShape(6.dp)).background(AppColors.Surface2)
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(card.number, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.Muted, maxLines = 1)
            if (card.rarity.isNotEmpty()) Text(rarityMark(card.rarity), fontSize = 10.sp, color = AppColors.Muted)
        }
    }
}

/** A short mark for a rarity, like the symbols printed on cards: ● common, ◆ uncommon, ★ rare. */
private fun rarityMark(rarity: String): String {
    val r = rarity.lowercase()
    return when {
        r == "common" -> "●"
        r == "uncommon" -> "◆"
        r == "rare" -> "★"
        "special illustration" in r -> "SIR"
        "illustration" in r -> "IR"
        "hyper" in r -> "HR"
        "ultra" in r -> "UR"
        "secret" in r -> "SR"
        "double" in r -> "★★"
        "holo" in r -> "★H"
        else -> rarity.split(' ').filter { it.isNotEmpty() }.joinToString("") { it.first().uppercase() }.take(3)
    }
}

/** The panel for one card: where it goes in the binder, and buttons to put it there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardSheet(
    entry: Checklist.Entry,
    binder: Binder,
    spot: Checklist.Spot?,
    // For a card you have: (page number, pocket position).
    where: Pair<Int, Int>?,
    setName: String,
    onDismiss: () -> Unit,
    onPutIn: (Checklist.Spot) -> Unit,
    onScan: (Checklist.Spot) -> Unit,
    onDetails: () -> Unit,
) {
    val card = entry.card
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppColors.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(84.dp)) {
                    // A card you don't have yet is shown faded.
                    CardImage(card.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().alpha(if (entry.owned) 1f else 0.55f), backupUrl = card.thumbnailBackupUrl, cardId = card.id)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Eyebrow("$setName · #${card.number}${card.setOfficial.takeIf { it > 0 }?.let { "/$it" }.orEmpty()}")
                    Text(card.name, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AppColors.Ink)
                    Text(
                        listOfNotNull(card.rarity.ifEmpty { null }, if (entry.secret) "secret rare" else null).joinToString(" · ").ifEmpty { "Rarity unknown" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Muted,
                    )
                }
            }

            val box = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Surface2).border(1.dp, AppColors.Border, RoundedCornerShape(14.dp)).padding(12.dp)
            when {
                entry.owned -> Text(
                    where?.let { "In this binder: page ${it.first}, pocket ${it.second + 1}" } ?: "In this binder",
                    fontWeight = FontWeight.SemiBold, color = AppColors.SuccessText, modifier = box,
                )
                spot == null -> Text(
                    "Every pocket in this binder is full. Add a page first, then come back to put it in.",
                    style = MaterialTheme.typography.bodyMedium, color = AppColors.Ink, modifier = box,
                )
                else -> Row(box, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniPage(binder.columns, binder.rows, spot.position)
                    Column(Modifier.weight(1f)) {
                        Text("Goes on page ${spot.pageNumber}, pocket ${spot.position + 1}", fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
                        Text(
                            spot.after?.let { "Right after #${it.number} ${it.name}" } ?: "The first free pocket",
                            style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
                        )
                    }
                }
            }

            if (!entry.owned && spot != null && binder.canEdit) {
                PrimaryButton(text = "Put it in", onClick = { onPutIn(spot) }, icon = R.drawable.ic_check)
                OutlinedButton(
                    onClick = { onScan(spot) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AppColors.BorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
                ) {
                    Icon(painterResource(R.drawable.ic_camera), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan it in to check")
                }
            }
            TextButton(onClick = onDetails, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Card details and market price", color = AppColors.AccentText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A tiny binder page with the target pocket lit up, so you can see where to slide the card. */
@Composable
internal fun MiniPage(columns: Int, rows: Int, target: Int) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(target) {
        while (true) {
            pulse.animateTo(0.35f, tween(600))
            pulse.animateTo(1f, tween(600))
        }
    }
    Column(
        Modifier.clip(RoundedCornerShape(5.dp)).background(AppColors.Surface).border(1.dp, AppColors.Border, RoundedCornerShape(5.dp)).padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(rows) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(columns) { c ->
                    val i = r * columns + c
                    Box(
                        Modifier.size(width = 12.dp, height = 17.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i == target) AppColors.AccentText.copy(alpha = pulse.value) else AppColors.BorderStrong),
                    )
                }
            }
        }
    }
}

/** When nothing is collected yet: explain, suggest the most common expansion, or choose one. */
@Composable
private fun NotCollecting(binder: Binder, suggestion: ExpansionCount?, onChoose: () -> Unit, onFollow: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("Checklist")
        Text("Missing cards", style = MaterialTheme.typography.headlineMedium, color = AppColors.Ink)
        val shape = RoundedCornerShape(18.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(AppColors.Surface).border(1.dp, AppColors.Border, shape).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MissingOutline(Card(id = "", localId = "???", name = "", setId = "", setName = "", imageUrl = null), Modifier.width(64.dp))
            Text("This binder isn't collecting an expansion yet", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = AppColors.Ink, textAlign = TextAlign.Center)
            Text(
                "Choose one and this page lists every card from it that isn't in the binder.",
                style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted, textAlign = TextAlign.Center,
            )
            if (!binder.isOwner) {
                Text("Only ${binder.ownerName ?: "the owner"} can choose what it collects.", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted, textAlign = TextAlign.Center)
            } else {
                if (suggestion != null) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Surface2).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Most cards in ${binder.name} are from ${suggestion.setName} (${suggestion.cards} ${if (suggestion.cards == 1) "card" else "cards"}).",
                            style = MaterialTheme.typography.bodyMedium, color = AppColors.Ink,
                        )
                        PrimaryButton(text = "Collect ${suggestion.setName}", onClick = { onFollow(suggestion.setId) })
                    }
                }
                OutlinedButton(
                    onClick = onChoose,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AppColors.BorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
                ) { Text(if (suggestion != null) "Choose another expansion" else "Choose expansions") }
            }
        }
    }
}

/** A burst of foil-coloured paper. Plays each time [trigger] goes up (skipped when it's 0). */
@Composable
private fun Confetti(trigger: Int) {
    if (trigger == 0) return
    val colors = AppColors.Holo
    var progress by remember(trigger) { mutableFloatStateOf(0f) }
    val pieces = remember(trigger) {
        val rnd = Random(trigger * 7919)
        List(90) {
            val angle = rnd.nextFloat() * PI.toFloat() * 2f
            val speed = 0.4f + rnd.nextFloat() * 0.9f
            ConfettiPiece(
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 1.1f,
                spin = (rnd.nextFloat() - 0.5f) * 900f,
                w = 6f + rnd.nextFloat() * 6f,
                h = 3f + rnd.nextFloat() * 4f,
                color = colors[rnd.nextInt(colors.size)],
            )
        }
    }
    LaunchedEffect(trigger) {
        val start = withFrameNanos { it }
        while (progress < 1f) {
            val now = withFrameNanos { it }
            progress = ((now - start) / 1_800_000_000f).coerceAtMost(1f)
        }
    }
    if (progress >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val t = progress * 1.8f
        val unit = size.minDimension * 0.55f
        val origin = Offset(size.width / 2, size.height * 0.3f)
        pieces.forEach { p ->
            val x = origin.x + p.vx * unit * t
            val y = origin.y + p.vy * unit * t + 0.9f * unit * t * t
            rotate(p.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    p.color.copy(alpha = (1f - progress).coerceIn(0f, 1f)),
                    topLeft = Offset(x - p.w.dp.toPx() / 2, y - p.h.dp.toPx() / 2),
                    size = Size(p.w.dp.toPx(), p.h.dp.toPx()),
                )
            }
        }
    }
}

private class ConfettiPiece(val vx: Float, val vy: Float, val spin: Float, val w: Float, val h: Float, val color: androidx.compose.ui.graphics.Color)
