package com.hybrid87sg.bindertracker.ui.screens

import com.hybrid87sg.bindertracker.ui.theme.appBackground
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.auth.AppUser
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.Eyebrow
import com.hybrid87sg.bindertracker.ui.components.StatTile
import com.hybrid87sg.bindertracker.ui.components.countUp
import com.hybrid87sg.bindertracker.ui.components.holoBrush
import com.hybrid87sg.bindertracker.ui.components.pressSquish
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import java.time.LocalDate
import com.hybrid87sg.bindertracker.data.ArtWaiting
import com.hybrid87sg.bindertracker.data.OfflineArtStatus
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import com.hybrid87sg.bindertracker.ui.theme.ThemeMode
import com.hybrid87sg.bindertracker.ui.components.AppColourRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import com.hybrid87sg.bindertracker.ui.components.SettingsGroup
import com.hybrid87sg.bindertracker.ui.components.SettingsRow
import com.hybrid87sg.bindertracker.ui.components.Motion
import com.hybrid87sg.bindertracker.ui.theme.AppShapes
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.text.font.FontWeight

/** Totals shown on the Account screen. */
data class CollectionStats(val cards: Int, val pages: Int, val binders: Int)

@Composable
fun AccountScreen(
    user: AppUser,
    version: String,
    updateMessage: String?,
    onCheckForUpdates: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    backupMessage: String? = null,
    backupBusy: Boolean = false,
    onBackUp: (Uri) -> Unit = {},
    onRestore: (Uri) -> Unit = {},
    // Totals for the stat tiles; null hides them.
    stats: CollectionStats? = null,
    // True while binders are kept in the Google account; [syncProblem] says why not, if something's wrong.
    syncActive: Boolean = false,
    syncProblem: String? = null,
    // False on the Me tab, where the tab bar takes you back instead.
    showBack: Boolean = true,
    // Card pictures saved for offline use; null hides the section.
    offlineArt: OfflineArtStatus? = null,
    onSaveMissingArt: (Boolean) -> Unit = {},
    onArtOnMobileData: (Boolean) -> Unit = {},
    onDownloadArtAgain: () -> Unit = {},
    // Opens Me > Card back. Null hides the row.
    onOpenCardBack: (() -> Unit)? = null,
) {
    // Android's own "Save to" and "Open" screens. The person picks where the
    // file goes (Google Drive, Downloads, ...); we get back a link to it.
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) onBackUp(uri)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRestore(uri)
    }

    val config = LocalConfiguration.current
    val unfolded = config.screenWidthDp >= 600 && config.screenHeightDp >= 480
    val clearance = com.hybrid87sg.bindertracker.ui.components.LocalTabBarClearance.current

    // ---- The sections, as blocks that can be stacked in one column (folded) or two (unfolded) ----
    val profile: @Composable () -> Unit = {
        ProfileCard(user, stats)
    }
    val account: @Composable () -> Unit = {
        SettingsGroup(label = "Account") {
            SettingsRow(
                title = when {
                    user.isLocalOnly -> "Saved on this phone only"
                    syncActive -> "Saved to your Google account"
                    else -> "Saved to this account on this phone"
                },
                subtitle = when {
                    user.isLocalOnly -> "Sign in with Google to keep binders on every phone and share them"
                    syncActive -> "Comes back on any phone you sign in on"
                    else -> null
                },
                icon = R.drawable.ic_cloud,
                below = if (!user.isLocalOnly && syncProblem != null) {
                    { Text(syncProblem, style = MaterialTheme.typography.bodySmall, color = AppColors.Danger) }
                } else null,
            )
            SettingsRow(
                title = if (user.isLocalOnly) "Go to sign-in" else "Sign out",
                icon = R.drawable.ic_logout,
                onClick = onSignOut,
            )
        }
    }
    val appearance: @Composable () -> Unit = {
        SettingsGroup(label = "Appearance") {
            Column(Modifier.fillMaxWidth().background(AppColors.Group).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePicker(selected = themeMode, onSelect = onThemeModeChange)
                Text(
                    if (themeMode == ThemeMode.System) "Light or dark, matching your phone's own setting."
                    else "Always ${themeMode.label.lowercase()}, whatever your phone is set to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Muted,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            // Your own app colours and background glitter (opens the colour sheet).
            AppColourRow()
            onOpenCardBack?.let { open -> CardBackRow(open) }
        }
    }
    val offline: @Composable () -> Unit = {
        offlineArt?.let { art -> OfflineArtSection(art, onSaveMissingArt, onArtOnMobileData, onDownloadArtAgain) }
    }
    val backups: @Composable () -> Unit = {
        SettingsGroup(label = "Backups") {
            SettingsRow(
                title = "Back up binders",
                subtitle = "One file with every binder, page and photo. Keep it in Google Drive or Downloads.",
                icon = R.drawable.ic_download,
                onClick = if (backupBusy) null else { { saveBackup.launch("binder-backup-${LocalDate.now()}.zip") } },
            )
            SettingsRow(
                title = "Restore from a backup",
                subtitle = "If you change or reset your phone",
                icon = R.drawable.ic_restore,
                // Some file apps don't label zip files properly, so allow any file;
                // the app checks it really is a backup before using it.
                onClick = if (backupBusy) null else { { openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) } },
            )
        }
        if (backupMessage != null) {
            Text(
                backupMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Muted,
                modifier = Modifier.padding(start = 6.dp, top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    val versionRow: @Composable () -> Unit = {
        SettingsGroup {
            SettingsRow(
                title = "App version $version",
                subtitle = updateMessage ?: "Check for updates",
                icon = R.drawable.ic_info,
                onClick = onCheckForUpdates,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    Column(Modifier.fillMaxSize().appBackground().statusBarsPadding()) {
        if (showBack) Box(Modifier.padding(start = 12.dp, top = 12.dp)) { BackButton("Shelf", onBack) }
        else Spacer(Modifier.height(28.dp))

        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = if (unfolded) 28.dp else 16.dp, end = if (unfolded) 28.dp else 16.dp, top = 8.dp, bottom = 16.dp + clearance),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Eyebrow("Trainer profile")
                Text(if (showBack) "Account" else "Me", style = MaterialTheme.typography.headlineLarge)
            }
            if (unfolded) {
                // Unfolded: two columns side by side.
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) { profile(); account(); versionRow() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) { appearance(); offline(); Column { backups() } }
                }
            } else {
                profile(); account(); appearance(); offline(); Column { backups() }; versionRow()
            }
        }
    }
}

/** The big rounded card at the top: your picture, name and collection totals. */
@Composable
private fun ProfileCard(user: AppUser, stats: CollectionStats?) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(10.dp, AppShapes.Large, ambientColor = AppColors.Shadow, spotColor = AppColors.Shadow)
            .clip(AppShapes.Large)
            .background(AppColors.Surface)
            // A soft blur of holo foil glowing from the top-right corner.
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        AppColors.Holo.map { it.copy(alpha = 0.30f) } + Color.Transparent,
                        center = Offset(size.width * 0.92f, 0f), radius = size.width * 0.55f,
                    ),
                    radius = size.width * 0.55f, center = Offset(size.width * 0.92f, 0f),
                )
            }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).drawBehind { drawRect(holoBrush(size.width)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = AppColors.Black, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (user.isLocalOnly) {
                    Text("Not signed in", style = MaterialTheme.typography.titleLarge)
                    Text("Using this phone only", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
                } else {
                    Text(user.name ?: "Signed in with Google", style = MaterialTheme.typography.titleLarge)
                    user.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted) }
                }
            }
        }
        stats?.let { st ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatTile("${countUp(st.cards, 200)}", if (st.cards == 1) "card" else "cards", Modifier.weight(1f))
                StatTile("${countUp(st.pages, 200)}", if (st.pages == 1) "page" else "pages", Modifier.weight(1f))
                StatTile("${countUp(st.binders, 200)}", if (st.binders == 1) "binder" else "binders", Modifier.weight(1f))
            }
        }
    }
}

