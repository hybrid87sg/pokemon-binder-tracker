package com.hybrid87sg.bindertracker.ui.screens

import com.hybrid87sg.bindertracker.ui.theme.appBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.data.CardSearch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.data.ExpansionOption
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.height
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.CardImage
import com.hybrid87sg.bindertracker.ui.components.formatDate
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import kotlinx.coroutines.delay

/** Status of the card list on this phone, shown at the bottom of the search screen. */
data class IndexStatus(val count: Int, val lastUpdated: Long, val downloading: Boolean, val error: String?)

/**
 * An expansion to start the filter on: the one the last [cardsInARow] cards
 * put in this binder came from.
 */
data class ExpansionPreset(val setId: String, val cardsInARow: Int)

/**
 * Search screen with its own search logic.
 * [title]/[backLabel] change when it's used to pick a card for a pocket.
 */
@Composable
fun SearchRoute(
    title: String,
    backLabel: String,
    status: IndexStatus,
    search: suspend (query: String, seriesId: String, setId: String) -> CardSearch,
    onBack: () -> Unit,
    onCardClick: (Card) -> Unit,
    onRefreshIndex: () -> Unit,
    // Every expansion in the card list, for the expansion filter.
    loadExpansions: suspend () -> List<ExpansionOption> = { emptyList() },
    // Start filtered to this expansion (it can arrive a moment after the screen opens).
    preset: ExpansionPreset? = null,
    // Start with this already typed in (the name live scanning read).
    initialQuery: String = "",
    // The cards showing (yours first), so the card screen can swipe through them.
    onResults: (List<Card>) -> Unit = {},
    // False on the Search tab: no Back button, and the keyboard waits until you tap the box.
    showBack: Boolean = true,
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    // The filter: one whole series, or one expansion, or neither ("" = all cards).
    var seriesId by rememberSaveable { mutableStateOf("") }
    var setId by rememberSaveable { mutableStateOf("") }
    // Apply the preset once, so clearing it (✕) sticks.
    var presetApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(preset) {
        if (preset != null && !presetApplied) {
            presetApplied = true
            seriesId = ""
            setId = preset.setId
        }
    }
    var expansions by remember { mutableStateOf<List<ExpansionOption>>(emptyList()) }
    LaunchedEffect(status.count) { expansions = loadExpansions() }
    var results by remember { mutableStateOf(CardSearch(emptyList())) }
    LaunchedEffect(results) { onResults(results.cards) }
    var searching by remember { mutableStateOf(false) }

    // Search shortly after typing stops, so we don't search on every letter.
    LaunchedEffect(query, seriesId, setId, status.count) {
        if (query.isBlank()) {
            results = CardSearch(emptyList())
            return@LaunchedEffect
        }
        delay(200)
        searching = true
        results = search(query, seriesId, setId)
        searching = false
    }

    SearchScreen(
        title = title,
        backLabel = backLabel,
        query = query,
        onQueryChange = { query = it },
        results = results.cards,
        places = results.places,
        moreOthers = results.moreOthers,
        searching = searching,
        status = status,
        onBack = onBack,
        onCardClick = onCardClick,
        onRefreshIndex = onRefreshIndex,
        expansions = expansions,
        selectedSeries = seriesId,
        selectedSet = setId,
        onFilterChange = { series, set -> seriesId = series; setId = set },
        filterNote = preset?.takeIf { seriesId.isEmpty() && setId == it.setId }
            ?.let { "Set from your last ${it.cardsInARow} cards. Tap ✕ to search every expansion." },
        showBack = showBack,
        autoFocus = showBack,
    )
}

