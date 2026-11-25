package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.data.ShelfLook
import com.hybrid87sg.bindertracker.data.ShelfView
import com.hybrid87sg.bindertracker.ui.components.pressSquish
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.DisplayFont

/**
 * Edit shelf: how the binders show on the shelf, in one small sheet. It sits
 * over the bottom of the shelf (folded) or on the right (unfolded), and every
 * change shows live on the shelf above or beside it.
 *
 * - Show binders by: Spine side (up to 5 a row) or Cover side (2 columns,
 *   4 when the phone is unfolded).
 * - Real 3D binders on or off.
 *
 * Changes are saved straight away, on this phone only.
 */
@Composable
fun EditShelfSheet(
    look: ShelfLook,
    onChange: (ShelfLook) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    // Unfolded: Cover side shows 4 covers a row.
    wide: Boolean = false,
) {
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
            GroupTitle("Show binders by")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ViewOption(ShelfView.Spine, "Spine side", "Up to 5 a row", look.view == ShelfView.Spine, { onChange(look.copy(view = ShelfView.Spine)) }, Modifier.weight(1f))
                ViewOption(ShelfView.Cover, "Cover side", if (wide) "4 columns" else "2 columns", look.view == ShelfView.Cover, { onChange(look.copy(view = ShelfView.Cover)) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Group {
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

/** One of the two ways to show the binders: a little drawing of it, its name and a hint. */
@Composable
private fun ViewOption(view: ShelfView, title: String, hint: String, picked: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    val ink = if (picked) AppColors.Accent else AppColors.Muted.copy(alpha = 0.55f)
    Column(
        modifier
            .pressSquish(press)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Surface2)
            .then(if (picked) Modifier.border(2.dp, AppColors.Accent, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple()) {
                if (!picked) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPick()
            }
            .semantics { selected = picked; role = Role.RadioButton }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(width = 96.dp, height = 48.dp)) {
            val w = size.width; val h = size.height
            val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            if (view == ShelfView.Spine) {
                // Five spines with the same gap.
                val bw = w * 0.13f; val gap = (w - 5 * bw) / 4f
                for (i in 0 until 5) drawRoundRect(ink, Offset(i * (bw + gap), 0f), Size(bw, h * 0.86f), r)
            } else {
                // Two covers side by side.
                val bw = w * 0.36f; val gap = w * 0.12f; val x0 = (w - 2 * bw - gap) / 2f
                for (i in 0 until 2) drawRoundRect(ink, Offset(x0 + i * (bw + gap), 0f), Size(bw, h * 0.86f), r)
            }
            drawRoundRect(ink.copy(alpha = ink.alpha * 0.6f), Offset(0f, h * 0.92f), Size(w, h * 0.08f), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.Ink)
        Text(hint, fontSize = 12.sp, color = AppColors.Muted)
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
