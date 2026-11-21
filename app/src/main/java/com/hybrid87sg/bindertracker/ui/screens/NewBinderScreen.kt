package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.BinderSize
import com.hybrid87sg.bindertracker.data.SpineColor
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.BottomBar
import com.hybrid87sg.bindertracker.ui.components.ColorPickerSheet
import com.hybrid87sg.bindertracker.ui.components.PrimaryButton
import com.hybrid87sg.bindertracker.ui.components.ShelfPlank
import com.hybrid87sg.bindertracker.ui.components.SpineArt
import com.hybrid87sg.bindertracker.ui.components.colorFromHex
import com.hybrid87sg.bindertracker.ui.theme.AppColors

private const val MAX_NAME_LENGTH = 20

@Composable
fun NewBinderScreen(
    onCancel: () -> Unit,
    onCreate: (name: String, colorHex: String, size: BinderSize) -> Unit,
) {
    BinderForm(
        title = "New binder",
        initialName = "",
        initialColorHex = SpineColor.Red.hex,
        initialSize = BinderSize.ThreeByThree,
        saveText = "Add to shelf",
        onCancel = onCancel,
        onSave = onCreate,
    )
}

/**
 * The form shared by "New binder" and "Edit binder": a live spine preview,
 * the name box, spine colour and binder size. [sizeNote] can show a line
 * under the sizes (Edit binder uses it to warn about cards that won't fit).
 */
@Composable
internal fun BinderForm(
    title: String,
    initialName: String,
    initialColorHex: String,
    initialSize: BinderSize,
    saveText: String,
    onCancel: () -> Unit,
    onSave: (name: String, colorHex: String, size: BinderSize) -> Unit,
    sizeNote: @Composable (BinderSize) -> Unit = {},
) {
    // "rememberSaveable" keeps what you typed even if the phone rotates.
    var name by rememberSaveable { mutableStateOf(initialName) }
    // The spine colour as text like "#8C2F2F", so it can be any colour, not just the six.
    var colorHex by rememberSaveable { mutableStateOf(initialColorHex) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var size by rememberSaveable { mutableStateOf(initialSize) }
    val canSave = name.isNotBlank()

    Column(Modifier.fillMaxSize().background(AppColors.Paper).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton("Cancel", onCancel)
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Live preview of the spine as you choose.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.ShelfBackground).padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpineArt(
                    name = name.trim().ifEmpty { "Name" },
                    tag = size.label,
                    color = colorFromHex(colorHex),
                    width = 72.dp,
                    height = 176.dp,
                )
                ShelfPlank(Modifier.padding(bottom = 14.dp), sideMargin = 12.dp)
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= MAX_NAME_LENGTH) name = it },
                label = { Text("Binder name") },
                placeholder = { Text("e.g. Vintage") },
                supportingText = { Text("${name.length} / $MAX_NAME_LENGTH") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AppColors.Surface,
                    focusedContainerColor = AppColors.Surface,
                    unfocusedBorderColor = AppColors.BorderStrong,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Spine colour", style = MaterialTheme.typography.titleMedium)
                val isPreset = SpineColor.entries.any { it.hex.equals(colorHex, ignoreCase = true) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SpineColor.entries.forEach { option ->
                        ColorSwatch(option, selected = option.hex.equals(colorHex, ignoreCase = true)) { colorHex = option.hex }
                    }
                    // The last button opens the full picker for any other colour.
                    CustomSwatch(customHex = colorHex.takeUnless { isPreset }) { showPicker = true }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Binder size", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("columns × rows", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BinderSize.entries.forEach { option ->
                        SizeTile(option, selected = option == size, modifier = Modifier.weight(1f)) { size = option }
                    }
                }
                sizeNote(size)
            }
        }

        BottomBar {
            PrimaryButton(
                text = if (canSave) saveText else "Name your binder to continue",
                enabled = canSave,
                onClick = { onSave(name, colorHex, size) },
            )
        }
    }

    if (showPicker) {
        ColorPickerSheet(
            binderName = name.trim().ifEmpty { "Name" },
            sizeLabel = size.label,
            initialHex = colorHex,
            onSave = { hex -> colorHex = hex; showPicker = false },
            onDismiss = { showPicker = false },
            saveLabel = "Use this colour",
        )
    }
}

/**
 * The "more colours" button. Shows a rainbow with a plus until a custom
 * colour is picked, then shows that colour with a ring around it.
 */
@Composable
private fun CustomSwatch(customHex: String?, onClick: () -> Unit) {
    val selected = customHex != null
    val rainbow = Brush.sweepGradient(
        listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE53935)),
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colorFromHex(customHex!!) else Color.Transparent,
        border = BorderStroke(3.dp, if (selected) AppColors.Ink else Color.Transparent),
        modifier = Modifier.size(44.dp).semantics {
            contentDescription = if (selected) "Custom colour, tap to change" else "More colours"
            this.selected = selected
            role = Role.RadioButton
        },
    ) {
        if (selected) {
            Box(Modifier.padding(3.dp).border(3.dp, AppColors.Paper, CircleShape))
        } else {
            Box(Modifier.fillMaxSize().background(rainbow, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(24.dp).background(AppColors.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = AppColors.Ink, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(option: SpineColor, selected: Boolean, onClick: () -> Unit) {
    val swatch = colorFromHex(option.hex)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = swatch,
        border = BorderStroke(3.dp, if (selected) AppColors.Ink else swatch),
        modifier = Modifier.size(44.dp).semantics {
            contentDescription = "Spine colour ${option.label}"
            this.selected = selected
            role = Role.RadioButton
        },
    ) {
        // A thin pale ring inside the dark outline shows which one is picked.
        if (selected) Box(Modifier.padding(3.dp).border(3.dp, AppColors.Paper, CircleShape))
    }
}

@Composable
private fun SizeTile(option: BinderSize, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) AppColors.Ink else AppColors.Surface
    val fg = if (selected) AppColors.Paper else AppColors.Ink
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        contentColor = fg,
        border = BorderStroke(2.dp, if (selected) AppColors.Ink else AppColors.Border),
        modifier = modifier.heightIn(min = 136.dp).semantics {
            this.selected = selected
            role = Role.RadioButton
        },
    ) {
        Column(
            Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // A tiny picture of the pocket grid, each pocket card-shaped (63 × 88).
            Box(Modifier.height(72.dp), contentAlignment = Alignment.Center) {
                Column(Modifier.width(34.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(option.rows) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(option.columns) {
                                Box(
                                    Modifier.weight(1f).aspectRatio(63f / 88f)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (selected) AppColors.Paper else AppColors.Ink),
                                )
                            }
                        }
                    }
                }
            }
            Text(option.label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "${option.pockets} cards",
                fontSize = 13.sp,
                color = if (selected) AppColors.Paper.copy(alpha = 0.75f) else AppColors.Muted,
            )
        }
    }
}