/**
 * "Offline artwork": how many card pictures are saved on the phone, two
 * switches, and a way to start over. See OfflineArt for how saving works.
 */
@Composable
private fun OfflineArtSection(
    art: OfflineArtStatus,
    onSaveMissing: (Boolean) -> Unit,
    onUseMobileData: (Boolean) -> Unit,
    onDownloadAgain: () -> Unit,
) {
    SettingsGroup(label = "Offline artwork") {
        // Binder cards save on any connection, so they only ever wait for internet.
        ArtProgress(
            "Cards in your binders",
            art.binderSaved,
            art.binderTotal,
            art.waiting.takeIf { it == ArtWaiting.NoInternet && art.binderSaved < art.binderTotal },
            busy = art.busy,
        )
        ArtProgress(
            "Missing cards in your expansions",
            art.missingSaved,
            art.missingTotal,
            art.waiting.takeIf { art.missingSaved < art.missingTotal },
            busy = art.busy,
            off = !art.saveMissing,
        )
        ArtSwitch(
            "Save Missing cards too",
            "Pictures of cards you're still hunting for",
            R.drawable.ic_tab_missing,
            art.saveMissing,
            onSaveMissing,
        )
        ArtSwitch(
            "Use mobile data",
            if (art.useMobileData) "On: Missing cards also download on mobile data."
            else "Off: Missing cards wait for Wi-Fi. Binder cards always save.",
            R.drawable.ic_wifi,
            art.useMobileData,
            onUseMobileData,
        )
        SettingsRow(
            title = "Download again",
            subtitle = "Using ${megabytes(art.bytes)}. Starts over if pictures look wrong.",
            icon = R.drawable.ic_restore,
            onClick = onDownloadAgain,
        )
    }
}