@Composable
fun SearchScreen(
    title: String,
    backLabel: String,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Card>,
    searching: Boolean,
    status: IndexStatus,
    onBack: () -> Unit,
    onCardClick: (Card) -> Unit,
    onRefreshIndex: () -> Unit,
    autoFocus: Boolean = true,
    expansions: List<ExpansionOption> = emptyList(),
    selectedSeries: String = "",
    selectedSet: String = "",
    // (seriesId, setId): one of them is filled in, or both are "" for all cards.
    onFilterChange: (String, String) -> Unit = { _, _ -> },
    // A line under the filter saying why it's already set, or null.
    filterNote: String? = null,
    // Where each of your own cards is, by card ID ("Kanto Binder · page 1").
    // Those cards come first in [results].
    places: Map<String, String> = emptyMap(),
    // True when more cards matched than are shown.
    moreOthers: Boolean = false,
    showBack: Boolean = true,
) {
    val filtered = selectedSeries.isNotEmpty() || selectedSet.isNotEmpty()
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val barShown = com.hybrid87sg.bindertracker.ui.components.LocalTabBarClearance.current > 0.dp
    Column(Modifier.fillMaxSize().appBackground().statusBarsPadding().imePadding()) {
        if (showBack) Box(Modifier.padding(start = 12.dp, top = 12.dp)) { BackButton(backLabel, onBack) }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = if (showBack) 8.dp else 28.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!showBack) com.hybrid87sg.bindertracker.ui.components.Eyebrow("Find any card")
                Text(title, style = if (showBack) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Card name or number") },
                placeholder = { Text("e.g. Pikachu or 025/165") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // A rounded search pill with a magnifying glass (2026 refresh).
                shape = RoundedCornerShape(28.dp),
                leadingIcon = { com.hybrid87sg.bindertracker.ui.components.SmallIcon(com.hybrid87sg.bindertracker.R.drawable.ic_search, AppColors.Muted, size = 22.dp) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AppColors.Group,
                    focusedContainerColor = AppColors.Surface,
                    unfocusedBorderColor = AppColors.Border,
                ),
                trailingIcon = if (searching) {
                    { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                } else null,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (expansions.isNotEmpty()) {
                ExpansionFilter(expansions, selectedSeries, selectedSet, onFilterChange)
                if (filterNote != null) Text(filterNote, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp + com.hybrid87sg.bindertracker.ui.components.LocalTabBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                status.count == 0 -> item { IndexEmptyState(status, onRefreshIndex) }
                query.isBlank() -> item {
                    Text(
                        if (!filtered) "Type part of a card name, or the number printed at the bottom of the card."
                        else "Type part of a card name, or the number printed at the bottom of the card. Only cards from ${filterName(expansions, selectedSeries, selectedSet)} are shown.",
                        style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
                }
                !searching && results.isEmpty() -> item {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (!filtered) "No card matches \"${query.trim()}\". Check the spelling, or try fewer letters."
                            else "No card in ${filterName(expansions, selectedSeries, selectedSet)} matches \"${query.trim()}\". Try another expansion, or All expansions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Muted,
                        )
                    }
                }
                else -> {
                    val mine = results.filter { it.id in places }
                    val others = results.filter { it.id !in places }
                    item {
                        Text(
                            if (moreOthers || (mine.isEmpty() && results.size >= 100)) "Showing the first 100 matches. Add more letters or the card number to narrow it down."
                            else "${results.size} ${if (results.size == 1) "card matches" else "cards match"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Muted,
                        )
                    }
                    if (mine.isNotEmpty()) {
                        item(key = "mine") { SectionLabel("In your binders") }
                        items(mine, key = { it.id }) { card -> CardRow(card, places[card.id]) { onCardClick(card) } }
                        if (others.isNotEmpty()) item(key = "others") { SectionLabel("All other cards") }
                    }
                    items(others, key = { it.id }) { card -> CardRow(card) { onCardClick(card) } }
                }
            }
            if (status.count > 0 && barShown) item(key = "footer") { IndexFooter(status, onRefreshIndex, inline = true) }
        }

        // With the floating tab bar, the footer goes at the end of the list instead (so the bar doesn't cover it).
        if (status.count > 0 && !barShown) IndexFooter(status, onRefreshIndex)
    }
}

/** What the filter is set to, in words: "Paldea Evolved", or "all of Scarlet & Violet". */
private fun filterName(expansions: List<ExpansionOption>, seriesId: String, setId: String): String = when {
    setId.isNotEmpty() -> expansions.firstOrNull { it.id == setId }?.name ?: setId
    seriesId.isNotEmpty() -> "all of " + (expansions.firstOrNull { it.seriesId == seriesId }?.seriesName ?: seriesId)
    else -> "all expansions"
}

/**
 * "Expansion: All expansions ▾". Tapping it opens a panel listing every expansion,
 * newest first, under its series. Tapping a series heading picks the whole series.
 * While a filter is on, the button is violet and an ✕ next to it clears it.
 */
@Composable
private fun ExpansionFilter(
    expansions: List<ExpansionOption>,
    selectedSeries: String,
    selectedSet: String,
    onChange: (String, String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val filtered = selectedSeries.isNotEmpty() || selectedSet.isNotEmpty()
    val label = when {
        selectedSet.isNotEmpty() -> filterName(expansions, "", selectedSet)
        selectedSeries.isNotEmpty() -> "All " + filterName(expansions, selectedSeries, "").removePrefix("all of ")
        else -> "All expansions"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(percent = 50),
            color = if (filtered) AppColors.Tonal else AppColors.Group,
            border = if (filtered) BorderStroke(1.5.dp, AppColors.AccentText) else null,
            modifier = Modifier.weight(1f).height(48.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Expansion: $label",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (filtered) FontWeight.SemiBold else null,
                    color = if (filtered) AppColors.AccentText else AppColors.Ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                com.hybrid87sg.bindertracker.ui.components.SmallIcon(com.hybrid87sg.bindertracker.R.drawable.ic_expand_more, if (filtered) AppColors.AccentText else AppColors.Ink)
            }
        }
        if (filtered) {
            Surface(
                onClick = { onChange("", "") },
                shape = androidx.compose.foundation.shape.CircleShape,
                color = AppColors.Group,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Clear expansion filter" },
            ) {
                Box(contentAlignment = Alignment.Center) { com.hybrid87sg.bindertracker.ui.components.SmallIcon(com.hybrid87sg.bindertracker.R.drawable.ic_close, AppColors.Ink) }
            }
        }
    }
    if (open) {
        ExpansionSheet(expansions, selectedSeries, selectedSet, onDismiss = { open = false }) { series, set ->
            open = false
            onChange(series, set)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpansionSheet(
    expansions: List<ExpansionOption>,
    selectedSeries: String,
    selectedSet: String,
    onDismiss: () -> Unit,
    onPick: (String, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppColors.Surface) {
        ExpansionPicker(expansions, selectedSeries, selectedSet, onPick)
    }
}

/** What's inside the panel. Kept separate so it can be drawn in tests. */
@Composable
fun ExpansionPicker(
    expansions: List<ExpansionOption>,
    selectedSeries: String,
    selectedSet: String,
    onPick: (String, String) -> Unit,
) {
    var find by rememberSaveable { mutableStateOf("") }
    val text = find.trim()
    // Keep an expansion if its name, or its series' name, has the typed letters.
    val shown = if (text.isEmpty()) expansions else expansions.filter {
        it.name.contains(text, ignoreCase = true) || it.seriesName.contains(text, ignoreCase = true)
    }
    // The list is already newest first with each series together, so this keeps that order.
    val groups = shown.groupBy { it.seriesId }

    Column(Modifier.fillMaxHeight(0.9f).navigationBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose an expansion", style = MaterialTheme.typography.titleLarge, color = AppColors.Ink)
            OutlinedTextField(
                value = find,
                onValueChange = { find = it },
                label = { Text("Find an expansion") },
                placeholder = { Text("e.g. Paldea") },
                singleLine = true,
                // A rounded search pill with a magnifying glass (2026 refresh).
                shape = RoundedCornerShape(28.dp),
                leadingIcon = { com.hybrid87sg.bindertracker.ui.components.SmallIcon(com.hybrid87sg.bindertracker.R.drawable.ic_search, AppColors.Muted, size = 22.dp) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AppColors.Group,
                    focusedContainerColor = AppColors.Surface,
                    unfocusedBorderColor = AppColors.Border,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            if (text.isEmpty()) {
                item(key = "all") {
                    PickerRow("All expansions", selectedSeries.isEmpty() && selectedSet.isEmpty(), bold = true) { onPick("", "") }
                }
            }
            if (groups.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No expansion matches \"$text\". Try fewer letters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Muted,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            groups.forEach { (seriesId, sets) ->
                item(key = "series-$seriesId") {
                    SeriesHeading(
                        name = sets.first().seriesName.ifEmpty { "Other" },
                        selected = seriesId.isNotEmpty() && selectedSeries == seriesId,
                        // Cards with no series can't be picked as a group.
                        onClick = if (seriesId.isEmpty()) null else ({ onPick(seriesId, "") }),
                    )
                }
                items(sets, key = { "set-${it.id}" }) { set ->
                    PickerRow(set.name, selectedSet == set.id) { onPick("", set.id) }
                }
            }
        }
    }
}

/** A series name in capitals. Tapping it picks every expansion in the series. */
@Composable
private fun SeriesHeading(name: String, selected: Boolean, onClick: (() -> Unit)?) {
    val content = @Composable {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 12.dp).padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) AppColors.AccentText else AppColors.Muted,
                modifier = Modifier.weight(1f),
            )
            if (onClick != null) {
                Text(
                    if (selected) "All cards ✓" else "All cards",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.AccentText,
                )
            }
        }
    }
    if (onClick == null) content()
    else Surface(onClick = onClick, color = AppColors.Surface, shape = RoundedCornerShape(10.dp)) { content() }
}

@Composable
private fun PickerRow(name: String, selected: Boolean, bold: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) AppColors.Surface2 else AppColors.Surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected || bold) FontWeight.SemiBold else null,
                color = if (selected) AppColors.AccentText else AppColors.Ink,
                modifier = Modifier.weight(1f),
            )
            if (selected) com.hybrid87sg.bindertracker.ui.components.SmallIcon(com.hybrid87sg.bindertracker.R.drawable.ic_check, AppColors.AccentText)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = AppColors.Muted,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** One card in the results. [place] says where it is in your binders, if it's yours. */
