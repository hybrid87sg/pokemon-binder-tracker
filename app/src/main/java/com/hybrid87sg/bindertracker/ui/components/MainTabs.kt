package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.ui.theme.AppColors

/** The five main tabs. [route] is the screen's address in the app's navigation. */
enum class MainTab(val route: String, val label: String, val icon: Int) {
    Binders("shelf", "Binders", R.drawable.ic_tab_binders),
    Search("search", "Search", R.drawable.ic_search),
    Missing("missing", "Missing", R.drawable.ic_tab_missing),
    Chat("chat", "Chat", R.drawable.ic_tab_chat),
    Me("account", "Me", R.drawable.ic_person),
    ;

    companion object {
        fun of(route: String?): MainTab? = entries.firstOrNull { it.route == route }
    }
}

/** The tab bar along the bottom (phone folded). [missing] = the red count on Missing. */
@Composable
fun MainTabBar(current: MainTab?, missing: Int, onSelect: (MainTab) -> Unit) {
    NavigationBar(containerColor = AppColors.Surface, tonalElevation = 0.dp) {
        MainTab.entries.forEach { tab ->
            val on = tab == current
            NavigationBarItem(
                selected = on,
                onClick = { onSelect(tab) },
                icon = { TabIcon(tab, on, missing) },
                label = { Text(tab.label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = AppColors.AccentText,
                    selectedTextColor = AppColors.Ink,
                    unselectedIconColor = AppColors.Muted,
                    unselectedTextColor = AppColors.Muted,
                ),
            )
        }
    }
}

/** The same tabs down the left side, for the unfolded phone. */
@Composable
fun MainTabRail(current: MainTab?, missing: Int, onSelect: (MainTab) -> Unit) {
    NavigationRail(containerColor = AppColors.Surface) {
        Spacer(Modifier.height(12.dp))
        MainTab.entries.forEach { tab ->
            val on = tab == current
            NavigationRailItem(
                selected = on,
                onClick = { onSelect(tab) },
                icon = { TabIcon(tab, on, missing) },
                label = { Text(tab.label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1) },
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = AppColors.AccentText,
                    selectedTextColor = AppColors.Ink,
                    unselectedIconColor = AppColors.Muted,
                    unselectedTextColor = AppColors.Muted,
                ),
            )
        }
    }
}

/** The tab's icon. The chosen tab sits on a pill of holo foil that shines in. */
@Composable
private fun TabIcon(tab: MainTab, selected: Boolean, missing: Int) {
    val foil by animateFloatAsState(if (selected) 0.45f else 0f, tween(Motion.Medium), label = "foil")
    Box(
        // Not clipped, so the red count can poke out of the corner.
        Modifier.size(width = 56.dp, height = 30.dp)
            .drawBehind { if (foil > 0f) drawRoundRect(holoBrush(size.width), alpha = foil, cornerRadius = CornerRadius(15.dp.toPx())) },
        contentAlignment = Alignment.Center,
    ) {
        if (tab == MainTab.Missing && missing > 0) {
            BadgedBox(badge = {
                Badge(containerColor = AppColors.Danger, contentColor = if (AppColors.isDark) AppColors.Paper else AppColors.White) {
                    Text(if (missing > 99) "99+" else "$missing", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }) {
                Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(22.dp))
            }
        } else {
            Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}
