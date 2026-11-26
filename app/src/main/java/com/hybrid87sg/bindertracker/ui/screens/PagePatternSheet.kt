package com.hybrid87sg.bindertracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.ui.components.ColourWheel
import com.hybrid87sg.bindertracker.ui.components.ballColor
import com.hybrid87sg.bindertracker.ui.components.drawPagePattern
import com.hybrid87sg.bindertracker.ui.components.pageColor
import com.hybrid87sg.bindertracker.ui.components.pressSquish
import com.hybrid87sg.bindertracker.ui.theme.AUTO_BRIGHTNESS
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.AppColourMath
import com.hybrid87sg.bindertracker.ui.theme.BallColour
import com.hybrid87sg.bindertracker.ui.theme.BallLayout
import com.hybrid87sg.bindertracker.ui.theme.BallStyle
import com.hybrid87sg.bindertracker.ui.theme.ColourChoice
import com.hybrid87sg.bindertracker.ui.theme.DisplayFont
import com.hybrid87sg.bindertracker.ui.theme.PagePattern
import com.hybrid87sg.bindertracker.ui.theme.PagePatternMath
import com.hybrid87sg.bindertracker.ui.theme.PatternGem
import kotlin.math.roundToInt

/** Which colour the colour pop-up is picking. */
private enum class PatternColour { Ball, Page }

/**
 * Page pattern: the Poké Balls on this binder's pages, in one panel. Folded,
 * it sits over the bottom of the screen with the page squeezed in above it;
 * unfolded, it pushes in from the right and the page sits on the left. Every
 * change shows live on the page.
 *
 * Ball colour and Page colour open the app's colour rings in a pop-up of
 * their own (the same rings as App colour).
 *
 * Saved straight away, on this phone only, for this binder.
 */
@Composable
fun BoxScope.PagePatternEditor(
    pattern: PagePattern,
    onChange: (PagePattern) -> Unit,
    onDone: () -> Unit,
    // 0 = out of sight, 1 = fully in.
    progress: Float,
    wide: Boolean,
    panelSize: Dp,
) {
    var picking by remember { mutableStateOf<PatternColour?>(null) }
    val shape = if (wide) RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    PatternPanel(
        pattern = pattern,
        onChange = onChange,
        onDone = onDone,
        onPickColour = { picking = it },
        modifier = (if (wide) {
            Modifier.align(Alignment.TopEnd).width(panelSize).fillMaxSize()
                // Slides in from the right edge, pushing the page over as it comes.
                .graphicsLayer { translationX = (1f - progress) * size.width }
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(panelSize)
                .graphicsLayer { translationY = (1f - progress) * size.height * 1.05f }
        })
            .shadow(16.dp, shape, ambientColor = AppColors.Shadow, spotColor = AppColors.Shadow)
            .clip(shape)
            .background(AppColors.Surface),
        wide = wide,
    )
    picking?.let { role ->
        ColourPopup(
            role = role,
            pattern = pattern,
            onChange = onChange,
            onClose = { picking = null },
        )
    }
}

