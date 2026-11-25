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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hybrid87sg.bindertracker.ui.theme.AppShapes
import com.hybrid87sg.bindertracker.ui.theme.windowBackground
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

/**
 * How much room the floating tab bar takes at the bottom of the screen
 * (bar + gap + the phone's own navigation bar). Main tab screens add this
 * below their last item so nothing hides behind the bar. 0 when there is no bar.
 */
val LocalTabBarClearance = compositionLocalOf { 0.dp }

/** How the pill moves: a quick slide with a small springy overshoot. */
private fun <T> pillSpring() = spring<T>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

/** Badge text for the Missing count. */
private fun badgeText(missing: Int) = if (missing > 99) "99+" else "$missing"

/**
 * The tab bar (phone folded): a rounded bar floating above the bottom of the
 * screen. The chosen tab becomes a holo pill with its name; the others are
 * icons only, so the bar stays small and light.
 */
@Composable
fun MainTabBar(
    current: MainTab?,
    missing: Int,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    // Room for the phone's own navigation bar. Null = work it out here.
    bottomInset: androidx.compose.ui.unit.Dp? = null,
) {
    Box(
        modifier.fillMaxWidth()
            .then(if (bottomInset == null) Modifier.navigationBarsPadding() else Modifier.padding(bottom = bottomInset))
            .padding(bottom = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .shadow(14.dp, AppShapes.Pill, ambientColor = AppColors.Shadow, spotColor = AppColors.Shadow)
                .clip(AppShapes.Pill)
                .background(AppColors.Surface)
                .padding(6.dp)
                .selectableGroup()
                .animateContentSize(pillSpring()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab -> BarItem(tab, tab == current, missing) { onSelect(tab) } }
        }
    }
}

@Composable
private fun BarItem(tab: MainTab, selected: Boolean, missing: Int, onClick: () -> Unit) {
    val pill by animateFloatAsState(if (selected) 1f else 0f, tween(Motion.Medium), label = "pill")
    val tint by animateColorAsState(if (selected) AppColors.Black else AppColors.Muted, tween(Motion.Medium), label = "tint")
    Row(
        Modifier
            .heightIn(min = 52.dp)
            .clip(AppShapes.Pill)
            .drawBehind { if (pill > 0f) drawRoundRect(holoBrush(size.width), alpha = pill, cornerRadius = CornerRadius(size.height / 2)) }
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .semantics(mergeDescendants = true) { contentDescription = tab.label }
            .padding(horizontal = if (selected) 16.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Icon(painterResource(tab.icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            if (tab == MainTab.Missing && missing > 0 && !selected) {
                Badge(
                    containerColor = AppColors.Danger,
                    contentColor = if (AppColors.isDark) AppColors.Paper else AppColors.White,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp),
                ) { Text(badgeText(missing), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
        AnimatedVisibility(selected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(tab.label, color = AppColors.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (tab == MainTab.Missing && missing > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge(containerColor = AppColors.Danger, contentColor = if (AppColors.isDark) AppColors.Paper else AppColors.White) {
                        Text(badgeText(missing), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * The same tabs for the unfolded phone: a floating rounded rail down the
 * left side, with a big round Add binder button above it. The holo pill
 * glides up and down behind the chosen tab's icon.
 */
@Composable
fun MainTabRail(current: MainTab?, missing: Int, onSelect: (MainTab) -> Unit, onAdd: (() -> Unit)? = null) {
    val item = 68.dp
    Column(
        Modifier.fillMaxHeight().width(100.dp).windowBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onAdd != null) {
            val press = remember { MutableInteractionSource() }
            Surface(
                onClick = onAdd,
                interactionSource = press,
                shape = RoundedCornerShape(18.dp),
                color = AppColors.Accent,
                contentColor = AppColors.White,
                shadowElevation = 6.dp,
                modifier = Modifier.size(56.dp).pressSquish(press).semantics { contentDescription = "Add binder" },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Box(
            Modifier.width(80.dp)
                .shadow(14.dp, RoundedCornerShape(40.dp), ambientColor = AppColors.Shadow, spotColor = AppColors.Shadow)
                .clip(RoundedCornerShape(40.dp))
                .background(AppColors.Surface)
                .padding(vertical = 8.dp),
        ) {
            val index = current?.ordinal ?: 0
            val y by animateDpAsState(item * index + 6.dp, pillSpring(), label = "pillY")
            val alpha by animateFloatAsState(if (current != null) 1f else 0f, tween(Motion.Short), label = "pillAlpha")
            Box(
                Modifier.offset(x = 12.dp, y = y).size(56.dp, 32.dp).drawBehind {
                    if (alpha > 0f) drawRoundRect(holoBrush(size.width), alpha = alpha, cornerRadius = CornerRadius(size.height / 2))
                },
            )
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                MainTab.entries.forEach { tab ->
                    RailItem(tab, tab == current, missing, Modifier.fillMaxWidth().height(item)) { onSelect(tab) }
                }
            }
        }
    }
}

/** One rail tab: icon (on the pill when chosen), label underneath. */
@Composable
private fun RailItem(tab: MainTab, selected: Boolean, missing: Int, modifier: Modifier, onClick: () -> Unit) {
    val iconColor by animateColorAsState(if (selected) AppColors.Black else AppColors.Muted, tween(Motion.Medium), label = "icon")
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
    ) {
        Box(Modifier.padding(top = 6.dp).size(56.dp, 32.dp), contentAlignment = Alignment.Center) {
            if (tab == MainTab.Missing && missing > 0) {
                BadgedBox(badge = {
                    Badge(containerColor = AppColors.Danger, contentColor = if (AppColors.isDark) AppColors.Paper else AppColors.White) {
                        Text(badgeText(missing), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
