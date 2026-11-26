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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextAlign
import com.hybrid87sg.bindertracker.data.CardPictures
import com.hybrid87sg.bindertracker.ui.components.CARD_ASPECT
import com.hybrid87sg.bindertracker.ui.components.CardImage
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/**
 * The round Filter button at the bottom right of the binder page, next to the
 * right page arrow (same size as the arrows). Glows neon with a count when on.
 */
@Composable
internal fun FilterIconButton(count: Int, open: Boolean, onClick: () -> Unit) {
    val neon = BatchNeon
    val on = count > 0
    Box(Modifier.size(52.dp)) {
        Box(
            Modifier.matchParentSize()
                .then(if (on) Modifier.drawBehind { drawCircle(neon.copy(alpha = 0.35f), radius = size.minDimension / 2 + 3f * density) }
                    else Modifier.shadow(3.dp, CircleShape, ambientColor = AppColors.Shadow, spotColor = AppColors.Shadow))
                .clip(CircleShape)
                .background(if (on) neon else if (open) AppColors.Surface2 else AppColors.Group)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = if (on) "Filter, $count on" else "Filter" },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(com.hybrid87sg.bindertracker.R.drawable.ic_filter),
                contentDescription = null, tint = if (on) OnNeon else AppColors.Ink, modifier = Modifier.size(20.dp),
            )
        }
        if (on) {
            // The count sits on the button's top-right edge.
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(20.dp).clip(CircleShape).background(OnNeon),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = neon)
            }
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
 * The Filter menu that rises from the Filter button (bottom right): Main/Master set, quick
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
    // Together = matches gathered onto results pages; false = in place.
    together: Boolean,
    onTogether: (Boolean) -> Unit,
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
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    AnimatedVisibility(
        visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 1f)),
        exit = fadeOut() + scaleOut(targetScale = 0.95f, transformOrigin = TransformOrigin(1f, 1f)),
        // Sits just above the bottom row (page arrows + Filter), lined up with its right edge.
        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().statusBarsPadding()
            .padding(bottom = 78.dp, start = 8.dp, end = 8.dp, top = 8.dp).widthIn(max = 520.dp),
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
            // Opened with filters on: just "Your filters" (+ Add another filter for the full list).
            // Ticking something in the full list keeps the full list showing.
            var adding by remember { mutableStateOf(false) }
            LaunchedEffect(visible) { if (visible) adding = false }
            val tick: (Choice) -> Unit = { adding = true; onChange(it) }
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
                SmallLabel("Show matches")
                TwoWaySwitch("Together", "In place", second = !together) { onTogether(!it) }
                Text(
                    if (together) "Matching cards gathered onto their own pages." else "Cards stay in their pockets; the others go dark.",
                    fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                )
                if (choice.active && !adding) {
                    SmallLabel("Your filters")
                    val picked = choice.quick.map { q -> Triple<String, String, () -> Unit>("Quick pick", q.label) { onChange(choice.toggle(q)) } } +
                        choice.picks.flatMap { (s, vs) -> vs.map { v -> Triple<String, String, () -> Unit>(s.title, v) { onChange(choice.toggle(s, v)) } } }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        picked.forEach { (title, value, remove) ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Surface2)
                                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(title, fontSize = 11.sp, color = AppColors.Muted)
                                    Text(value, fontSize = 14.sp, color = AppColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = remove)
                                        .semantics { contentDescription = "Remove $value" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(AppColors.Surface), contentAlignment = Alignment.Center) {
                                        com.hybrid87sg.bindertracker.ui.components.SmallIcon(com.hybrid87sg.bindertracker.R.drawable.ic_close, AppColors.Ink, size = 16.dp)
                                    }
                                }
                            }
                        }
                    }
                    Box(
                        Modifier.padding(top = 8.dp, bottom = 10.dp).fillMaxWidth().heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, AppColors.AccentText.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { adding = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("+ Add another filter", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.AccentText) }
                } else {
                if (choice.active) {
                    Text(
                        "‹ Back to your filters", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.AccentText,
                        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { adding = false }.padding(vertical = 6.dp),
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
                                .clickable(role = Role.Checkbox) { tick(choice.toggle(q)) }
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
                        com.hybrid87sg.bindertracker.ui.components.SmallIcon(com.hybrid87sg.bindertracker.R.drawable.ic_chevron_right, AppColors.Muted, Modifier.rotate(if (isOpen) 90f else 0f))
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
                                TypePicker(options.map { it.first }.toSet(), picked) { tick(choice.toggle(s, it)) }
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
                                            .clickable(role = Role.Checkbox) { tick(choice.toggle(s, v)) },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = v in picked,
                                            onCheckedChange = { tick(choice.toggle(s, v)) },
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

/** A two-way switch with a neon thumb, like Main set / Master set. */
@Composable
private fun TwoWaySwitch(first: String, secondLabel: String, second: Boolean, onChange: (Boolean) -> Unit) {
    val neon = BatchNeon
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(shape).background(AppColors.Ink.copy(alpha = 0.08f))
            .border(1.dp, AppColors.BorderStrong, shape).padding(3.dp),
    ) {
        listOf(first to false, secondLabel to true).forEach { (label, value) ->
            val on = value == second
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(shape)
                    .background(if (on) neon else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onChange(value) },
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) OnNeon else AppColors.Muted) }
        }
    }
}

