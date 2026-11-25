package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.DisplayFont

/** The rainbow foil colours as a left-to-right paint. */
fun holoBrush(width: Float, shift: Float = 0f): Brush =
    Brush.linearGradient(AppColors.Holo, start = Offset(-shift * width, 0f), end = Offset(width * (1f - shift), width * 0.35f))

/** A thin rainbow strip along the top edge, like the foil on a holo card. */
fun Modifier.holoTopStripe(height: Float = 3f): Modifier = drawBehind {
    drawRect(holoBrush(size.width), size = Size(size.width, height * density))
}

/** Small capital letters above a big heading, e.g. "YOUR COLLECTION". */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = AppColors.AccentText,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
    )
}

/** One small box with a big number and a label, e.g. "142 / cards". [foil] gives it the rainbow top. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, foil: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(AppColors.Surface2)
            .then(if (foil) Modifier.holoTopStripe() else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = AppColors.Ink, maxLines = 1)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted, maxLines = 1)
    }
}

/** A rounded progress bar filled with foil. It grows from empty when first shown, and slides when [fraction] changes. */
@Composable
fun HoloBar(fraction: Float, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction.coerceIn(0f, 1f), tween(Motion.Long, easing = Motion.Ease)) }
    val value = anim.value
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(5.dp)).background(AppColors.Surface2)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(5.dp))
                .drawBehind { drawRect(holoBrush(size.width.coerceAtLeast(1f))) },
        )
    }
}
