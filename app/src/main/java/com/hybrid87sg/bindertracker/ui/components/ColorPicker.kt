package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.data.SpineColor
import com.hybrid87sg.bindertracker.ui.theme.AppColors

/**
 * Ready-made colours shown as round buttons at the top of the picker.
 * The first six are the original spine colours; the rest add brighter
 * and lighter choices.
 */
val QuickColors: List<Pair<String, String>> = SpineColor.entries.map { it.label to it.hex } + listOf(
    "Sky" to "#3F7FBF",
    "Teal" to "#2A7F7A",
    "Yellow" to "#F2C230",
    "Orange" to "#D9662B",
    "Pink" to "#D46A92",
    "Silver" to "#B9B8B1",
)

/**
 * A pop-up panel that slides up from the bottom of the screen and lets the
 * person choose a binder's spine colour. [onSave] gets the new colour as
 * text like "#8C2F2F".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    binderName: String,
    sizeLabel: String,
    initialHex: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    saveLabel: String = "Save colour",
) {
    // skipPartiallyExpanded = open fully, so the Save button is always visible.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppColors.Paper) {
        ColorPicker(binderName, sizeLabel, initialHex, onSave, onDismiss, saveLabel)
    }
}

/** What's inside the panel. Kept separate so it can be drawn in tests. */
@Composable
fun ColorPicker(
    binderName: String,
    sizeLabel: String,
    initialHex: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    saveLabel: String = "Save colour",
) {
    // The colour currently chosen, as a number like 0xFF8C2F2F.
    val start = remember(initialHex) { ColorMath.parseHex(initialHex) ?: ColorMath.parseHex(SpineColor.Red.hex)!! }
    var chosen by rememberSaveable { mutableStateOf(start) }
    // Where the two sliders sit. They start at the binder's current colour.
    val (startHue, startShade) = remember(start) { ColorMath.slidersFor(start) }
    var hue by rememberSaveable { mutableFloatStateOf(startHue) }
    var shade by rememberSaveable { mutableFloatStateOf(startShade) }
    // What's typed in the colour code box.
    var codeText by rememberSaveable { mutableStateOf(ColorMath.toHex(start)) }

    // Every way of choosing a colour goes through here, so all the parts
    // of the picker stay in step with each other.
    fun choose(color: Int, moveSliders: Boolean = true, updateCode: Boolean = true) {
        chosen = color
        if (moveSliders) ColorMath.slidersFor(color).let { (h, s) -> hue = h; shade = s }
        if (updateCode) codeText = ColorMath.toHex(color)
    }

    val chosenHex = ColorMath.toHex(chosen)

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Spine colour", style = MaterialTheme.typography.titleLarge)

        // Live preview: the binder spine in the new colour, on a bit of shelf.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.ShelfBackground).padding(top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpineArt(name = binderName, tag = sizeLabel, color = Color(chosen), width = 64.dp, height = 150.dp)
            ShelfPlank(Modifier.padding(bottom = 12.dp), sideMargin = 12.dp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quick picks", style = MaterialTheme.typography.titleMedium)
            QuickColors.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { (label, hex) ->
                        val color = ColorMath.parseHex(hex)!!
                        Swatch(label, color, selected = color == chosen) { choose(color) }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Make your own", style = MaterialTheme.typography.titleMedium)
            Text("Slide along each bar, or tap anywhere on it.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)

            SliderLabel("Colour")
            GradientSlider(
                fraction = hue / 360f,
                brush = remember { Brush.horizontalGradient((0..6).map { Color(ColorMath.hsvToArgb(it * 60f, 0.85f, 0.85f)) }) },
                thumbColor = Color(ColorMath.hsvToArgb(hue, 0.85f, 0.85f)),
                label = "Colour",
                onChange = { f ->
                    hue = f * 360f
                    choose(ColorMath.shadeColor(hue, shade), moveSliders = false)
                },
            )

            SliderLabel("Shade", left = "Pale", right = "Dark")
            GradientSlider(
                fraction = shade,
                brush = Brush.horizontalGradient((0..4).map { Color(ColorMath.shadeColor(hue, it / 4f)) }),
                thumbColor = Color(chosen),
                label = "Shade",
                onChange = { f ->
                    shade = f
                    choose(ColorMath.shadeColor(hue, shade), moveSliders = false)
                },
            )
        }

        // For people who already know the exact colour they want.
        OutlinedTextField(
            value = codeText,
            onValueChange = { text ->
                if (text.length <= 7) {
                    codeText = text
                    ColorMath.parseHex(text)?.let { choose(it, updateCode = false) }
                }
            },
            label = { Text("Colour code (optional)") },
            supportingText = {
                Text(
                    if (ColorMath.parseHex(codeText) == null) "Use 6 letters or numbers, like #8C2F2F" else "Like #8C2F2F",
                )
            },
            isError = ColorMath.parseHex(codeText) == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = AppColors.Surface,
                focusedContainerColor = AppColors.Surface,
                unfocusedBorderColor = AppColors.BorderStrong,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel, modifier = Modifier.height(56.dp)) { Text("Cancel", color = AppColors.Ink) }
            Box(Modifier.weight(1f).padding(start = 8.dp)) {
                PrimaryButton(text = saveLabel, onClick = { onSave(chosenHex) })
            }
        }
    }
}

