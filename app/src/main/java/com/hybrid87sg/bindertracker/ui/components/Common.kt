package com.hybrid87sg.bindertracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.LightPalette

/** Turns "#8C2F2F" into a colour Compose can draw. */
fun colorFromHex(hex: String): Color =
    runCatching { Color(hex.toColorInt()) }.getOrDefault(LightPalette.ink)

/**
 * Turns text sideways so it reads bottom-to-top, like the name on a real
 * binder spine. It measures the text as if it were lying flat, then swaps
 * width and height so the layout around it makes room for the tall shape.
 */
fun Modifier.verticalText(): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth),
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = (placeable.height - placeable.width) / 2,
                y = (placeable.width - placeable.height) / 2,
            )
        }
    }
    .rotate(-90f)

/**
 * A binder drawn from the side: coloured spine, a pale label with the
 * binder's name, and its size underneath.
 */
@Composable
fun SpineArt(
    name: String,
    tag: String,
    color: Color,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
            .background(color),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 9.dp, end = 9.dp, top = 14.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(4.dp))
                // The paper label stays pale in dark mode too, like a real binder.
                .background(LightPalette.paper),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name,
                modifier = Modifier.verticalText(),
                color = LightPalette.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = tag,
            modifier = Modifier.padding(bottom = 12.dp),
            // Dark writing on light spines (like yellow), white on dark ones.
            color = if (ColorMath.isLight(color.toArgb())) AppColors.Ink else AppColors.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The wooden plank under a row of binders. */
@Composable
fun ShelfPlank(modifier: Modifier = Modifier, sideMargin: Dp = 8.dp) {
    Column(modifier.fillMaxWidth().padding(horizontal = sideMargin)) {
        Box(
            Modifier.fillMaxWidth().height(14.dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(AppColors.Wood),
        )
        Box(Modifier.fillMaxWidth().height(8.dp).background(AppColors.WoodDark))
    }
}

/** "< Shelf" style back button used at the top of most screens. */
@Composable
fun BackButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = AppColors.Ink) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** The big full-width blue button. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Accent,
            contentColor = AppColors.White,
            disabledContainerColor = AppColors.Disabled,
            disabledContentColor = AppColors.OnDisabled,
        ),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A strip pinned to the bottom of the screen that holds the main button. */
@Composable
fun BottomBar(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(AppColors.Paper)) {
        HorizontalDivider(color = AppColors.Border)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
        ) { content() }
    }
}
