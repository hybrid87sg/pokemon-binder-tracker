package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.data.LedColour
import com.hybrid87sg.bindertracker.data.LedStrips
import com.hybrid87sg.bindertracker.data.ShelfLook
import com.hybrid87sg.bindertracker.data.ShelfWood
import com.hybrid87sg.bindertracker.ui.components.pressSquish
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.AppColourMath
import com.hybrid87sg.bindertracker.ui.theme.DisplayFont
import com.hybrid87sg.bindertracker.ui.three.Led
import com.hybrid87sg.bindertracker.ui.three.ShelfUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Edit shelf: everything about the shelf in one sheet, grouped like the Me tab.
 * It sits over the bottom of the shelf (folded) or on the right (unfolded),
 * and every change shows live on the shelf above or beside it.
 *
 * - Lights: which LED strips (back + front, front, back or off) and how bright.
 * - LED colour: a colour ring like the app colour (inner ring = colour, outer
 *   ring = White to Vivid), up to 4 colours that blend along the strip, and
 *   ready-made colours.
 * - Wood, and how the binders stand (angle, which side the covers face, 3D).
 *
 * Changes are saved straight away, on this phone only.
 */
@Composable
fun EditShelfSheet(
    look: ShelfLook,
    onChange: (ShelfLook) -> Unit,
    onDone: () -> Unit,
    // False when the shelf is the flat drawn one (lights and wood only show in 3D).
    showsLights: Boolean,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    var sel by remember { mutableIntStateOf(0) }
    val active = sel.coerceIn(0, look.led.lastIndex)
    Column(modifier) {
        // The little handle and the title with Done.
        Box(Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(CircleShape).background(AppColors.Muted.copy(alpha = 0.4f)))
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Edit shelf", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.Ink, modifier = Modifier.weight(1f))
            val press = remember { MutableInteractionSource() }
            Button(
                onClick = onDone,
                interactionSource = press,
                modifier = Modifier.heightIn(min = 40.dp).pressSquish(press),
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.White),
            ) { Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        ) {
            if (!showsLights) {
                Text(
                    "Lights and wood show on the 3D shelf. Switch on Real 3D binders below to see them.",
                    fontSize = 12.5.sp, color = AppColors.Muted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
            val dim = Modifier.alpha(if (showsLights) 1f else 0.55f)

            GroupTitle("Lights")
            Group(dim) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("LED strips", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.Ink)
                    Text("Back lights the wall, front lights the spines", fontSize = 12.sp, color = AppColors.Muted)
                }
                Segments(
                    options = LedStrips.entries.map { it to it.label },
                    current = look.strips,
                    onPick = { onChange(look.copy(strips = it)) },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row {
                        Text("Brightness", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.Ink, modifier = Modifier.weight(1f))
                        Text("${look.brightness}%", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.Muted)
                    }
                    val mid = Color(Led.at(look.led, 0.5f))
                    Slider(
                        value = look.brightness.toFloat(),
                        onValueChange = { v -> v.roundToInt().let { if (it != look.brightness) onChange(look.copy(brightness = it)) } },
                        valueRange = 0f..100f,
                        enabled = look.strips != LedStrips.Off,
                        colors = SliderDefaults.colors(
                            thumbColor = AppColors.White,
                            activeTrackColor = mid,
                            inactiveTrackColor = AppColors.Surface,
                        ),
                        modifier = Modifier.semantics { contentDescription = "LED brightness" },
                    )
                }
            }

            GroupTitle("LED colour")
            Group(dim) {
                LedTabs(
                    colours = look.led,
                    active = active,
                    onSelect = { sel = it },
                    onAdd = {
                        val last = look.led.last()
                        onChange(look.copy(led = look.led + LedColour((last.hue + 70) % 360, maxOf(60, last.strength))))
                        sel = look.led.size
                    },
                    onRemove = { i ->
                        onChange(look.copy(led = look.led.filterIndexed { j, _ -> j != i }))
                        if (sel >= i) sel = (sel - 1).coerceAtLeast(0)
                    },
                )
                LedRing(
                    colour = look.led[active],
                    blend = look.led.size,
                    onChange = { c -> onChange(look.copy(led = look.led.toMutableList().also { it[active] = c })) },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
                )
                Text(
                    "Inner ring: colour · Outer ring: White to Vivid",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
                )
                // How the whole strip looks, colours blending left to right.
                val cols = look.led.map { Color(Led.argb(it)) }
                Box(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(8.dp)
                        .shadow(6.dp, CircleShape, ambientColor = cols[cols.size / 2], spotColor = cols[cols.size / 2])
                        .clip(CircleShape)
                        .background(if (cols.size > 1) Brush.horizontalGradient(cols) else Brush.horizontalGradient(listOf(cols[0], cols[0]))),
                )
                Presets(look.led, onPick = { p -> sel = 0; onChange(look.copy(led = p)) })
            }

            GroupTitle("Wood")
            Group(dim) {
                Row(Modifier.fillMaxWidth().padding(6.dp)) {
                    ShelfWood.entries.forEach { w ->
                        val f = ShelfUnit.finish(w)
                        val picked = look.wood == w
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .clickable { onChange(look.copy(wood = w)) }
                                .semantics { selected = picked; role = Role.RadioButton }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(44.dp)
                                    .then(if (picked) Modifier.border(2.dp, AppColors.Accent, RoundedCornerShape(16.dp)).padding(4.dp) else Modifier.padding(4.dp))
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Brush.linearGradient(listOf(Color(f.main), Color(f.dark)))),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(w.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                        }
                    }
                }
            }

            GroupTitle("Binders")
            Group {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Angle", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.Ink)
                    Segments(
                        options = ShelfLook.Angles.map { it to if (it == 0) "Straight" else "$it°" },
                        current = look.angle,
                        onPick = { onChange(look.copy(angle = it)) },
                    )
                }
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Covers face", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.Ink)
                    Segments(
                        options = listOf(false to "Left", true to "Right"),
                        current = if (look.turned) look.coversOnRight else null,
                        onPick = { onChange(look.copy(coversOnRight = it)) },
                        enabled = look.turned,
                    )
                    if (!look.turned) Text("Pick an angle first, then choose a side.", fontSize = 12.sp, color = AppColors.Muted)
                }
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Real 3D binders", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.Ink)
                        Text("Off: the flat drawn shelf", fontSize = 12.sp, color = AppColors.Muted)
                    }
                    Switch(
                        checked = look.threeD,
                        onCheckedChange = { onChange(look.copy(threeD = it)) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Accent),
                    )
                }
            }
            Text(
                "Only changes your shelf on this phone. Friends you share binders with keep their own.",
                fontSize = 12.sp, color = AppColors.Muted,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 18.dp + bottomPadding),
            )
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = AppColors.Muted,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AppColors.Surface2).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** A row of pill buttons where one is picked (like a segmented switch). */
@Composable
private fun <T> Segments(options: List<Pair<T, String>>, current: T?, onPick: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).clip(CircleShape).background(AppColors.Surface).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val picked = value == current
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp).clip(CircleShape)
                    .background(if (picked) AppColors.Accent else Color.Transparent)
                    .clickable(enabled = enabled) {
                        if (!picked) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPick(value)
                    }
                    .semantics { selected = picked; role = Role.RadioButton },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    color = if (picked) AppColors.White else AppColors.Muted,
                )
            }
        }
    }
}

