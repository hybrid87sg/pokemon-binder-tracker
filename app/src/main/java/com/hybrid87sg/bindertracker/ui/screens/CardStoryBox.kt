package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.ai.AiSetup
import com.hybrid87sg.bindertracker.ai.CardStories
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.tcgdex.CardDetails
import com.hybrid87sg.bindertracker.ui.components.holoBrush
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * The "Card story" box on the card screen. Shows the saved story if there
 * is one; otherwise a button that asks Gemini to write it. Hidden in builds
 * without Firebase.
 */
@Composable
fun CardStoryBox(card: Card, details: CardDetails?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!AiSetup.isAvailable(context)) return
    val scope = rememberCoroutineScope()
    // Per card: swiping to another card starts fresh.
    var story by remember(card.id) { mutableStateOf<String?>(null) }
    var writing by remember(card.id) { mutableStateOf(false) }
    var error by remember(card.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(card.id) { story = CardStories.saved(context, card.id) }
    fun write() {
        if (writing) return
        writing = true
        error = null
        scope.launch {
            CardStories.write(context, card, details?.rarity, details?.illustrator)
                .onSuccess { story = it }
                .onFailure { error = it.message ?: "Something went wrong." }
            writing = false
        }
    }

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(AppColors.Surface).border(1.dp, AppColors.Border, shape)
            // A strip of holo foil down the left edge, like the Account card.
            .drawBehind { drawRect(holoBrush(size.height), size = Size(4.dp.toPx(), size.height)) }
            .animateContentSize()
            .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Card story", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = AppColors.Ink, modifier = Modifier.weight(1f))
                Text(
                    "AI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.AccentText,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.Surface2).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            val text = story
            when {
                writing -> Shimmer()
                text != null -> {
                    Text(text, fontSize = 15.sp, lineHeight = 22.sp, color = AppColors.Ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Written by Gemini. It may get details wrong.", fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.weight(1f))
                        TextButton(onClick = { write() }) { Text("Write it again", color = AppColors.AccentText, fontWeight = FontWeight.SemiBold) }
                    }
                }
                else -> OutlinedButton(
                    onClick = { write() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(2.dp, AppColors.Ink),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
                ) {
                    Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Tell me about this card", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
            error?.let { Text(it, fontSize = 13.sp, color = AppColors.Danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}

/** Three grey lines that pulse while the story is being written. */
@Composable
private fun Shimmer() {
    val t = rememberInfiniteTransition(label = "story")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    Column(Modifier.alpha(a).semantics { contentDescription = "Writing the card story" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1f, 0.92f, 0.7f).forEach { w ->
            Box(Modifier.fillMaxWidth(w).height(11.dp).clip(RoundedCornerShape(6.dp)).background(AppColors.Surface2))
        }
    }
}
