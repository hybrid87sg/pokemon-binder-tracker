package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.ui.theme.AppColors

/** The four main tabs. [route] is the screen's address in the app's navigation. */
enum class MainTab(val route: String, val label: String, val icon: Int) {
    Binders("shelf", "Binders", R.drawable.ic_tab_binders),
    Search("search", "Search", R.drawable.ic_search),
    Missing("missing", "Missing", R.drawable.ic_tab_missing),
    Me("account", "Me", R.drawable.ic_person),
    ;

    companion object {
        fun of(route: String?): MainTab? = entries.firstOrNull { it.route == route }
    }
}

// The holo pill behind the chosen tab's icon.
private val PillWidth = 60.dp
private val PillHeight = 32.dp
private const val PillAlpha = 0.5f

/**
 * How the pill moves: a quick slide with a small springy overshoot, so it
 * settles onto the new tab like a card dropping into a sleeve.
 */
private fun <T> pillSpring() = spring<T>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

/** The holo pill itself. It is one shape that glides, not one per tab. */
@Composable
private fun HoloPill(modifier: Modifier, visible: Boolean) {
    val alpha by animateFloatAsState(if (visible) PillAlpha else 0f, tween(Motion.Short), label = "pillAlpha")
    Box(
        modifier.size(PillWidth, PillHeight).drawBehind {
            if (alpha > 0f) drawRoundRect(holoBrush(size.width), alpha = alpha, cornerRadius = CornerRadius(size.height / 2))
        },
    )
}

/** The tab bar along the bottom (phone folded). [missing] = the red count on Missing. */
@Composable
fun MainTabBar(current: MainTab?, missing: Int, onSelect: (MainTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(AppColors.Surface).navigationBarsPadding()) {
        HorizontalDivider(color = AppColors.Border)
        BoxWithConstraints(Modifier.fillMaxWidth().height(68.dp)) {
            val slot = maxWidth / MainTab.entries.size
            val index = current?.ordinal ?: 0
            val x by animateDpAsState(slot * index + (slot - PillWidth) / 2, pillSpring(), label = "pillX")
            HoloPill(Modifier.offset(x = x, y = 8.dp), visible = current != null)
            Row(Modifier.fillMaxSize().selectableGroup()) {
                MainTab.entries.forEach { tab ->
                    TabItem(tab, tab == current, missing, Modifier.weight(1f).fillMaxHeight()) { onSelect(tab) }
                }
            }
        }
    }
}

/** The same tabs down the left side, for the unfolded phone. The pill glides up and down. */
@Composable
fun MainTabRail(current: MainTab?, missing: Int, onSelect: (MainTab) -> Unit) {
    val item = 72.dp
    val top = 12.dp
    Box(
        Modifier.fillMaxHeight().width(88.dp).background(AppColors.Surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)),
    ) {
        val index = current?.ordinal ?: 0
        val y by animateDpAsState(top + item * index + 8.dp, pillSpring(), label = "pillY")
        HoloPill(Modifier.offset(x = (88.dp - PillWidth) / 2, y = y), visible = current != null)
        Column(Modifier.fillMaxWidth().padding(top = top).selectableGroup()) {
            MainTab.entries.forEach { tab ->
                TabItem(tab, tab == current, missing, Modifier.fillMaxWidth().height(item)) { onSelect(tab) }
            }
        }
    }
}

/** One tab: icon on top (sitting on the pill when chosen), label underneath. */
@Composable
private fun TabItem(tab: MainTab, selected: Boolean, missing: Int, modifier: Modifier, onClick: () -> Unit) {
    val iconColor by animateColorAsState(if (selected) AppColors.AccentText else AppColors.Muted, tween(Motion.Medium), label = "icon")
    val textColor by animateColorAsState(if (selected) AppColors.Ink else AppColors.Muted, tween(Motion.Medium), label = "text")
    Column(
        modifier.selectable(
            selected = selected,
            onClick = onClick,
            role = Role.Tab,
            interactionSource = remember { MutableInteractionSource() },
            indication = null, // the gliding pill is the feedback
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(Modifier.padding(top = 8.dp).size(PillWidth, PillHeight), contentAlignment = Alignment.Center) {
            if (tab == MainTab.Missing && missing > 0) {
                BadgedBox(badge = {
                    Badge(containerColor = AppColors.Danger, contentColor = if (AppColors.isDark) AppColors.Paper else AppColors.White) {
                        Text(if (missing > 99) "99+" else "$missing", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }) {
                    Icon(painterResource(tab.icon), contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                }
            } else {
                Icon(painterResource(tab.icon), contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
        }
        Text(
            tab.label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
