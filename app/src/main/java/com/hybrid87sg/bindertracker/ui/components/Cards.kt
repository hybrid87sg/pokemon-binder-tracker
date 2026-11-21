package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** Pokémon cards are 63 × 88 mm, so every card picture uses this shape. */
const val CARD_ASPECT = 63f / 88f

/** A card picture loaded from the internet, with a plain placeholder while it loads. */
@Composable
fun CardImage(url: String?, contentDescription: String?, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.aspectRatio(CARD_ASPECT).clip(shape).background(CardPlaceholder).border(1.dp, CardPlaceholderBorder, shape)) {
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { BlankCard() },
                error = { BlankCard() },
            )
        } else {
            BlankCard()
        }
    }
}

@Composable
private fun BlankCard() {
    // Same simple "card" drawing as the prototype: a panel for the artwork.
    Box(Modifier.fillMaxSize().padding(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(3.dp)).background(CardArtPlaceholder))
    }
}

private val CardPlaceholder = androidx.compose.ui.graphics.Color(0xFFE4E0D4)
private val CardPlaceholderBorder = androidx.compose.ui.graphics.Color(0xFFCFCABD)
private val CardArtPlaceholder = androidx.compose.ui.graphics.Color(0xFFD4CFC0)

/** 0.26 + "USD" -> "US$0.26"; 0.14 + "EUR" -> "€0.14". */
fun formatMoney(amount: Double, currencyCode: String): String {
    val format = NumberFormat.getCurrencyInstance(Locale.UK)
    runCatching { format.currency = Currency.getInstance(currencyCode) }
    if (currencyCode == "USD") return "US$" + "%,.2f".format(Locale.UK, amount)
    return format.format(amount)
}

/** "2026-11-20T22:16:31.016Z" -> "20 Nov 2026". */
fun formatDate(iso: String?): String? = iso?.let {
    runCatching {
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).withZone(ZoneId.systemDefault()).format(Instant.parse(it))
    }.getOrNull()
}

/** Milliseconds -> "21 Nov 2026". */
fun formatDate(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
