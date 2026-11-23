package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.ai.BinderChat
import com.hybrid87sg.bindertracker.ai.ChatMessage
import com.hybrid87sg.bindertracker.ui.components.Eyebrow
import com.hybrid87sg.bindertracker.ui.components.holoBrush
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.appBackground

/**
 * The Chat tab: Binder chat. Ask about your collection in plain words and
 * Gemini answers from your binders. Starter questions sit above the box.
 */
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    busy: Boolean,
    cardCount: Int,
    binderCount: Int,
    available: Boolean,
    onAsk: (String) -> Unit,
    onClear: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    // Keep the newest message in view.
    LaunchedEffect(messages.size, busy) {
        val last = messages.size + (if (busy) 1 else 0) - 1
        if (last >= 0) list.animateScrollToItem(last)
    }
    fun send(text: String) {
        if (text.isBlank() || busy) return
        onAsk(text)
        typed = ""
    }

    Column(Modifier.fillMaxSize().appBackground().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 32.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("Ask about your cards")
                Text("Binder chat", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Answers come from your $binderCount ${if (binderCount == 1) "binder" else "binders"} and $cardCount ${if (cardCount == 1) "card" else "cards"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Muted,
                )
            }
            if (messages.isNotEmpty()) {
                TextButton(onClick = onClear, enabled = !busy) { Text("New chat", color = AppColors.AccentText, fontWeight = FontWeight.SemiBold) }
            }
        }

        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        if (available) "Ask anything about your binders, for example where a card is, what you're missing, or which binder is nearly full. Gemini writes the answers, so check anything important."
                        else "Binder chat needs the AI helper switched on. See Me → AI helper (setup).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Muted,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
            itemsIndexed(messages) { _, m -> Bubble(m) }
            if (busy) item { Typing() }
        }

        if (available) {
            val unasked = BinderChat.suggestions.filter { s -> messages.none { it.fromMe && it.text == s } }
            if (unasked.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(unasked) { s ->
                        Text(
                            s,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Ink,
                            maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.Surface)
                                .border(1.dp, AppColors.BorderStrong, RoundedCornerShape(50))
                                .then(if (busy) Modifier.alpha(0.5f) else Modifier)
                                .clickable(enabled = !busy) { send(s) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = { Text("Ask about your binders") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(typed) }),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = AppColors.Surface,
                        focusedContainerColor = AppColors.Surface,
                        unfocusedBorderColor = AppColors.BorderStrong,
                    ),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = { send(typed) },
                    enabled = typed.isNotBlank() && !busy,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = AppColors.Accent, contentColor = AppColors.White),
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Send" },
                ) {
                    Icon(painterResource(R.drawable.ic_send), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromMe) Alignment.CenterEnd else Alignment.CenterStart) {
        if (m.fromMe) {
            Text(
                m.text,
                color = AppColors.White,
                fontSize = 15.sp,
                modifier = Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)).background(AppColors.Accent)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        } else {
            Column(
                Modifier.widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)).background(AppColors.Surface)
                    .border(1.dp, AppColors.Border, RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).drawBehind { drawRect(holoBrush(size.width)) })
                    Text("Binder chat", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.Muted)
                }
                Text(m.text, fontSize = 15.sp, color = if (m.failed) AppColors.Danger else AppColors.Ink)
            }
        }
    }
}

/** Three dots that fade in and out while Gemini writes. */
@Composable
private fun Typing() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(
        Modifier.clip(RoundedCornerShape(18.dp)).background(AppColors.Surface).border(1.dp, AppColors.Border, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "Binder chat is writing" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) { i ->
            val a by t.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(7.dp).alpha(a).clip(CircleShape).background(AppColors.Muted))
        }
    }
}
