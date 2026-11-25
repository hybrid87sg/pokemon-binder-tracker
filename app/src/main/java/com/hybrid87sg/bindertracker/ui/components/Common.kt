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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.hybrid87sg.bindertracker.ui.theme.AppShapes

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

/**
 * The back button at the top of most screens: a round button with an arrow,
 * then the name of where you are going back to (left out when it would just
 * say "Back"). The arrow and the words are one tap target.
 */
@Composable
fun BackButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = AppColors.Ink, maxLines: Int = Int.MAX_VALUE) {
    val press = remember { MutableInteractionSource() }
    // On camera screens the colour is light; give the circle a dark see-through fill there.
    val onCamera = color != AppColors.Ink
    val showLabel = label != "Back"
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(AppShapes.Pill)
            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), role = Role.Button, onClickLabel = null, onClick = onClick)
            .pressSquish(press)
            .semantics { contentDescription = if (showLabel) "Back to $label" else "Back" }
            .padding(end = if (showLabel) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp)
                .shadow(if (onCamera) 0.dp else 3.dp, CircleShape, ambientColor = AppColors.Shadow, spotColor = AppColors.Shadow)
                .clip(CircleShape)
                .background(if (onCamera) Color(0x59000000) else AppColors.Group),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        if (showLabel) {
            Spacer(Modifier.width(10.dp))
            Text(
                label, color = color, style = MaterialTheme.typography.titleMedium, maxLines = maxLines,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** The big blue button: a full-width pill. */
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
        shape = AppShapes.Pill,
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

/** A second-choice button: a soft tinted pill (instead of a heavy outline). */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    fullWidth: Boolean = true,
) {
    val press = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = press,
        modifier = modifier.then(if (fullWidth) Modifier.fillMaxWidth() else Modifier).heightIn(min = 52.dp).pressSquish(press),
        shape = AppShapes.Pill,
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Tonal,
            contentColor = AppColors.OnTonal,
            disabledContainerColor = AppColors.Disabled,
            disabledContentColor = AppColors.OnDisabled,
        ),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A round icon button that floats a little above the background, e.g. the
 * pencil on My binders. [primary] fills it with the accent colour.
 */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    elevated: Boolean = true,
) {
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = press,
        shape = shape,
        color = if (primary) AppColors.Accent else AppColors.Group,
        contentColor = if (primary) AppColors.White else AppColors.Ink,
        shadowElevation = if (elevated) 3.dp else 0.dp,
        modifier = modifier.size(48.dp).pressSquish(press).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

/** One button in an [IconButtonGroup]. */
class GroupButton(@DrawableRes val icon: Int, val description: String, val primary: Boolean = false, val onClick: () -> Unit)

/**
 * Small icon buttons joined into one rounded strip: round outer ends, tight
 * inner corners. Reads as one tidy control instead of loose squares.
 */
@Composable
fun IconButtonGroup(buttons: List<GroupButton>, modifier: Modifier = Modifier) {
    Row(
        modifier.shadow(3.dp, AppShapes.Pill, ambientColor = AppColors.Shadow, spotColor = AppColors.Shadow).clip(AppShapes.Pill),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        buttons.forEachIndexed { i, b ->
            val first = i == 0
            val last = i == buttons.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (first) 24.dp else 8.dp, bottomStart = if (first) 24.dp else 8.dp,
                topEnd = if (last) 24.dp else 8.dp, bottomEnd = if (last) 24.dp else 8.dp,
            )
            RoundIconButton(b.icon, b.description, b.onClick, primary = b.primary, shape = shape, elevated = false)
        }
    }
}

/** The small grey heading above a settings group, e.g. "Backups". */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 6.dp, bottom = 8.dp).semantics { heading() },
        color = AppColors.Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * Rows grouped into one rounded block, like current Android settings: the
 * block has big round outer corners and the rows inside are split by thin gaps.
 * Put [SettingsRow]s (or any content) inside.
 */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, label: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (label != null) GroupLabel(label)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            content = content,
        )
    }
}

/**
 * One row in a [SettingsGroup]: a round tinted icon (or your own [leading]),
 * a title, an optional line under it, and something on the right ([trailing],
 * or a ">" when the row opens something).
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = AppColors.Ink,
    onClick: (() -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .background(AppColors.Group)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> { Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }; Spacer(Modifier.width(14.dp)) }
            icon != null -> {
                Box(Modifier.size(40.dp).clip(CircleShape).background(AppColors.Tonal), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = null, tint = AppColors.OnTonal, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            below?.invoke()
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        } else if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
        }
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

/** A small drawn icon (used where the app once typed symbols like ✕ or ▾, which look different on every phone). */
@Composable
fun SmallIcon(@DrawableRes res: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}
