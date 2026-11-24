package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import com.hybrid87sg.bindertracker.ui.theme.windowBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.hybrid87sg.bindertracker.data.BinderFill
import com.hybrid87sg.bindertracker.data.ChecklistProgress
import com.hybrid87sg.bindertracker.ui.components.holoBrush
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import com.hybrid87sg.bindertracker.ui.components.Eyebrow
import com.hybrid87sg.bindertracker.ui.components.Motion
import com.hybrid87sg.bindertracker.ui.components.StatTile
import com.hybrid87sg.bindertracker.ui.components.arrive
import com.hybrid87sg.bindertracker.ui.components.countUp
import com.hybrid87sg.bindertracker.ui.components.pressSquish
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.ui.components.ShelfPlank
import com.hybrid87sg.bindertracker.ui.components.SpineArt
import com.hybrid87sg.bindertracker.ui.components.colorFromHex
import com.hybrid87sg.bindertracker.ui.components.verticalText
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.hybrid87sg.bindertracker.data.CoverPicture
import com.hybrid87sg.bindertracker.data.ShelfLook
import com.hybrid87sg.bindertracker.ui.components.BinderTurn
import com.hybrid87sg.bindertracker.ui.components.CoverLook
import com.hybrid87sg.bindertracker.ui.components.ShelfFit
import com.hybrid87sg.bindertracker.ui.components.rememberCoverBitmaps
import com.hybrid87sg.bindertracker.ui.components.turnedBinder

private const val SPINES_PER_ROW = 4
private val SpineHeight = 280.dp

/** One slot on a shelf row: either a binder, or the "Add binder" button. */
private sealed interface ShelfItem {
    data class BinderItem(val binder: Binder) : ShelfItem
    data object AddItem : ShelfItem
}

