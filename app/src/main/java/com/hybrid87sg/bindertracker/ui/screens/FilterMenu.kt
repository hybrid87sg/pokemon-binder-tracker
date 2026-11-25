package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.data.CardFact
import com.hybrid87sg.bindertracker.ui.screens.FilterLogic.Choice
import com.hybrid87sg.bindertracker.ui.screens.FilterLogic.Quick
import com.hybrid87sg.bindertracker.ui.screens.FilterLogic.Section
import com.hybrid87sg.bindertracker.ui.theme.AppColors

/** The "Filter" button in the binder page's header, with how many things are ticked. */
@Composable
internal fun FilterButton(count: Int, open: Boolean, onClick: () -> Unit) {
    val neon = BatchNeon
    val on = count > 0
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "filterChevron")
    Row(
        Modifier.height(38.dp)
            .then(if (on) Modifier.drawBehind { drawRoundRect(neon.copy(alpha = 0.35f), topLeft = Offset(-3f * density, -3f * density), size = Size(size.width + 6f * density, size.height + 6f * density), cornerRadius = CornerRadius(size.height)) } else Modifier)
            .clip(RoundedCornerShape(50))
            .background(if (on) neon else AppColors.Ink.copy(alpha = 0.08f))
            .border(1.dp, if (on) neon else AppColors.BorderStrong, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (on) "Filter, $count on" else "Filter" }
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val ink = if (on) OnNeon else AppColors.Ink
        // Three lines getting shorter: the usual filter sign.
        Box(Modifier.size(16.dp).drawBehind {
            val w = 1.8f * density
            listOf(0.22f to 1f, 0.5f to 0.6f, 0.78f to 0.25f).forEach { (y, len) ->
                val half = size.width * len / 2
                drawLine(ink, Offset(size.width / 2 - half, size.height * y), Offset(size.width / 2 + half, size.height * y), w, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        })
        Text("Filter", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink)
        if (on) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(OnNeon), contentAlignment = Alignment.Center) {
                Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = neon)
            }
        }
        Text("⌄", fontSize = 14.sp, color = ink, modifier = Modifier.rotate(turn).padding(bottom = 4.dp))
    }
}