@Composable
private fun PatternPanel(
    pattern: PagePattern,
    onChange: (PagePattern) -> Unit,
    onDone: () -> Unit,
    onPickColour: (PatternColour) -> Unit,
    modifier: Modifier,
    wide: Boolean,
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier) {
        if (!wide) {
            Box(Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(CircleShape).background(AppColors.Muted.copy(alpha = 0.4f)))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = if (wide) 18.dp else 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Page pattern", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.Ink)
                Text("This binder, on this phone only", fontSize = 12.sp, color = AppColors.Muted)
            }
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
            GroupTitle("Ball style")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BallStyle.entries.forEach { style ->
                    StyleOption(
                        sample = pattern.copy(style = style, drift = false),
                        label = style.label,
                        picked = pattern.style == style,
                        onPick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(pattern.copy(style = style)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Group {
                ColourRow("Ball colour", PagePatternMath.nameFor(pattern.ball, page = false), ballOrb(pattern.ball)) { onPickColour(PatternColour.Ball) }
                ColourRow("Page colour", PagePatternMath.nameFor(BallColour.Pick(pattern.page), page = true), Brush.linearGradient(listOf(pattern.pageColor(), pattern.pageColor()))) { onPickColour(PatternColour.Page) }
            }
            LabelledSlider("Size", PagePatternMath.sizeWord(pattern.size), pattern.size.toFloat(), PagePatternMath.SIZE_MIN.toFloat()..PagePatternMath.SIZE_MAX.toFloat()) {
                onChange(pattern.copy(size = it.roundToInt()))
            }
            LabelledSlider("Spacing", PagePatternMath.gapWord(pattern.gap), pattern.gap.toFloat(), PagePatternMath.GAP_MIN.toFloat()..PagePatternMath.GAP_MAX.toFloat()) {
                onChange(pattern.copy(gap = it.roundToInt()))
            }
            LabelledSlider("Tilt", if (pattern.mixTilt) "Mixed" else "${pattern.tilt}°", pattern.tilt.toFloat(), -90f..90f, enabled = !pattern.mixTilt) {
                // Clicks onto straight when it comes close.
                val t = it.roundToInt().let { v -> if (kotlin.math.abs(v) <= 3) 0 else v }
                onChange(pattern.copy(tilt = t))
            }
            SwitchRow("Mix up the tilt", "Every ball gets its own angle", pattern.mixTilt) { onChange(pattern.copy(mixTilt = it)) }
            GroupTitle("Pattern")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(AppColors.Surface2).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BallLayout.entries.forEach { layout ->
                    val on = pattern.layout == layout
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50))
                            .background(if (on) AppColors.Surface else Color.Transparent)
                            .clickable { onChange(pattern.copy(layout = layout)) }
                            .semantics { selected = on; role = Role.RadioButton },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(layout.label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) AppColors.Ink else AppColors.Muted)
                    }
                }
            }
            LabelledSlider("Strength", "${(pattern.strength * 100).roundToInt()}%", pattern.strength, PagePatternMath.STRENGTH_MIN..PagePatternMath.STRENGTH_MAX) {
                onChange(pattern.copy(strength = (it * 100).roundToInt() / 100f))
            }
            SwitchRow("Slow drift", "The balls glide slowly across the page", pattern.drift) { onChange(pattern.copy(drift = it)) }
            Spacer(Modifier.height(12.dp))
            val resetPress = remember { MutableInteractionSource() }
            OutlinedButton(
                onClick = { onChange(PagePattern()) },
                interactionSource = resetPress,
                modifier = Modifier.fillMaxWidth().height(48.dp).pressSquish(resetPress),
                shape = RoundedCornerShape(percent = 50),
                border = BorderStroke(2.dp, AppColors.Ink),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
            ) { Text("Reset to the original look", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Spacer(Modifier.height(18.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** A little square of the page drawn with one ball style. */
@Composable
private fun StyleOption(sample: PagePattern, label: String, picked: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val ball = ballColor(sample.ball)
    val press = remember { MutableInteractionSource() }
    Column(modifier.pressSquish(press), horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(14.dp)
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                .then(if (picked) Modifier.border(2.dp, AppColors.AccentText, shape) else Modifier.border(1.dp, AppColors.Border, shape))
                .clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), onClick = onPick)
                .semantics { selected = picked; role = Role.RadioButton; contentDescription = "$label balls" },
        ) {
            drawPagePattern(sample.copy(size = 22, gap = 12, mixTilt = false, layout = BallLayout.Rows, strength = maxOf(sample.strength, 0.5f)), ball)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = if (picked) FontWeight.Bold else FontWeight.SemiBold, color = if (picked) AppColors.Ink else AppColors.Muted)
    }
}

/** The fill for a ball colour's orb (red over white for the classic ball). */
private fun ballOrb(c: BallColour): Brush = when (c) {
    BallColour.RedWhite -> Brush.verticalGradient(0f to Color(PagePatternMath.CLASSIC_RED), 0.5f to Color(PagePatternMath.CLASSIC_RED), 0.5f to Color.White, 1f to Color.White)
    else -> ballColor(c).let { Brush.linearGradient(listOf(it, it)) }
}

/** "Ball colour · White  ›": tapping it opens the colour rings. */
@Composable
private fun ColourRow(title: String, name: String, orb: Brush, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Change $title", onClick = onClick)
            .heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(orb).border(2.dp, AppColors.BorderStrong, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.Ink)
            Text(name, fontSize = 12.sp, color = AppColors.Muted)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    word: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = AppColors.Muted, modifier = Modifier.weight(1f))
            Text(word, fontSize = 12.5.sp, color = AppColors.Muted)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = AppColors.Accent, activeTrackColor = AppColors.Accent, inactiveTrackColor = AppColors.Surface2),
        )
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.Ink)
            Text(hint, fontSize = 12.sp, color = AppColors.Muted)
        }
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Accent))
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
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AppColors.Surface2).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

// ---------------------------------------------------------------- the colour pop-up

/**
 * The colour rings in a pop-up of their own, over the page: the same three
 * rings as App colour, with quick-pick gems under them. The page changes live
 * behind it; Cancel (or tapping outside, or Back) puts the old colour back.
 */
