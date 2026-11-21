package com.hybrid87sg.bindertracker.ui.screens

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
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.CardImage
import com.hybrid87sg.bindertracker.ui.components.formatDate
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import kotlinx.coroutines.delay

/** Status of the card list on this phone, shown at the bottom of the search screen. */
data class IndexStatus(val count: Int, val lastUpdated: Long, val downloading: Boolean, val error: String?)

/**
 * Search screen with its own search logic.
 * [title]/[backLabel] change when it's used to pick a card for a pocket.
 */
@Composable
fun SearchRoute(
    title: String,
    backLabel: String,
    status: IndexStatus,
    search: suspend (String) -> List<Card>,
    onBack: () -> Unit,
    onCardClick: (Card) -> Unit,
    onRefreshIndex: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Card>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // Search shortly after typing stops, so we don't search on every letter.
    LaunchedEffect(query, status.count) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(200)
        searching = true
        results = search(query)
        searching = false
    }

    SearchScreen(
        title = title,
        backLabel = backLabel,
        query = query,
        onQueryChange = { query = it },
        results = results,
        searching = searching,
        status = status,
        onBack = onBack,
        onCardClick = onCardClick,
        onRefreshIndex = onRefreshIndex,
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
) {
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(AppColors.Paper).statusBarsPadding().imePadding()) {
        Box(Modifier.padding(start = 8.dp, top = 12.dp)) { BackButton(backLabel, onBack) }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Card name or number") },
                placeholder = { Text("e.g. Pikachu or 025/165") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AppColors.White,
                    focusedContainerColor = AppColors.White,
                    unfocusedBorderColor = AppColors.BorderStrong,
                ),
                trailingIcon = if (searching) {
                    { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                } else null,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                status.count == 0 -> item { IndexEmptyState(status, onRefreshIndex) }
                query.isBlank() -> item {
                    Text("Type part of a card name, or the number printed at the bottom of the card.", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
                }
                !searching && results.isEmpty() -> item {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No card matches \"${query.trim()}\". Check the spelling, or try fewer letters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Muted,
                        )
                    }
                }
                else -> {
                    item {
                        Text(
                            if (results.size >= 100) "Showing the first 100 matches. Add more letters or the card number to narrow it down."
                            else "${results.size} ${if (results.size == 1) "card matches" else "cards match"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Muted,
                        )
                    }
                    items(results, key = { it.id }) { card -> CardRow(card) { onCardClick(card) } }
                }
            }
        }

        if (status.count > 0) IndexFooter(status, onRefreshIndex)
    }
}

@Composable
private fun CardRow(card: Card, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = AppColors.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CardImage(card.thumbnailUrl, contentDescription = null, modifier = Modifier.width(44.dp), corner = 4.dp)
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
            TextButton(onClick = onRefresh, colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Accent)) {
                Text("Download card list", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun IndexFooter(status: IndexStatus, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(AppColors.Paper)) {
        HorizontalDivider(color = AppColors.Border)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
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
