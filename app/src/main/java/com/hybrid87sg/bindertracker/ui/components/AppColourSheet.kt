package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.AppColourMath
import com.hybrid87sg.bindertracker.ui.theme.AppColourSetting
import com.hybrid87sg.bindertracker.ui.theme.ColourChoice
import com.hybrid87sg.bindertracker.ui.theme.DisplayFont
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The rainbow foil colours used on the glitter bar and its gem. */
private val FOIL = listOf(Color(0xFFFF9AD5), Color(0xFFFFD36E), Color(0xFF8CF0C8), Color(0xFF7FB7FF), Color(0xFFC59BFF))

/** The button colour for one pick, shown on its tab dot, the gems and the row's orb. */
private fun gemColour(c: ColourChoice): Color =
    Color(AppColourMath.build(listOf(c.copy(strength = maxOf(c.strength, 0.5f))), dark = false).accent)

/** How the colour ring shows a hue: bright and clear, the same in light and dark mode. */
private fun ringColour(hue: Float): Color = Color(AppColourMath.hsl(hue, 78f, 58f))

/**
 * The "App colour" row on the Me tab: an orb showing your colours and their
 * names. Tapping it opens [AppColourSheet].
 */
@Composable
fun AppColourRow(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colours = AppColourSetting.colours ?: listOf(AppColourMath.CLASSIC)
    val press = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .pressSquish(press)
            .clip(shape)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Border, shape)
            .clickable(interactionSource = press, indication = LocalIndication.current) { open = true }
            .padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColourOrb(colours, Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(colours.joinToString(" + ") { AppColourMath.nameFor(it.hue) }, style = MaterialTheme.typography.titleMedium)
            Text("App colours and glitter, on this phone only", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
        Text("›", fontSize = 26.sp, color = AppColors.Muted)
    }
    if (open) AppColourSheet(onClose = { open = false })
}

/** A glossy round gem filled with your colours, ringed with foil. */
@Composable
private fun ColourOrb(colours: List<ColourChoice>, modifier: Modifier) {
    val fills = colours.map { gemColour(it) }
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(FOIL + FOIL.first(), center), radius = r)
        val inner = r - 2.5.dp.toPx()
        val brush = if (fills.size == 1) Brush.linearGradient(listOf(fills[0], fills[0]))
        else Brush.linearGradient(fills, start = Offset(0f, 0f), end = Offset(size.width, size.height))
        drawCircle(brush, radius = inner)
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Transparent), center = Offset(size.width * 0.34f, size.height * 0.3f), radius = inner * 0.6f),
            radius = inner,
        )
    }
}

