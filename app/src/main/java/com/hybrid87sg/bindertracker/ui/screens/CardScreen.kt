package com.hybrid87sg.bindertracker.ui.screens

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
import com.hybrid87sg.bindertracker.data.Card
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
) {
    var details by remember { mutableStateOf<CardDetails?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(card.id, attempt) {
        failed = false
        loadDetails(card.id).onSuccess { details = it }.onFailure { failed = true }
    }
    CardScreen(card, details, failed, onRetry = { attempt++ }, onBack = onBack)
}

@Composable
fun CardScreen(
    card: Card,
    details: CardDetails?,
    failed: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(AppColors.Paper).statusBarsPadding()) {
        Box(Modifier.padding(start = 8.dp, top = 12.dp)) { BackButton("Back", onBack) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardImage(
                card.largeImageUrl,
                contentDescription = "${card.name} card",
                modifier = Modifier.width(240.dp).align(Alignment.CenterHorizontally),
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
                else -> PriceTable(details.prices)
            }
            Text(
                "Market prices are what the card typically sells for on each marketplace, not a guaranteed sale. Prices in Singapore dollars come in a later update.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Muted,
            )
        }
    }
}

@Composable
private fun PriceTable(prices: List<MarketPrice>) {
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
                        Text(formatMoney(price.amount, price.currency), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
