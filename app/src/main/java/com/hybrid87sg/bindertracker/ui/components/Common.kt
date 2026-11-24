package com.hybrid87sg.bindertracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.shadow
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
import com.hybrid87sg.bindertracker.ui.theme.DisplayFont
import com.hybrid87sg.bindertracker.ui.theme.LightPalette
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.remember

/** The cream paper label on every spine, the same in light and dark mode. */
private val SpineLabel = Color(0xFFF6F4EF)
private val SpineShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp)

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
    // How full the binder is, 0 to 1, shown as a small bar. Null hides the bar.
    fill: Float? = null,
    // Your cover picture, shown small in a cream "window" above the label.
    picture: androidx.compose.ui.graphics.ImageBitmap? = null,
) {
    val pale = ColorMath.isLight(color.toArgb())
    Column(
        modifier = modifier
            .width(width)
            .height(height)
            // A soft shadow, so the binder stands out from the shelf.
            .shadow(6.dp, SpineShape, ambientColor = AppColors.Shadow, spotColor = AppColors.Shadow)
            .clip(SpineShape)
            .background(color)
            // Leatherette grain, a padded sheen and stitching (see Textures.kt).
            .leatherCover(pale),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (picture != null) {
            // The window: a cream frame with the picture cropped to a square.
            Box(
                Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp).fillMaxWidth().aspectRatio(1f)
                    .shadow(1.5.dp, RoundedCornerShape(5.dp))
                    .clip(RoundedCornerShape(5.dp))
                    .background(SpineLabel)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(3.dp)),
            ) {
                androidx.compose.foundation.Image(
                    bitmap = picture,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 9.dp, end = 9.dp, top = if (picture != null) 8.dp else 14.dp, bottom = 8.dp)
                .shadow(1.5.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                // The paper label stays pale in dark mode too, like a real binder.
                .background(SpineLabel)
                // A strip of rainbow foil across the top of the label.
                .holoTopStripe(height = 6f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name,
                modifier = Modifier.verticalText(),
                color = AppColors.Black,
                fontFamily = DisplayFont,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (fill != null) {
            // How full the binder is: dark bar on pale spines, white on dark ones.
            val barColor = if (pale) AppColors.Black else AppColors.White
            Box(
                Modifier.padding(horizontal = 9.dp).fillMaxWidth().height(5.dp)
                    .clip(RoundedCornerShape(3.dp)).background(barColor.copy(alpha = 0.25f)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fill.coerceIn(0f, 1f)).clip(RoundedCornerShape(3.dp)).background(barColor))
            }
            Spacer(Modifier.height(5.dp))
        }
        Text(
            text = tag,
            modifier = Modifier.padding(bottom = 12.dp),
            // Dark writing on pale spines (like yellow), white on dark ones, in
            // both light and dark mode. (It used the main text colour before,
            // which is almost white in dark mode: unreadable on yellow.)
            color = if (pale) AppColors.Black else AppColors.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The wooden plank under a row of binders. */
@Composable
fun ShelfPlank(modifier: Modifier = Modifier, sideMargin: Dp = 8.dp) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = sideMargin)
            .shadow(5.dp, RoundedCornerShape(3.dp), ambientColor = AppColors.Shadow, spotColor = AppColors.Shadow),
    ) {
        Box(
            Modifier.fillMaxWidth().height(14.dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(AppColors.Wood)
                .woodGrain(),
        )
        Box(Modifier.fillMaxWidth().height(8.dp).background(AppColors.WoodDark).woodGrain())
    }
}

/** "< Shelf" style back button used at the top of most screens. */
@Composable
fun BackButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = AppColors.Ink) {
    val press = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        interactionSource = press,
        modifier = modifier.heightIn(min = 48.dp).pressSquish(press),
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
    val press = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = press,
        modifier = modifier.fillMaxWidth().height(56.dp).pressSquish(press),
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
    Column(Modifier.fillMaxWidth().background(AppColors.Surface)) {
        HorizontalDivider(color = AppColors.Border)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
        ) { content() }
    }
}