/**
 * The colour sheet: tabs for up to four colours, the foil colour ring with its
 * strength ring, ready-made gems, the glitter bar, and Reset / Use colour.
 * Everything re-tints live while you change it; closing without "Use colour"
 * puts the old colours back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppColourSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val startColours = remember { AppColourSetting.colours }
    val startGlitter = remember { AppColourSetting.glitter }
    var colours by remember { mutableStateOf(startColours ?: listOf(AppColourMath.CLASSIC)) }
    var active by remember { mutableIntStateOf(0) }
    var glitter by remember { mutableFloatStateOf(startGlitter) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun preview() = AppColourSetting.show(colours, glitter)

    ModalBottomSheet(
        onDismissRequest = { AppColourSetting.show(startColours, startGlitter); onClose() },
        sheetState = sheetState,
        containerColor = AppColors.Surface,
        // A light dim, so you can still see the app re-tint behind the sheet.
        scrimColor = Color.Black.copy(alpha = 0.18f),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("App colour", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "Only on this phone",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted,
                    modifier = Modifier.border(1.dp, AppColors.Border, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }

            ColourTabs(
                colours = colours,
                active = active,
                onSelect = { active = it },
                onAdd = {
                    colours = colours + ColourChoice((colours.last().hue + 60f) % 360f, colours.first().strength)
                    active = colours.lastIndex
                    preview()
                },
                onRemove = { i ->
                    colours = colours.filterIndexed { j, _ -> j != i }
                    if (active >= i) active = (active - 1).coerceAtLeast(0)
                    preview()
                },
            )
            Text("Inner ring: colour. Outer ring: strength.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)

            ColourWheel(
                choice = colours[active],
                isMain = active == 0,
                onChange = { c ->
                    colours = colours.toMutableList().also { it[active] = c }
                    preview()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            PresetGems(selectedHue = colours[active].hue, onPick = { hue ->
                colours = colours.toMutableList().also { it[active] = it[active].copy(hue = hue) }
                preview()
            })

            GlitterBar(glitter, onChange = { glitter = it; preview() })

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val resetPress = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = {
                        colours = listOf(AppColourMath.CLASSIC); active = 0; glitter = 0f
                        preview()
                    },
                    interactionSource = resetPress,
                    modifier = Modifier.weight(1f).height(52.dp).pressSquish(resetPress),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(2.dp, AppColors.Ink),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
                ) { Text("Reset", style = MaterialTheme.typography.labelLarge) }
                val usePress = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        AppColourSetting.save(context, colours, glitter)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() }
                    },
                    interactionSource = usePress,
                    modifier = Modifier.weight(1.4f).height(52.dp).pressSquish(usePress),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.White),
                ) { Text("Use colour", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

private val TAB_NAMES = listOf("Main", "2nd", "3rd", "4th")

/** Main, plus a tab for each extra background colour (× removes it), then "+ Add colour". */
@Composable
private fun ColourTabs(
    colours: List<ColourChoice>,
    active: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        colours.forEachIndexed { i, c ->
            val selected = i == active
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier
                    .height(38.dp)
                    .clip(shape)
                    .background(if (selected) AppColors.Surface2 else Color.Transparent)
                    .border(if (selected) 2.dp else 1.dp, if (selected) AppColors.AccentText else AppColors.Border, shape)
                    .clickable { onSelect(i) }
                    .padding(start = 10.dp, end = if (i == 0) 12.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(gemColour(c)))
                Spacer(Modifier.width(7.dp))
                Text(TAB_NAMES[i], fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = if (selected) AppColors.Ink else AppColors.Muted)
                if (i > 0) {
                    Box(
                        Modifier.padding(start = 2.dp).size(28.dp).clip(CircleShape)
                            .clickable { onRemove(i) }
                            .semantics { contentDescription = "Remove ${TAB_NAMES[i]} colour" },
                        contentAlignment = Alignment.Center,
                    ) { Text("×", fontSize = 16.sp, color = AppColors.Muted) }
                }
            }
        }
        if (colours.size < AppColourMath.MAX_COLOURS) {
            Box(
                Modifier.height(38.dp).clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, AppColors.BorderStrong, RoundedCornerShape(12.dp))
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("+ Add colour", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.AccentText) }
        }
    }
}

/** A point [radius] away from [c], at [topDegrees] measured clockwise from the top. */
private fun polar(c: Offset, radius: Float, topDegrees: Float): Offset {
    val a = Math.toRadians(topDegrees.toDouble())
    return Offset(c.x + radius * sin(a).toFloat(), c.y - radius * cos(a).toFloat())
}

/** The angle of [p] around [c], clockwise from the top, 0 to 360. */
private fun topAngle(p: Offset, c: Offset): Float {
    val deg = Math.toDegrees(atan2((p.x - c.x).toDouble(), (-(p.y - c.y)).toDouble())).toFloat()
    return (deg + 360f) % 360f
}

// The strength ring runs clockwise from the bottom-left (210°) for 300°, leaving a gap at the bottom.
private const val STRENGTH_START = 210f
private const val STRENGTH_SWEEP = 300f

/**
 * The foil colour ring. Drag around the inner rainbow ring to pick the colour
 * (it snaps onto the eight ready-made colours), or around the outer ring to
 * go from Soft to Vivid. The middle shows the background and the colour's name.
 */
