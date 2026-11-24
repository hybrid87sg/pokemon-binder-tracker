package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.data.ShelfLook
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.appBackground
import kotlin.math.roundToInt

/**
 * Edit shelf: how the binders stand on My binders. A slider that stops at
 * each angle (Straight, 15° … 35°) and a Left / Right choice for which side the
 * covers show on. Changes are saved straight away (on this phone only), and
 * the little shelf at the top shows them as you go.
 */
@Composable
fun EditShelfScreen(
    look: ShelfLook,
    // Up to three of your binders for the preview (sample ones if you have fewer).
    preview: List<PreviewBinder>,
    onChange: (ShelfLook) -> Unit,
    onDone: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val angles = ShelfLook.Angles
    val index = angles.indexOf(look.angle).coerceAtLeast(0)
    Column(Modifier.fillMaxSize().appBackground().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton("Back", onDone)
            Spacer(Modifier.weight(1f))
            Text("Edit shelf", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Done", color = AppColors.AccentText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            ShelfPreview(preview, look, perRow = 3)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Binder angle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        angleText(look),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.AccentText,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text("Slide to turn the binders. It stops at each angle.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                // The slider moves in steps: 0 = Straight, 1 = 15°, … 5 = 35°.
                Slider(
                    value = index.toFloat(),
                    onValueChange = { v ->
                        val i = v.roundToInt().coerceIn(0, angles.lastIndex)
                        if (i != index) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onChange(look.copy(angle = angles[i]))
                        }
                    },
                    valueRange = 0f..angles.lastIndex.toFloat(),
                    steps = angles.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = AppColors.Accent,
                        activeTrackColor = AppColors.Accent,
                        inactiveTrackColor = AppColors.Border,
                        activeTickColor = AppColors.Surface,
                        inactiveTickColor = AppColors.Muted,
                    ),
                    modifier = Modifier.semantics { stateDescription = angleText(look) },
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    angles.forEachIndexed { i, a ->
                        Text(
                            if (a == 0) "Straight" else "$a°",
                            fontSize = 12.sp,
                            fontWeight = if (i == index) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (i == index) AppColors.Ink else AppColors.Muted,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text("Turn binders", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (look.turned) "Which side the covers show on." else "Pick an angle first, then choose a side.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Muted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.alpha(if (look.turned) 1f else 0.45f)) {
                    SideButton("Left", picked = look.turned && !look.coversOnRight, enabled = look.turned, Modifier.weight(1f)) {
                        onChange(look.copy(coversOnRight = false))
                    }
                    SideButton("Right", picked = look.turned && look.coversOnRight, enabled = look.turned, Modifier.weight(1f)) {
                        onChange(look.copy(coversOnRight = true))
                    }
                }
            }

            Text(
                "Only changes how your shelf looks on this phone. Friends you share binders with keep their own setting.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Muted,
            )
        }
    }
}

private fun angleText(look: ShelfLook) =
    if (!look.turned) "Straight" else "${look.angle}° ${if (look.coversOnRight) "right" else "left"}"

@Composable
private fun SideButton(label: String, picked: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (picked) AppColors.Accent.copy(alpha = 0.12f) else AppColors.Surface,
        contentColor = AppColors.Ink,
        border = BorderStroke(if (picked) 2.dp else 1.dp, if (picked) AppColors.Accent else AppColors.Border),
        modifier = modifier.heightIn(min = 52.dp).semantics {
            selected = picked
            contentDescription = "Covers on the ${label.lowercase()}"
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(if (label == "Left") "◀  Left" else "Right  ▶", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