@Composable
private fun ColourPopup(role: PatternColour, pattern: PagePattern, onChange: (PagePattern) -> Unit, onClose: () -> Unit) {
    val before = remember { pattern }
    fun cancel() { onChange(before); onClose() }
    BackHandler { cancel() }
    val isBall = role == PatternColour.Ball
    val gems = if (isBall) PagePatternMath.BALL_GEMS else PagePatternMath.PAGE_GEMS
    val current: BallColour = if (isBall) pattern.ball else BallColour.Pick(pattern.page)
    // The rings always show a colour; "App colour" and red & white start the rings on a soft teal.
    val choice = (current as? BallColour.Pick)?.choice ?: ColourChoice(160f, 0.6f)
    fun set(c: BallColour) {
        onChange(
            if (isBall) pattern.copy(ball = c)
            else pattern.copy(page = (c as? BallColour.Pick)?.choice ?: pattern.page),
        )
    }
    val colourOf: (ColourChoice) -> Color = { c -> Color(if (isBall) PagePatternMath.ballColour(c) else PagePatternMath.pageColour(c)) }

    Box(Modifier.fillMaxSize()) {
        // Tapping outside cancels.
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Cancel") { cancel() },
        )
        Column(
            Modifier.align(Alignment.Center).padding(16.dp).widthIn(max = 340.dp)
                .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = AppColors.Shadow, spotColor = AppColors.Shadow)
                .clip(RoundedCornerShape(28.dp)).background(AppColors.Surface)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isBall) "Ball colour" else "Page colour", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.Ink, modifier = Modifier.weight(1f))
                Text(
                    "Only on this phone",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted,
                    modifier = Modifier.border(1.dp, AppColors.Border, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
            Text("Middle ring: colour. Outer ring: Plain to Vivid. Inner ring: dark to light (Auto at the top).", fontSize = 12.sp, color = AppColors.Muted)
            val name = PagePatternMath.nameFor(current, page = !isBall)
            val hex = when (current) {
                BallColour.RedWhite -> "Red + white"
                else -> "#" + Integer.toHexString((if (isBall) ballColor(current) else pattern.pageColor()).toArgb()).takeLast(6).uppercase()
            }
            ColourWheel(
                choice = choice,
                onChange = { set(BallColour.Pick(it)) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                snapHues = PagePatternMath.snapHues(gems),
                strengthEnds = { hue -> colourOf(ColourChoice(hue, 0f, AUTO_BRIGHTNESS)) to Color(AppColourMath.hsl(hue, 100f, if (isBall) 55f else 30f)) },
                brightnessColour = { b -> colourOf(choice.copy(brightness = b)) },
                strengthLabels = "PLAIN" to "VIVID",
                strengthWord = { if (it < 0.12f) "Plain" else AppColourMath.strengthWord(it) },
                middle = { c, r ->
                    // A little window onto the page with the new colour.
                    val window = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
                    clipPath(window) {
                        translate(c.x - r, c.y - r) {
                            val sample = pattern.copy(size = 22, gap = 10, layout = BallLayout.Rows, mixTilt = false, drift = false, strength = maxOf(pattern.strength, 0.4f))
                            drawWindow(sample, ballColor(sample.ball), r * 2)
                        }
                    }
                },
            ) {
                val shadow = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 2f), 6f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, style = shadow, maxLines = 2, modifier = Modifier.widthIn(max = 100.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(hex, fontSize = 10.5.sp, color = Color(0xFFD6D8DE), style = shadow)
                }
            }
            GemRow(gems, current, isBall) { set(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val cancelPress = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = { cancel() },
                    interactionSource = cancelPress,
                    modifier = Modifier.weight(1f).height(52.dp).pressSquish(cancelPress),
                    shape = RoundedCornerShape(percent = 50),
                    border = BorderStroke(2.dp, AppColors.Ink),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
                ) { Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                val usePress = remember { MutableInteractionSource() }
                Button(
                    onClick = onClose,
                    interactionSource = usePress,
                    modifier = Modifier.weight(1.4f).height(52.dp).pressSquish(usePress),
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.White),
                ) { Text("Use colour", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }
    }
}

/** Draws a [side] x [side] square of page with the pattern (for the middle of the rings). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWindow(p: PagePattern, ball: Color, side: Float) {
    clipRect(0f, 0f, side, side) {
        val spots = PagePatternMath.spots(p, side, side, p.size.dp.toPx(), p.gap.dp.toPx())
        drawRect(p.pageColor(), size = androidx.compose.ui.geometry.Size(side, side))
        drawPagePattern(p, ball, spots = spots)
    }
}

/** The quick picks under the rings. Tapping one sets the colour (and the rings jump to it). */
@Composable
private fun GemRow(gems: List<PatternGem>, current: BallColour, isBall: Boolean, onPick: (BallColour) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        gems.forEach { g ->
            val on = when (val c = g.colour) {
                is BallColour.Pick -> current is BallColour.Pick && PagePatternMath.same(c.choice, current.choice)
                else -> c == current
            }
            val fill = when (val c = g.colour) {
                is BallColour.Pick -> colourFor(c, isBall).let { Brush.linearGradient(listOf(it, it)) }
                BallColour.RedWhite -> ballOrb(c)
                BallColour.App -> Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), ballColor(c)))
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .then(if (on) Modifier.border(2.dp, AppColors.AccentText, CircleShape) else Modifier)
                    .clickable(onClickLabel = g.name) { onPick(g.colour) }
                    .semantics { contentDescription = g.name; selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(fill).border(1.dp, AppColors.BorderStrong, CircleShape))
            }
        }
    }
}

private fun colourFor(c: BallColour, isBall: Boolean): Color = when (c) {
    is BallColour.Pick -> Color(if (isBall) PagePatternMath.ballColour(c.choice) else PagePatternMath.pageColour(c.choice))
    else -> ballColor(c)
}