@Composable
private fun ColourWheel(choice: ColourChoice, isMain: Boolean, onChange: (ColourChoice) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(choice)
    val change by rememberUpdatedState(onChange)
    val dark = AppColors.isDark
    val accent = if (isMain) AppColors.Accent else Color(AppColourMath.build(listOf(choice), dark).accent)

    Box(
        modifier
            .size(252.dp)
            .semantics {
                contentDescription = "Colour ring"
                stateDescription = "${AppColourMath.nameFor(choice.hue)}, ${AppColourMath.strengthWord(choice.strength)}"
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val s = size.width.toFloat()
                    val c = Offset(s / 2f, s / 2f)
                    // Touching the outer band moves strength; anywhere inside it moves the colour.
                    val onStrength = (down.position - c).getDistance() / s > 0.42f
                    fun handle(p: Offset) {
                        val angle = topAngle(p, c)
                        if (onStrength) {
                            var a = (angle - STRENGTH_START + 360f) % 360f
                            if (a > STRENGTH_SWEEP) a = if (a > 330f) 0f else STRENGTH_SWEEP
                            change(current.copy(strength = ((a / STRENGTH_SWEEP) * 100f).roundToInt() / 100f))
                        } else {
                            var hue = angle.roundToInt().toFloat()
                            val near = AppColourMath.PRESETS.firstOrNull { AppColourMath.hueDistance(it.hue, hue) < 5f }
                            if (near != null) {
                                if (current.hue != near.hue) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                hue = near.hue
                            }
                            change(current.copy(hue = hue % 360f))
                        }
                    }
                    handle(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        handle(ch.position)
                        ch.consume()
                    }
                }
            },
    ) {
        val background = AppColors.BackgroundStops
        val border = AppColors.Border
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width
            val c = center
            // Outer strength ring: from a soft grey-ish version of the colour to the vivid one.
            val soft = Color(AppColourMath.hsl(choice.hue, if (dark) 12f else 14f, if (dark) 42f else 80f))
            val vivid = Color(AppColourMath.hsl(choice.hue, 100f, if (dark) 56f else 50f))
            val rp = s * 0.468f
            val steps = 60
            for (i in 0 until steps) {
                val t = i / (steps - 1f)
                drawArc(
                    color = androidx.compose.ui.graphics.lerp(soft, vivid, t),
                    startAngle = STRENGTH_START + i * (STRENGTH_SWEEP / steps) - 90f,
                    sweepAngle = STRENGTH_SWEEP / steps + 0.6f,
                    useCenter = false,
                    topLeft = Offset(c.x - rp, c.y - rp),
                    size = Size(rp * 2, rp * 2),
                    style = Stroke(width = s * 0.06f),
                )
            }
            // Inner rainbow ring. A sweep gradient starts at 3 o'clock, which is 90° from the top.
            val rh = s * 0.35f
            val rainbow = (0..12).map { ringColour((it * 30f + 90f) % 360f) }
            drawCircle(Brush.sweepGradient(rainbow, c), radius = rh, style = Stroke(width = s * 0.10f))
            AppColourMath.PRESETS.forEach { p ->
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 3.dp.toPx(), center = polar(c, rh, p.hue))
            }
            // The middle: a little window onto the background.
            drawCircle(Brush.linearGradient(background, start = Offset(c.x - s * 0.27f, c.y - s * 0.27f), end = Offset(c.x + s * 0.27f, c.y + s * 0.27f)), radius = s * 0.27f)
            drawCircle(border, radius = s * 0.27f, style = Stroke(width = 1.dp.toPx()))
            // The two gems.
            gem(polar(c, rh, choice.hue), 17.dp.toPx(), ringColour(choice.hue))
            gem(polar(c, rp, STRENGTH_START + choice.strength * STRENGTH_SWEEP), 12.dp.toPx(),
                androidx.compose.ui.graphics.lerp(soft, vivid, choice.strength))
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
            Text("SOFT", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AppColors.Muted)
            Text("VIVID", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AppColors.Muted)
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(width = 24.dp, height = 34.dp).clip(RoundedCornerShape(4.dp)).background(accent))
            Spacer(Modifier.height(4.dp))
            Text(AppColourMath.nameFor(choice.hue), fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.Ink)
            Text(
                "${AppColourMath.strengthWord(choice.strength)} · ${(choice.strength * 100).roundToInt()}%",
                fontSize = 11.sp, color = AppColors.Muted,
            )
        }
    }
}