@Composable
fun ShelfScreen(
    binders: List<Binder>,
    onOpenBinder: (Binder) -> Unit,
    onChangeColor: (Binder, String) -> Unit,
    onAddBinder: () -> Unit,
    // Null hides the button (the tab bar has Search and Me instead).
    onOpenAccount: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    // How full each binder is, by binder ID (for the bars and totals).
    fill: Map<String, BinderFill> = emptyMap(),
    // A short green note, e.g. "3 binders added to your account.", or null.
    notice: String? = null,
    onDismissNotice: () -> Unit = {},
    // Opens the Join screen (a binder a friend shared). Null hides the button.
    onJoin: (() -> Unit)? = null,
    // Missing cards per collecting binder, by binder ID, for the badges.
    missing: Map<String, ChecklistProgress> = emptyMap(),
    // How the binders stand: straight, or turned to show their covers (Edit shelf).
    look: ShelfLook = ShelfLook(),
    // Your own cover pictures, by binder ID (shown when the binders are turned).
    covers: Map<String, CoverPicture> = emptyMap(),
    // Opens Edit shelf. Null hides the pencil button.
    onEditShelf: (() -> Unit)? = null,
    // Hold a binder: opens Edit binder (only for binders you can edit).
    onEditBinder: (Binder) -> Unit = {},
    // Each binder's first page, by binder ID: the 3D binder opens onto it.
    firstPages: Map<String, com.hybrid87sg.bindertracker.ui.three.SheetLook> = emptyMap(),
    // A 3D binder opened onto its first page (binder, page ID). Null: open the binder as usual.
    onOpenPage: ((Binder, String) -> Unit)? = null,
) {
    val coverBitmaps = rememberCoverBitmaps(covers)
    // Real 3D binders, when switched on and this phone can run the 3D engine.
    val use3D = look.threeD && binders.isNotEmpty() && com.hybrid87sg.bindertracker.ui.three.Engine3D.ready
    // How far a binder has got opening (0 to 1); the title fades out as it opens.
    var opening by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val headerFade = Modifier.graphicsLayer { alpha = (1f - opening * 2.2f).coerceIn(0f, 1f) }
    // The slide-in plays once each time the app starts, not every time you come back.
    val playIntro = remember { !ShelfIntro.played }
    SideEffect { ShelfIntro.played = true }
    val cards = binders.sumOf { fill[it.id]?.filled ?: 0 }
    val pockets = binders.sumOf { fill[it.id]?.total ?: 0 }
    // All binders, then the "Add binder" slot, cut into rows of 4.
    val items: List<ShelfItem> = binders.map { ShelfItem.BinderItem(it) } + ShelfItem.AddItem
    val rows = items.chunked(SPINES_PER_ROW)

    // Where the title and totals end (in the window), so the 3D shelf keeps its binders below them.
    var headerBottom by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().windowBackground()) {
        if (use3D) {
            val cardBack = com.hybrid87sg.bindertracker.ui.components.LocalCardBack.current.choice
            val context = androidx.compose.ui.platform.LocalContext.current
            val screen = androidx.compose.ui.platform.LocalConfiguration.current
            val sheetColor = AppColors.Surface.toArgb()
            val dir = if (look.angle == 0 || look.coversOnRight) 1 else -1
            val looks = binders.map { b ->
                val c = covers[b.id]
                com.hybrid87sg.bindertracker.ui.three.BinderLook3D(
                    id = b.id,
                    name = b.name,
                    tag = if (b.isShared) "Shared" else b.sizeLabel,
                    colorHex = b.colorHex,
                    fill = fill[b.id]?.fraction ?: 0f,
                    dir = dir,
                    picturePath = c?.path,
                    pictureVersion = c?.version ?: 0L,
                    placement = c?.placement ?: com.hybrid87sg.bindertracker.data.CoverPlacement(),
                    // Drawn where the page screen will show its cards (see PageLanding),
                    // so the camera lands on them exactly and the page screen takes over seamlessly.
                    sheet = firstPages[b.id]?.let { s ->
                        val key = com.hybrid87sg.bindertracker.ui.three.PageLanding.key(s.columns, s.rows, screen.screenWidthDp, screen.screenHeightDp)
                        s.copy(landing = com.hybrid87sg.bindertracker.ui.three.PageLanding.get(context, key), surface = sheetColor)
                    },
                    back = cardBack,
                )
            }
            com.hybrid87sg.bindertracker.ui.three.Shelf3D(
                binders = looks,
                look = look,
                colors = com.hybrid87sg.bindertracker.ui.three.ShelfColors(AppColors.Wood, AppColors.ShelfBackground, AppColors.Paper),
                onOpen = { id, pageId ->
                    binders.firstOrNull { it.id == id }?.let { b ->
                        if (pageId != null && onOpenPage != null) onOpenPage(b, pageId) else onOpenBinder(b)
                    }
                },
                onEdit = { id -> binders.firstOrNull { it.id == id }?.let { if (it.canEdit) onEditBinder(it) else onOpenBinder(it) } },
                onOpening = { opening = it },
                // The whole screen, behind the title (binders stay below it), so the
                // opening binder can glide right up to where the page screen draws its page.
                headerBottom = headerBottom,
                modifier = Modifier.fillMaxSize(),
            )
        }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            headerFade.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("Your collection")
                Text("My binders", style = MaterialTheme.typography.headlineLarge)
            }
            if (use3D) {
                // The 3D shelf has no empty "Add binder" slot, so it's a button up here.
                val addPress = remember { MutableInteractionSource() }
                Surface(
                    onClick = onAddBinder,
                    interactionSource = addPress,
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.Surface,
                    contentColor = AppColors.Ink,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border),
                    modifier = Modifier.size(48.dp).pressSquish(addPress).semantics { contentDescription = "Add binder" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            if (onEditShelf != null) {
                val editPress = remember { MutableInteractionSource() }
                Surface(
                    onClick = onEditShelf,
                    interactionSource = editPress,
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.Surface,
                    contentColor = AppColors.Ink,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border),
                    modifier = Modifier.size(48.dp).pressSquish(editPress).semantics { contentDescription = "Edit shelf" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            if (onJoin != null) {
                val joinPress = remember { MutableInteractionSource() }
                Surface(
                    onClick = onJoin,
                    interactionSource = joinPress,
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.Surface,
                    contentColor = AppColors.Ink,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border),
                    modifier = Modifier.size(48.dp).pressSquish(joinPress).semantics { contentDescription = "Join a shared binder" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_qr), contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            if (onSearch != null) {
            val searchPress = remember { MutableInteractionSource() }
            Surface(
                onClick = onSearch,
                interactionSource = searchPress,
                shape = RoundedCornerShape(14.dp),
                color = AppColors.Surface,
                contentColor = AppColors.Ink,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border),
                modifier = Modifier.size(48.dp).pressSquish(searchPress).semantics { contentDescription = "Search cards" },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            }
            if (onOpenAccount != null) {
            val accountPress = remember { MutableInteractionSource() }
            Surface(
                onClick = onOpenAccount,
                interactionSource = accountPress,
                shape = CircleShape,
                color = AppColors.Accent,
                contentColor = AppColors.White,
                modifier = Modifier.size(48.dp).pressSquish(accountPress).semantics { contentDescription = "Account" },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_person), contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            }
        }

        if (notice != null) {
            // The note goes away by itself after 6 seconds, or when tapped.
            LaunchedEffect(notice) {
                delay(6_000)
                onDismissNotice()
            }
            SuccessNotice(notice, onDismissNotice)
        }

        // Totals: cards in pockets, binders, and how full they are overall.
        val statsIn = arrive(playIntro, durationMillis = Motion.Medium)
        Row(
            headerFade.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                .graphicsLayer { alpha = statsIn; translationY = (1f - statsIn) * 12.dp.toPx() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile("${countUp(cards, 150)}", if (cards == 1) "card" else "cards", Modifier.weight(1f), foil = true)
            StatTile("${countUp(binders.size, 150)}", if (binders.size == 1) "binder" else "binders", Modifier.weight(1f))
            StatTile("${countUp(if (pockets == 0) 0 else cards * 100 / pockets, 150)}%", "filled", Modifier.weight(1f))
        }
        Text(
            if (binders.isEmpty()) "No binders yet · add your first one" else "Tap to open, hold to edit",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Muted,
            modifier = headerFade.padding(start = 20.dp, end = 20.dp, top = 6.dp)
                .onGloballyPositioned { headerBottom = it.boundsInWindow().bottom },
        )

        if (!use3D) {
        // LazyColumn only builds the rows that are on screen, so a shelf
        // with many binders still scrolls smoothly.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            itemsIndexed(rows) { rowIndex, row ->
                // Each shelf is a soft box the gradient shows through, shaded at the top like a real shelf.
                Column(
                    Modifier.padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.ShelfBackground.copy(alpha = 0.7f))
                        .drawBehind {
                            drawRect(
                                Brush.verticalGradient(listOf(AppColors.Shadow.copy(alpha = AppColors.Shadow.alpha * 0.6f), Color.Transparent), endY = 18.dp.toPx()),
                            )
                        }
                        .padding(top = 16.dp),
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth().height(SpineHeight).padding(horizontal = 12.dp)) {
                    val setup = shelfSetup(look, maxWidth, SPINES_PER_ROW)
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(setup.gap),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // Covers on the left stick out past the first binder, so leave room for them.
                        if (setup.turn != null && !look.coversOnRight) Spacer(Modifier.width((setup.reach - setup.gap).coerceAtLeast(0.dp)))
                        row.forEachIndexed { i, item ->
                            // Each binder slides up onto the shelf, one after another.
                            val order = rowIndex * SPINES_PER_ROW + i
                            val inT = arrive(playIntro, delayMillis = 80 + order * 70, durationMillis = 460)
                            val slide = Modifier
                                // The binder nearer you stands in front of its neighbour's cover.
                                .zIndex(if (look.coversOnRight) i.toFloat() else -i.toFloat())
                                .graphicsLayer {
                                    alpha = inT
                                    translationY = (1f - inT) * 48.dp.toPx()
                                    // Fade without an off-screen picture, so the cover isn't cut off.
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                }
                            when (item) {
                                is ShelfItem.BinderItem -> Box(slide) {
                                    BinderSpine(
                                        item.binder,
                                        fill = fill[item.binder.id]?.fraction ?: 0f,
                                        grow = inT,
                                        setup = setup,
                                        cover = coverLook(item.binder.name, item.binder.colorHex, coverBitmaps[item.binder.id], covers[item.binder.id]),
                                        onClick = { onOpenBinder(item.binder) },
                                        // Hold: Edit binder, for binders you can edit.
                                        onLongClick = { if (item.binder.canEdit) onEditBinder(item.binder) },
                                        picture = coverBitmaps[item.binder.id],
                                    )
                                    missing[item.binder.id]?.takeIf { it.total > 0 }?.let { p ->
                                        MissingBadge(p.missing, pop = inT, modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-8).dp))
                                    }
                                }
                                ShelfItem.AddItem -> AddBinderSpine(
                                    onAddBinder,
                                    // Stand clear of the last binder's cover.
                                    slide.padding(start = if (setup.turn != null && look.coversOnRight) setup.reach else 0.dp),
                                    width = setup.spineWidth,
                                    height = setup.spineHeight,
                                )
                            }
                        }
                    }
                    }
                    ShelfPlank(sideMargin = 0.dp)
                }
            }
        }
        }
    }
    }
}