@Composable
private fun SliderLabel(title: String, left: String? = null, right: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (left != null && right != null) {
            Text("$left  ·  $right", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
    }
}

/** One round colour button. A dark ring with a pale inner ring marks the chosen one. */
@Composable
private fun Swatch(label: String, color: Int, selected: Boolean, onClick: () -> Unit) {
    val swatch = Color(color)
    // Light colours get a thin grey edge so they don't vanish into the background.
    val edge = when {
        selected -> AppColors.Ink
        ColorMath.isLight(color) -> AppColors.BorderStrong
        else -> swatch
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = swatch,
        border = BorderStroke(if (selected) 3.dp else 1.dp, edge),
        modifier = Modifier.size(44.dp).semantics {
            contentDescription = label
            this.selected = selected
            role = Role.RadioButton
        },
    ) {
        if (selected) Box(Modifier.padding(3.dp).border(3.dp, AppColors.Paper, CircleShape))
    }
}

/**
 * A wide, finger-friendly bar filled with a colour gradient. Touch or drag
 * anywhere on it to move the round handle. [fraction] is 0 (left) to 1 (right).
 */
@Composable
private fun GradientSlider(
    fraction: Float,
    brush: Brush,
    thumbColor: Color,
    label: String,
    onChange: (Float) -> Unit,
) {
    // Keeps the latest onChange even though the touch handler below is set up once.
    val latestOnChange by rememberUpdatedState(onChange)
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                contentDescription = label
                stateDescription = "${(fraction * 100).toInt()} percent"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { value -> latestOnChange(value.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                // Handle edges sit 22dp in from each side, so map the touch to that range.
                val inset = 22.dp.toPx()
                fun toFraction(x: Float) = ((x - inset) / (size.width - inset * 2)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    latestOnChange(toFraction(down.position.x))
                    drag(down.id) { change ->
                        change.consume()
                        latestOnChange(toFraction(change.position.x))
                    }
                }
            }
            .drawWithContent {
                val radius = 20.dp.toPx()
                val trackHeight = 28.dp.toPx()
                val inset = 22.dp.toPx()
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(0f, (size.height - trackHeight) / 2),
                    size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2),
                )
                val center = Offset(inset + (size.width - inset * 2) * fraction.coerceIn(0f, 1f), size.height / 2)
                drawCircle(Color.White, radius, center)
                drawCircle(thumbColor, radius - 4.dp.toPx(), center)
                drawCircle(AppColors.Ink.copy(alpha = 0.35f), radius, center, style = Stroke(1.dp.toPx()))
            },
    )
}
