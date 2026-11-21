package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.cards.Card
import com.hybrid87sg.bindertracker.cards.CardIndex
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.PrimaryButton
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/**
 * The card index: download the list of every Pokémon card once, then
 * search it by name, set or number.
 *
 * This screen only draws things. The data comes in as parameters and taps
 * go out through the "on..." functions, so it can be previewed in tests.
 */
@Composable
fun CardSearchScreen(
    cardCount: Int,
    updatedAt: Long?,
    downloadProgress: Float?,
    downloadError: String?,
    query: String,
    results: List<Card>,
    onQueryChange: (String) -> Unit,
    onDownload: () -> Unit,
    onBack: () -> Unit,
) {
    val downloading = downloadProgress != null
    Column(Modifier.fillMaxSize().background(AppColors.Paper).statusBarsPadding().imePadding()) {
        Box(Modifier.padding(start = 8.dp, top = 12.dp)) { BackButton("Shelf", onBack) }

        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Card index", style = MaterialTheme.typography.headlineMedium)

            when {
                downloading -> DownloadProgress(downloadProgress ?: 0f)
                cardCount == 0 -> FirstDownload(onDownload)
                else -> IndexStatus(cardCount, updatedAt, onDownload)
            }

            if (downloadError != null) {
                Text(
                    downloadError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Danger,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            if (cardCount > 0) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search cards") },
                    placeholder = { Text("e.g. Pikachu, or 049/202") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppColors.White,
                        unfocusedContainerColor = AppColors.White,
                    ),
                )
                Text(
                    "Type a name, a set, or the number at the bottom of the card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Muted,
                )
            }
        }

        if (cardCount > 0 && query.isNotBlank()) {
            if (results.isEmpty()) {
                Text(
                    "No cards match “${query.trim()}”. Check the spelling, or try just the name or just the number.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else {
                HorizontalDivider(color = AppColors.Border)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(results, key = { it.id }) { card ->
                        CardRow(card)
                        HorizontalDivider(color = AppColors.Border, modifier = Modifier.padding(start = 92.dp))
                    }
                    if (results.size >= CardIndex.SEARCH_LIMIT) {
                        item {
                            Text(
                                "Showing the first ${CardIndex.SEARCH_LIMIT} matches. Add the set name or card number to narrow it down.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Muted,
                                modifier = Modifier.padding(20.dp).navigationBarsPadding(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstDownload(onDownload: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.White)
            .border(1.dp, AppColors.Border, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Get the card list", style = MaterialTheme.typography.titleMedium)
        Text(
            "Download the list of every Pokémon card once (a few MB, from the free TCGdex database). " +
                "After that, searching works even without internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Muted,
        )
        PrimaryButton(text = "Download card list", onClick = onDownload)
    }
}

@Composable
private fun DownloadProgress(progress: Float) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Downloading card list… ${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Muted,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.Accent,
            trackColor = AppColors.Border,
        )
    }
}

@Composable
private fun IndexStatus(cardCount: Int, updatedAt: Long?, onDownload: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val count = NumberFormat.getIntegerInstance().format(cardCount)
        val date = updatedAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
        Text(
            if (date != null) "$count cards · updated $date" else "$count cards",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Muted,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onDownload,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Accent),
        ) { Text("Update list", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun CardRow(card: Card) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Grey box the size of a card, with the picture drawn on top once it loads.
        Box(
            Modifier.size(width = 56.dp, height = 78.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AppColors.Border),
        ) {
            if (card.smallImageUrl != null) {
                AsyncImage(
                    model = card.smallImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.setName, style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.numberLabel, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
    }
}