/** A tab for each LED colour (× removes it), then "+ Add colour" (up to 4). */
@Composable
private fun LedTabs(colours: List<LedColour>, active: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit, onRemove: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 6.dp, end = 6.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colours.forEachIndexed { i, c ->
            val on = i == active
            Row(
                Modifier.height(32.dp).clip(CircleShape).background(AppColors.Surface)
                    .then(if (on) Modifier.border(2.dp, AppColors.AccentText, CircleShape) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(start = 5.dp, end = if (colours.size > 1) 4.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(Color(Led.argb(c))).border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(if (colours.size > 1) "${i + 1}" else "Colour", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                if (colours.size > 1) {
                    Box(
                        Modifier.padding(start = 2.dp).size(24.dp).clip(CircleShape)
                            .clickable { onRemove(i) }
                            .semantics { contentDescription = "Remove colour ${i + 1}" },
                        contentAlignment = Alignment.Center,
                    ) { Text("×", fontSize = 15.sp, color = AppColors.Muted) }
                }
            }
        }
        if (colours.size < ShelfLook.MAX_LED_COLOURS) {
            Box(
                Modifier.height(32.dp).clip(CircleShape).border(1.5.dp, AppColors.BorderStrong, CircleShape)
                    .clickable(onClick = onAdd).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (colours.size == 1) "+ Add colour" else "+ Add", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppColors.AccentText) }
        }
    }
}

// The outer ring runs clockwise from the bottom-left (210°) for 300°, like the app colour ring.
private const val OUTER_START = 210f
private const val OUTER_SWEEP = 300f

private fun polar(c: Offset, radius: Float, topDegrees: Float): Offset {
    val a = Math.toRadians(topDegrees.toDouble())
    return Offset(c.x + radius * sin(a).toFloat(), c.y - radius * cos(a).toFloat())
}

private fun topAngle(p: Offset, c: Offset): Float {
    val deg = Math.toDegrees(atan2((p.x - c.x).toDouble(), (-(p.y - c.y)).toDouble())).toFloat()
    return (deg + 360f) % 360f
}

/**
 * The LED colour ring. Drag around the inner rainbow ring for the colour (it
 * snaps onto the ready-made colours), or around the outer ring to go from
 * White to Vivid. The middle shows the LED glowing in that colour.
 */
@Composable
private fun LedRing(colour: LedColour, blend: Int, onChange: (LedColour) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(colour)
    val change by rememberUpdatedState(onChange)
    val snaps = remember { ShelfLook.LedPresets.filter { it.colours.size == 1 }.map { it.colours[0].hue }.distinct() }
    val core = AppColors.Surface
    Box(
        modifier
            .size(240.dp)
            .semantics {
                contentDescription = "LED colour ring"
                stateDescription = "${AppColourMath.nameFor(colour.hue.toFloat())}, ${Led.strengthWord(colour.strength)}"
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val s = size.width.toFloat()
                    val c = Offset(s / 2f, s / 2f)
                    val d = (down.position - c).getDistance() / s
                    if (d < 0.26f) return@awaitEachGesture // the middle does nothing
                    val outer = d > 0.42f
                    fun handle(p: Offset) {
                        val angle = topAngle(p, c)
                        if (outer) {
                            var a = (angle - OUTER_START + 360f) % 360f
                            if (a > OUTER_SWEEP) a = if (a > 330f) 0f else OUTER_SWEEP
                            change(current.copy(strength = (a / OUTER_SWEEP * 100f).roundToInt()))
                        } else {
                            var hue = angle.roundToInt() % 360
                            val near = snaps.firstOrNull { AppColourMath.hueDistance(it.toFloat(), hue.toFloat()) < 5f }
                            if (near != null) {
                                if (current.hue != near) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                hue = near
                            }
                            change(current.copy(hue = hue))
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
        val led = Color(Led.argb(colour))
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width
            val c = center
            // Outer ring: White (a hint of the colour) to Vivid.
            val white = Color(AppColourMath.hsl(colour.hue.toFloat(), 15f, 94f))
            val vivid = Color(AppColourMath.hsl(colour.hue.toFloat(), 100f, 55f))
            val rp = s * 0.46f
            val steps = 60
            for (i in 0 until steps) {
                drawArc(
                    color = androidx.compose.ui.graphics.lerp(white, vivid, i / (steps - 1f)),
                    startAngle = OUTER_START + i * (OUTER_SWEEP / steps) - 90f,
                    sweepAngle = OUTER_SWEEP / steps + 0.6f,
                    useCenter = false,
                    topLeft = Offset(c.x - rp, c.y - rp),
                    size = Size(rp * 2, rp * 2),
                    style = Stroke(width = s * 0.07f),
                )
            }
            // Inner rainbow ring (a sweep gradient starts at 3 o'clock, 90° from the top).
            val rh = s * 0.35f
            val rainbow = (0..12).map { Color(AppColourMath.hsl((it * 30f + 90f) % 360f, 90f, 60f)) }
            drawCircle(Brush.sweepGradient(rainbow, c), radius = rh, style = Stroke(width = s * 0.10f))
            snaps.forEach { h -> drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.5.dp.toPx(), center = polar(c, rh, h.toFloat())) }
            // The middle.
            drawCircle(core, radius = s * 0.29f)
            gem(polar(c, rh, colour.hue.toFloat()), 11.dp.toPx())
            gem(polar(c, rp, OUTER_START + colour.strength / 100f * OUTER_SWEEP), 9.dp.toPx())
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(30.dp).shadow(14.dp, CircleShape, ambientColor = led, spotColor = led).clip(CircleShape).background(led))
            Spacer(Modifier.height(6.dp))
            Text(Led.strengthWord(colour.strength), fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.Ink)
            Text(if (blend > 1) "$blend colours blend" else "One colour", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted)
        }
    }
}

/** A white gem with a dark rim, where you drag. */
private fun DrawScope.gem(at: Offset, radius: Float) {
    drawCircle(Color.Black.copy(alpha = 0.3f), radius = radius + 3.dp.toPx(), center = at + Offset(0f, 2.dp.toPx()))
    drawCircle(Color(0xFF1B1730), radius = radius + 3.dp.toPx(), center = at)
    drawCircle(Color.White, radius = radius, center = at)
}

/** The ready-made LED colours, three to a row. */
@Composable
private fun Presets(current: List<LedColour>, onPick: (List<LedColour>) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 6.dp)) {
        ShelfLook.LedPresets.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { p ->
                    val on = p.colours == current
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (on) AppColors.Surface else Color.Transparent)
                            .clickable { onPick(p.colours) }
                            .semantics { selected = on }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val cols = p.colours.map { Color(Led.argb(it)) }
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .background(if (cols.size > 1) Brush.horizontalGradient(cols) else Brush.horizontalGradient(listOf(cols[0], cols[0])))
                                .border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(p.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink, maxLines = 1)
                    }
                }
            }
        }
    }
}