/** The little foil-rimmed number on a spine: cards still missing, or a green tick when complete. */
@Composable
private fun MissingBadge(count: Int, pop: Float, modifier: Modifier = Modifier) {
    val done = count == 0
    Box(
        modifier
            .graphicsLayer { val s = pop.coerceIn(0f, 1f); scaleX = s; scaleY = s }
            .semantics { contentDescription = if (done) "Complete" else "$count cards missing" }
            .clip(RoundedCornerShape(50))
            .drawBehind { drawRect(holoBrush(size.width)) }
            .padding(2.dp)
            .clip(RoundedCornerShape(50))
            .background(if (done) AppColors.Success else AppColors.White)
            .heightIn(min = 20.dp)
            .widthIn(min = 22.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (done) "✓" else if (count > 999) "999+" else "$count",
            color = if (done) AppColors.White else AppColors.Black,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun BinderSpine(
    binder: Binder,
    fill: Float,
    grow: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    setup: ShelfSetup? = null,
    cover: CoverLook? = null,
    picture: ImageBitmap? = null,
) {
    val turn = setup?.turn
    val haptics = LocalHapticFeedback.current
    // Pressing a binder lifts it off the shelf a little, like pulling it out.
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val lift by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "lift")
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
    // "combinedClickable" listens for both a normal tap and a tap and hold.
    // The little buzz on hold tells you the press was noticed.
    SpineArt(
        name = binder.name,
        // Shared binders say so at the bottom of the spine (the full story is inside the binder).
        tag = if (binder.isShared) "Shared" else binder.sizeLabel,
        color = colorFromHex(binder.colorHex),
        width = setup?.binderWidth ?: 64.dp,
        height = setup?.spineHeight ?: SpineHeight,
        // The fill bar grows as the binder arrives on the shelf.
        fill = fill * grow,
        picture = picture,
        modifier = modifier
            .graphicsLayer {
                translationY = -lift * 12.dp.toPx()
                scaleX = 1f + lift * 0.05f
                scaleY = 1f + lift * 0.05f
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            // The binder's shadow on the shelf (the cover is clipped to its shape inside it).
            // A turned binder draws its own shadow, and its cover reaches outside this box.
            .then(if (turn == null) Modifier.shadow(6.dp, shape, clip = true, ambientColor = AppColors.Shadow, spotColor = AppColors.Shadow) else Modifier)
            .combinedClickable(
                interactionSource = press,
                indication = LocalIndication.current,
                onClickLabel = "Open",
                onLongClickLabel = "Edit binder",
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .semantics {
                contentDescription = "Binder ${binder.name}, ${binder.sizeLabel}" +
                    if (binder.isOwner) { if (binder.isShared) ", shared" else "" } else ", shared by ${binder.ownerName ?: "a friend"}"
            }
            .then(if (turn != null && cover != null) Modifier.turnedBinder(turn, cover) else Modifier),
    )
}

/** Sizes for one shelf row: how big the binders are, the gaps, and how they're turned (null = straight). */
internal class ShelfSetup(
    val turn: BinderTurn?,
    val binderWidth: Dp,
    val spineWidth: Dp,
    val spineHeight: Dp,
    val gap: Dp,
    val reach: Dp,
)

/** Works out [ShelfSetup] for a shelf [available] wide holding [perRow] binders. */
@Composable
internal fun shelfSetup(look: ShelfLook, available: Dp, perRow: Int): ShelfSetup {
    val density = LocalDensity.current
    return remember(look, available, perRow, density) {
        with(density) {
            val fit = ShelfFit.of(
                angleDegrees = look.angle.toFloat(),
                coverOnRight = look.coversOnRight,
                width = 64.dp.toPx(),
                height = SpineHeight.toPx(),
                gapStraight = 8.dp.toPx(),
                gapExtra = 40.dp.toPx(),
                available = available.toPx(),
                perRow = perRow,
            )
            val w = 64.dp.toPx() * fit.scale
            val h = SpineHeight.toPx() * fit.scale
            ShelfSetup(
                turn = if (look.turned) BinderTurn.of(w, h, look.angle.toFloat(), look.coversOnRight) else null,
                binderWidth = w.toDp(),
                spineWidth = fit.spineWidth.toDp(),
                spineHeight = h.toDp(),
                gap = fit.gap.toDp(),
                reach = fit.coverReach.toDp(),
            )
        }
    }
}

/** What to paint on a binder's cover. */
internal fun coverLook(name: String, colorHex: String, picture: ImageBitmap?, saved: CoverPicture?) = CoverLook(
    color = colorFromHex(colorHex),
    name = name,
    picture = picture,
    placement = saved?.placement ?: com.hybrid87sg.bindertracker.data.CoverPlacement(),
)

/** A pretend binder for the previews on Edit shelf and Edit binder. */
class PreviewBinder(val name: String, val tag: String, val colorHex: String, val fill: Float?, val picture: ImageBitmap? = null, val placement: com.hybrid87sg.bindertracker.data.CoverPlacement = com.hybrid87sg.bindertracker.data.CoverPlacement())

/**
 * A small shelf showing [binders] standing the way [look] says, for previews.
 * [perRow] sets how many would fit on a row (fewer = bigger binders).
 */
@Composable
internal fun ShelfPreview(binders: List<PreviewBinder>, look: ShelfLook, perRow: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.ShelfBackground.copy(alpha = 0.7f))
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(AppColors.Shadow.copy(alpha = AppColors.Shadow.alpha * 0.6f), Color.Transparent), endY = 18.dp.toPx()))
            }
            .padding(top = 16.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(SpineHeight).padding(horizontal = 12.dp)) {
            val setup = shelfSetup(look, maxWidth, perRow)
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(setup.gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (setup.turn != null && !look.coversOnRight) Spacer(Modifier.width((setup.reach - setup.gap).coerceAtLeast(0.dp)))
                binders.forEachIndexed { i, b ->
                    val cover = CoverLook(colorFromHex(b.colorHex), b.name, b.picture, b.placement)
                    SpineArt(
                        name = b.name,
                        tag = b.tag,
                        color = colorFromHex(b.colorHex),
                        width = setup.binderWidth,
                        height = setup.spineHeight,
                        fill = b.fill,
                        picture = b.picture,
                        modifier = Modifier
                            .zIndex(if (look.coversOnRight) i.toFloat() else -i.toFloat())
                            .then(if (setup.turn != null) Modifier.turnedBinder(setup.turn, cover) else Modifier),
                    )
                }
                if (setup.turn != null && look.coversOnRight) Spacer(Modifier.width((setup.reach - setup.gap).coerceAtLeast(0.dp)))
            }
        }
        ShelfPlank(sideMargin = 0.dp)
    }
}