/** One line of progress: a label, "164 of 431" and a foil bar. */
@Composable
private fun ArtProgress(label: String, saved: Int, total: Int, waiting: ArtWaiting?, busy: Boolean, off: Boolean = false) {
    val fraction = if (total == 0) (if (off) 0f else 1f) else saved.toFloat() / total
    val shown by animateFloatAsState(fraction, tween(600), label = "art progress")
    val done = !off && saved >= total
    SettingsRow(
        title = label,
        subtitle = when {
            off -> "Off"
            total == 0 -> "Nothing to save"
            done -> if (total == 1) "Saved" else "All $total saved"
            waiting == ArtWaiting.NoInternet -> "$saved of $total · waiting for internet"
            waiting == ArtWaiting.Wifi -> "$saved of $total · waiting for Wi-Fi"
            busy -> "$saved of $total"
            // Finished, but a few cards have no picture on either website (brand-new sets).
            else -> "$saved of $total · ${total - saved} ${if (total - saved == 1) "has" else "have"} no picture online"
        },
        icon = R.drawable.ic_image,
        modifier = Modifier.semantics(mergeDescendants = true) {},
        below = {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(AppShapes.Pill).background(AppColors.Surface2)) {
                Box(
                    Modifier.fillMaxWidth(shown.coerceIn(0f, 1f)).height(6.dp).clip(AppShapes.Pill)
                        .drawBehind { drawRect(holoBrush(size.width)) },
                )
            }
        },
    )
}

/** A setting with a short explanation under it and an on/off switch. The whole row is the switch. */
@Composable
private fun ArtSwitch(title: String, explain: String, icon: Int, on: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(
        title = title,
        subtitle = explain,
        icon = icon,
        modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        // The whole row handles the tap, so the switch itself doesn't need to.
        trailing = { Switch(checked = on, onCheckedChange = null) },
    )
}

/** 14_200_000 bytes -> "14 MB"; small amounts in KB. */
private fun megabytes(bytes: Long): String = when {
    bytes < 1_000_000 -> "${(bytes + 999) / 1_000} KB"
    bytes < 10_000_000 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
    else -> "${bytes / 1_000_000} MB"
}

/**
 * Follow phone / Light / Dark as one rounded switch: the picked one is a
 * filled pill; tapping another switches the whole app at once.
 */
@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(AppShapes.Pill).background(AppColors.Surface2).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val press = remember { MutableInteractionSource() }
            val fill by animateColorAsState(if (isSelected) AppColors.Accent else Color.Transparent, tween(Motion.Medium), label = "themeFill")
            Surface(
                selected = isSelected,
                onClick = { onSelect(mode) },
                interactionSource = press,
                shape = AppShapes.Pill,
                color = fill,
                contentColor = if (isSelected) AppColors.White else AppColors.Muted,
                shadowElevation = if (isSelected) 2.dp else 0.dp,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).pressSquish(press).semantics { role = Role.RadioButton },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
                    Text(if (mode == ThemeMode.System) "Phone" else mode.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/** "Card back": which back face-down cards show. Opens the Card back screen. */
@Composable
private fun CardBackRow(onOpen: () -> Unit) {
    val setting = com.hybrid87sg.bindertracker.ui.components.LocalCardBack.current
    SettingsRow(
        title = "Card back",
        subtitle = setting.choice.style.title,
        leading = {
            com.hybrid87sg.bindertracker.ui.components.CardBack(
                Modifier.width(28.dp).aspectRatio(63f / 88f).clip(RoundedCornerShape(4.dp)),
            )
        },
        onClick = onOpen,
    )
}