@Composable
private fun CardRow(card: Card, place: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = AppColors.Group,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CardImage(card.thumbnailUrl, contentDescription = null, modifier = Modifier.width(44.dp), corner = 4.dp, backupUrl = card.thumbnailBackupUrl, cardId = card.id)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${card.setName} · #${card.number}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (place != null) {
                    Text(
                        place,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.AccentText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(50))
                            .background(AppColors.Surface2).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexEmptyState(status: IndexStatus, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (status.downloading) {
            Text("Downloading the card list…", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("About 24,000 cards. This only happens once, then search works offline.", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
        } else {
            Text("The card list isn't downloaded yet.", style = MaterialTheme.typography.titleMedium)
            status.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.Danger) }
            TextButton(onClick = onRefresh, colors = ButtonDefaults.textButtonColors(contentColor = AppColors.AccentText)) {
                Text("Download card list", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun IndexFooter(status: IndexStatus, onRefresh: () -> Unit, inline: Boolean = false) {
    Column(Modifier.fillMaxWidth().then(if (inline) Modifier.padding(top = 8.dp) else Modifier.background(AppColors.Paper))) {
        if (!inline) HorizontalDivider(color = AppColors.Border)
        Row(
            Modifier.fillMaxWidth().then(if (inline) Modifier else Modifier.navigationBarsPadding())
                .padding(start = if (inline) 6.dp else 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    status.downloading -> "Updating card list…"
                    status.error != null -> status.error
                    else -> "%,d cards · updated %s".format(status.count, formatDate(status.lastUpdated))
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (status.error != null) AppColors.Danger else AppColors.Muted,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh, enabled = !status.downloading) { Text("Update") }
        }
    }
}