@Composable
private fun AddBinderSpine(onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 64.dp, height: Dp = SpineHeight) {
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = press,
        shape = RoundedCornerShape(6.dp),
        // Transparent, so the dashed outline drawn underneath shows through.
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = Color4A,
        modifier = modifier
            .pressSquish(press)
            .width(width)
            .height(height)
            .dashedBorder()
            .semantics { contentDescription = "Add binder" },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(14.dp))
            Text("Add binder", modifier = Modifier.verticalText(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private val Color4A get() = AppColors.Ink

/** Remembers, while the app is open, that the shelf's slide-in has already played. */
private object ShelfIntro {
    var played = false
}

/** Draws a dashed outline, like the empty slot in the prototype. */
private fun Modifier.dashedBorder(): Modifier = drawBehind {
    val stroke = 2.dp.toPx()
    drawRoundRect(
        color = AppColors.DashedBorder,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
}

/** A green "done" note with a tick, shown under the shelf's title. */
@Composable
private fun SuccessNotice(text: String, onDismiss: () -> Unit) {
    Surface(
        onClick = onDismiss,
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface,
        contentColor = AppColors.Ink,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Success),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(AppColors.Success),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = AppColors.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text, style = MaterialTheme.typography.titleSmall, color = AppColors.SuccessText)
                Text("Everything you made on this phone is kept.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
        }
    }
}