/** A glossy gem: white rim, colour inside, a shine at the top-left. */
private fun DrawScope.gem(at: Offset, radius: Float, fill: Color) {
    drawCircle(Color.Black.copy(alpha = 0.22f), radius = radius + 1.5f, center = at + Offset(0f, 2.dp.toPx()))
    drawCircle(Color.White, radius = radius, center = at)
    val inner = radius - 3.dp.toPx()
    drawCircle(fill, radius = inner, center = at)
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), Color.Transparent), center = at + Offset(-inner * 0.3f, -inner * 0.35f), radius = inner * 0.6f),
        radius = inner, center = at,
    )
}

/** The eight ready-made colours as gems. Tapping one moves the current tab's colour onto it. */
@Composable
private fun PresetGems(selectedHue: Float, onPick: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppColourMath.PRESETS.forEach { p ->
            val selected = AppColourMath.hueDistance(p.hue, selectedHue) < 1f
            val colour = gemColour(ColourChoice(p.hue, 0.6f))
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .then(if (selected) Modifier.border(2.dp, AppColors.Ink, CircleShape) else Modifier)
                    .clickable { onPick(p.hue) }
                    .semantics { contentDescription = p.name },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(28.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(colour, radius = r)
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f), Color.Transparent), center = Offset(r * 0.7f, r * 0.6f), radius = r * 0.7f),
                        radius = r,
                    )
                }
            }
        }
    }
}

/** The background glitter bar: a foil track with a sparkle gem you slide from Off to Full holo. */
@Composable
private fun GlitterBar(value: Float, onChange: (Float) -> Unit) {
    val latest by rememberUpdatedState(onChange)
    val word = AppColourMath.glitterWord(value)
    val trackEmpty = AppColors.Surface2
    val border = AppColors.Border
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Background glitter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$word · ${(value * 100).roundToInt()}%", fontSize = 12.5.sp, color = AppColors.Muted)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics {
                    contentDescription = "Background glitter"
                    stateDescription = word
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                    setProgress { latest(it.coerceIn(0f, 1f)); true }
                }
                .pointerInput(Unit) {
                    val thumb = 14.dp.toPx()
                    fun at(x: Float) = (((x - thumb) / (size.width - 2 * thumb)).coerceIn(0f, 1f) * 100f).roundToInt() / 100f
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        latest(at(down.position.x))
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            latest(at(ch.position.x))
                            ch.consume()
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val thumb = 14.dp.toPx()
                val trackH = 12.dp.toPx()
                val top = (size.height - trackH) / 2f
                val radius = CornerRadius(trackH / 2f)
                val x = thumb + value * (size.width - 2 * thumb)
                drawRoundRect(trackEmpty, topLeft = Offset(0f, top), size = Size(size.width, trackH), cornerRadius = radius)
                drawRoundRect(
                    Brush.horizontalGradient(FOIL, startX = 0f, endX = size.width),
                    topLeft = Offset(0f, top), size = Size(x, trackH), cornerRadius = radius,
                )
                drawRoundRect(border, topLeft = Offset(0f, top), size = Size(size.width, trackH), cornerRadius = radius, style = Stroke(1.dp.toPx()))
                // The sparkle gem.
                val c = Offset(x, size.height / 2f)
                drawCircle(Color.Black.copy(alpha = 0.22f), radius = thumb + 1f, center = c + Offset(0f, 2.dp.toPx()))
                drawCircle(Color.White, radius = thumb, center = c)
                drawCircle(Brush.sweepGradient(FOIL + FOIL.first(), c), radius = thumb - 3.dp.toPx(), center = c)
                val star = Path().apply {
                    val o = 7.dp.toPx()
                    val i = 1.8.dp.toPx()
                    for (k in 0 until 8) {
                        val r = if (k % 2 == 0) o else i
                        val a = k * PI / 4
                        val p = Offset(c.x + (r * sin(a)).toFloat(), c.y - (r * cos(a)).toFloat())
                        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }
                drawPath(star, Color.White)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppColourMath.GLITTER_STEPS.forEach { step ->
                Text(
                    step, fontSize = 10.5.sp,
                    fontWeight = if (step == word) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (step == word) AppColors.Ink else AppColors.Muted,
                )
            }
        }
    }
}