/** What's ticked, as chips under the header. Tap one to take it off. */
@Composable
internal fun FilterChips(choice: Choice, masterSet: Boolean?, onChange: (Choice) -> Unit) {
    val neon = BatchNeon
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        masterSet?.let {
            Text(
                if (it) "Master set" else "Main set",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.Ink.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        @Composable
        fun chip(label: String, remove: () -> Unit) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(neon)
                    .clickable(role = Role.Button, onClick = remove)
                    .semantics { contentDescription = "Remove $label" }
                    .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnNeon, maxLines = 1)
                Box(Modifier.size(16.dp).clip(CircleShape).background(OnNeon.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Text("✕", fontSize = 9.sp, color = OnNeon)
                }
            }
        }
        choice.quick.forEach { q -> chip(q.label) { onChange(choice.toggle(q)) } }
        choice.picks.forEach { (s, vs) -> vs.forEach { v -> chip(v) { onChange(choice.toggle(s, v)) } } }
        if (choice.count > 1) {
            Text(
                "Clear", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button) { onChange(Choice()) }.padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

/** "3 here · 9 in binder" and Next match, just above the page arrows. */
@Composable
internal fun FilterMatchBar(here: Int, total: Int, onNext: (() -> Unit)?) {
    val neon = BatchNeon
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$here here · $total in binder",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Ink,
            modifier = Modifier.weight(1f),
        )
        val can = onNext != null
        Text(
            if (can) "Next match ›" else if (total == 0) "No matches" else "No other pages",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (can) OnNeon else AppColors.Muted,
            modifier = Modifier.clip(RoundedCornerShape(50))
                .background(if (can) neon else AppColors.Ink.copy(alpha = 0.08f))
                .then(if (can) Modifier.clickable(role = Role.Button) { onNext?.invoke() } else Modifier)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/**
 * The Filter menu that drops down from the header: Main/Master set, quick
 * picks, and lists you can tick several things in. The binder page behind
 * changes as you tick. Drawn over the page (call inside the screen's Box).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.FilterMenu(
    visible: Boolean,
    choice: Choice,
    onChange: (Choice) -> Unit,
    onClose: () -> Unit,
    masterSet: Boolean?,
    onMasterSet: (Boolean) -> Unit,
    items: List<FilterLogic.Item>,
    facts: Map<String, CardFact>,
    matching: Int,
    loading: Int,
    quickCounts: Map<Quick, Int>,
    sgd: (CardFact) -> Double?,
    rarityRank: (String) -> Int,
) {
    val neon = BatchNeon
    // The page darkens a little behind the menu; a tap there closes it.
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x66080418))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp
    AnimatedVisibility(
        visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f, transformOrigin = TransformOrigin(0.75f, 0f)),
        exit = fadeOut() + scaleOut(targetScale = 0.95f, transformOrigin = TransformOrigin(0.75f, 0f)),
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp, start = 8.dp, end = 8.dp).widthIn(max = 520.dp),
    ) {
        val shape = RoundedCornerShape(18.dp)
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight)
                .shadow(18.dp, shape).clip(shape).background(AppColors.Surface)
                .border(1.dp, AppColors.Border, shape)
                // Taps inside the menu shouldn't reach the page underneath.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filter", style = MaterialTheme.typography.titleLarge, color = AppColors.Ink, modifier = Modifier.weight(1f))
                if (choice.active) {
                    Text(
                        "Clear all", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onChange(Choice()) }.padding(8.dp),
                    )
                }
            }
            val open = remember { mutableStateMapOf(Section.Pokemon to true) }
            val search = remember { mutableStateMapOf<Section, String>() }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                masterSet?.let { m ->
                    SmallLabel("Count")
                    MasterSetToggle(m, onMasterSet)
                    Text(
                        (if (m) "Secret rares count too." else "Secret rares don't count.") + " Just on this phone.",
                        fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                    )
                }
                SmallLabel("Quick picks")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Quick.entries.forEach { q ->
                        val on = q in choice.quick
                        Row(
                            Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50))
                                .background(if (on) neon else AppColors.Surface)
                                .border(1.5.dp, if (on) neon else AppColors.Border, RoundedCornerShape(50))
                                .clickable(role = Role.Checkbox) { onChange(choice.toggle(q)) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(q.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) OnNeon else AppColors.Ink)
                            Text("${quickCounts[q] ?: 0}", fontSize = 11.sp, color = if (on) OnNeon.copy(alpha = 0.7f) else AppColors.Muted)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Section.entries.forEach { s ->
                    val options = remember(s, items, facts.size) { FilterLogic.options(s, items, facts, sgd, rarityRank) }
                    val picked = choice.picked(s)
                    val isOpen = open[s] == true
                    Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Border))
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable { open[s] = !isOpen }.padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (picked.isEmpty()) "Any" else picked.joinToString(", "),
                            fontSize = 13.sp,
                            fontWeight = if (picked.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                            color = if (picked.isEmpty()) AppColors.Muted else AppColors.AccentText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp).padding(end = 6.dp),
                        )
                        Text("›", fontSize = 18.sp, color = AppColors.Muted, modifier = Modifier.rotate(if (isOpen) 90f else 0f))
                    }
                    if (isOpen) {
                        Column(Modifier.padding(bottom = 10.dp)) {
                            val needsFacts = s == Section.Artist || s == Section.Type || s == Section.Kind || s == Section.Price
                            if (options.isEmpty()) {
                                Text(
                                    when {
                                        needsFacts && loading > 0 -> "Looking up card details… ($loading left)"
                                        needsFacts -> "No details yet. Check your internet connection."
                                        else -> "Nothing to pick in this binder."
                                    },
                                    fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(vertical = 4.dp),
                                )
                            } else if (s == Section.Type) {
                                TypePicker(options.map { it.first }.toSet(), picked) { onChange(choice.toggle(s, it)) }
                            } else {
                                if (needsFacts && loading > 0) {
                                    Text("Looking up card details… ($loading left)", fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.padding(bottom = 4.dp))
                                }
                                val q = search[s].orEmpty()
                                if (s.searchable && options.size > 6) {
                                    SearchBox(q, "Search ${s.title.lowercase()}") { search[s] = it }
                                }
                                options.filter { q.isBlank() || it.first.contains(q.trim(), ignoreCase = true) }.forEach { (v, n) ->
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp))
                                            .clickable(role = Role.Checkbox) { onChange(choice.toggle(s, v)) },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = v in picked,
                                            onCheckedChange = { onChange(choice.toggle(s, v)) },
                                            colors = CheckboxDefaults.colors(checkedColor = AppColors.Accent, uncheckedColor = AppColors.BorderStrong),
                                        )
                                        Text(v, fontSize = 14.sp, color = AppColors.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("$n", fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.padding(end = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Border))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (choice.active) "$matching card${if (matching == 1) "" else "s"} match${if (matching == 1) "es" else ""}" else "No filters yet",
                    fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.weight(1f),
                )
                Text(
                    if (choice.active) "Show $matching" else "Close",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnNeon,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(neon)
                        .clickable(role = Role.Button, onClick = onClose)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SmallLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
        color = AppColors.Muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
}

@Composable
private fun SearchBox(value: String, hint: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(shape).background(AppColors.Surface2)
            .border(1.5.dp, AppColors.Border, shape).padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = AppColors.Muted)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = AppColors.Ink),
            cursorBrush = SolidColor(AppColors.Accent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
        )
    }
}

/** The energy symbols as round buttons; types no card here has are faded. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypePicker(have: Set<String>, picked: Set<String>, onToggle: (String) -> Unit) {
    val neon = BatchNeon
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 6) {
        FilterLogic.Types.forEach { (name, argb, letter) ->
            val there = name in have
            val on = name in picked
            Column(
                Modifier.width(52.dp).clip(RoundedCornerShape(10.dp))
                    .then(if (there) Modifier.clickable(role = Role.Checkbox) { onToggle(name) } else Modifier)
                    .padding(vertical = 2.dp)
                    .then(if (there) Modifier else Modifier.background(Color.Transparent)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(34.dp)
                        .then(if (on) Modifier.drawBehind { drawCircle(neon.copy(alpha = 0.45f), radius = size.minDimension / 2 + 5f * density) } else Modifier)
                        .clip(CircleShape).background(Color(argb).copy(alpha = if (there) 1f else 0.25f))
                        .then(if (on) Modifier.border(2.5.dp, OnNeon, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Text(letter, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                Text(
                    name, fontSize = 10.5.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (there) AppColors.Ink else AppColors.Muted.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }
        }
    }
}