/**
 * The matching cards gathered together: packed in order onto results pages
 * the size of the binder's pages, in a striped frame so it's clear these
 * aren't the binder's real pages. Tap a card to open it; tap its "P4" tag to
 * see it in its real pocket.
 */
@Composable
internal fun FilterResults(
    items: List<FilterLogic.Item>,
    columns: Int,
    rows: Int,
    pageNumberOf: (String) -> Int,
    onOpen: (FilterLogic.Item) -> Unit,
    onShowInBinder: (FilterLogic.Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val neon = BatchNeon
    val perPage = (columns * rows).coerceAtLeast(1)
    val pages = items.chunked(perPage)
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(items.size) { page = page.coerceAtMost((pages.size - 1).coerceAtLeast(0)) }
    val shown = pages.getOrNull(page).orEmpty()
    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.TopCenter) {
            val gap = 8.dp
            val label = 34.dp
            val frame = 12.dp
            val byWidth = (maxWidth - frame * 2 - gap * (columns - 1)) / columns
            val byHeight = ((maxHeight - frame * 2 - gap * (rows - 1)) / rows - label) * CARD_ASPECT
            val cell = minOf(byWidth, byHeight).coerceAtLeast(40.dp)
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier.clip(shape)
                    .drawBehind {
                        // Purple stripes: these are results, not the binder's real pages.
                        drawRect(neon)
                        val w = 10.dp.toPx()
                        var x = -size.height
                        while (x < size.width) {
                            drawLine(neon.copy(alpha = 0.6f).compositeOverDark(), Offset(x, size.height), Offset(x + size.height, 0f), w)
                            x += w * 2
                        }
                    }
                    .padding(frame)
                    .clip(RoundedCornerShape(10.dp)).background(AppColors.Surface)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                if (items.isEmpty()) {
                    Box(Modifier.width(cell * columns + gap * (columns - 1)).height(160.dp), contentAlignment = Alignment.Center) {
                        Text("Nothing matches. Try removing a filter.", color = AppColors.Muted, fontSize = 14.sp)
                    }
                }
                shown.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { item ->
                            Column(Modifier.width(cell)) {
                                Box {
                                    CardImage(
                                        item.thumb,
                                        contentDescription = item.name + if (item.owned) "" else ", missing",
                                        modifier = Modifier.width(cell).aspectRatio(CARD_ASPECT)
                                            .clickable(role = Role.Button) { onOpen(item) },
                                        backupUrl = CardPictures.backupUrl(item.cardId, large = false),
                                        cardId = item.cardId,
                                        grey = !item.owned,
                                    )
                                    val n = pageNumberOf(item.pageId)
                                    Text(
                                        "P$n", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnNeon,
                                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp)
                                            .clip(RoundedCornerShape(50)).background(neon)
                                            .clickable(role = Role.Button) { onShowInBinder(item) }
                                            .semantics { contentDescription = "Show in binder, page $n" }
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text(item.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (item.owned) AppColors.Ink else AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Page ${pageNumberOf(item.pageId)}", fontSize = 11.sp, color = AppColors.AccentText, maxLines = 1)
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.width(cell)) }
                    }
                }
            }
        }
        // Results 1 of 3, with arrows.
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ResultsArrow(com.hybrid87sg.bindertracker.R.drawable.ic_chevron_left, "Previous results", page > 0) { page-- }
            Text(
                if (pages.isEmpty()) "No results" else "Results ${page + 1} of ${pages.size}",
                style = MaterialTheme.typography.bodyLarge, color = AppColors.Ink,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            ResultsArrow(com.hybrid87sg.bindertracker.R.drawable.ic_chevron_right, "Next results", page < pages.size - 1) { page++ }
        }
    }
}

@Composable
private fun ResultsArrow(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(50.dp).clip(CircleShape).background(AppColors.Surface2.copy(alpha = if (enabled) 1f else 0.4f))
            .border(1.dp, AppColors.Border, CircleShape)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { com.hybrid87sg.bindertracker.ui.components.SmallIcon(icon, AppColors.Ink.copy(alpha = if (enabled) 1f else 0.35f), size = 24.dp) }
}

/** A slightly darker stripe colour, so the stripes show on the neon. */
private fun Color.compositeOverDark(): Color = Color(red * 0.82f, green * 0.82f, blue * 0.9f, 1f)
