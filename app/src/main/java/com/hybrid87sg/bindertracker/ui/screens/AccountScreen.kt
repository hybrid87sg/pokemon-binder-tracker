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
) {
    // Android's own "Save to" and "Open" screens. The person picks where the
    // file goes (Google Drive, Downloads, ...); we get back a link to it.
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) onBackUp(uri)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRestore(uri)
    }

    Column(Modifier.fillMaxSize().appBackground().statusBarsPadding()) {
        if (showBack) Box(Modifier.padding(start = 8.dp, top = 12.dp)) { BackButton("Shelf", onBack) }
        else Spacer(Modifier.height(32.dp))

        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("Trainer profile")
                Text(if (showBack) "Account" else "Me", style = if (showBack) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
            }

            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.Surface)
                    .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
                    // A strip of foil down the left edge.
                    .drawBehind { drawRect(holoBrush(size.height), size = Size(4.dp.toPx(), size.height)) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).drawBehind { drawRect(holoBrush(size.width)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = AppColors.Black, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (user.isLocalOnly) {
                        Text("Not signed in", style = MaterialTheme.typography.titleMedium)
                        Text("Using this phone only", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
                    } else {
                        Text(user.name ?: "Signed in with Google", style = MaterialTheme.typography.titleMedium)
                        user.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted) }
                    }
                }
            }

            stats?.let { st ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("${countUp(st.cards, 200)}", if (st.cards == 1) "card" else "cards", Modifier.weight(1f), foil = true)
                    StatTile("${countUp(st.pages, 200)}", if (st.pages == 1) "page" else "pages", Modifier.weight(1f))
                    StatTile("${countUp(st.binders, 200)}", if (st.binders == 1) "binder" else "binders", Modifier.weight(1f))
                }
            }

            Text(
                when {
                    user.isLocalOnly -> "Your binders are saved on this phone only. Sign in with Google to keep them on every phone you use, and to share them."
                    syncActive -> "Your binders are saved online in your Google account, so they come back on any phone you sign in on."
                    else -> "Your binders are saved to this account on this phone."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Muted,
            )
            if (!user.isLocalOnly && syncProblem != null) {
                Text(syncProblem, style = MaterialTheme.typography.bodyMedium, color = AppColors.Danger)
            }

            val signOutPress = remember { MutableInteractionSource() }
            OutlinedButton(
                onClick = onSignOut,
                interactionSource = signOutPress,
                modifier = Modifier.fillMaxWidth().height(56.dp).pressSquish(signOutPress),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(2.dp, AppColors.Ink),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
            ) {
                Text(if (user.isLocalOnly) "Go to sign-in" else "Sign out", style = MaterialTheme.typography.labelLarge)
            }

            HorizontalDivider(color = AppColors.Border)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                ThemePicker(selected = themeMode, onSelect = onThemeModeChange)
                Text(
                    if (themeMode == ThemeMode.System) "Light or dark, matching your phone's own setting."
                    else "Always ${themeMode.label.lowercase()}, whatever your phone is set to.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                )
            }

            HorizontalDivider(color = AppColors.Border)

            offlineArt?.let { art ->
                OfflineArtSection(art, onSaveMissingArt, onArtOnMobileData, onDownloadArtAgain)
                HorizontalDivider(color = AppColors.Border)
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Backups", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Save all your binders, pages and photos into one file. Keep it in Google Drive or your Downloads folder, and restore it if you change or reset your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                )
                val backUpPress = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = { saveBackup.launch("binder-backup-${LocalDate.now()}.zip") },
                    enabled = !backupBusy,
                    interactionSource = backUpPress,
                    modifier = Modifier.fillMaxWidth().height(56.dp).pressSquish(backUpPress),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(2.dp, AppColors.Ink),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
                ) { Text("Back up binders", style = MaterialTheme.typography.labelLarge) }
                val restorePress = remember { MutableInteractionSource() }
                OutlinedButton(
                    // Some file apps don't label zip files properly, so allow any file;
                    // the app checks it really is a backup before using it.
                    onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    enabled = !backupBusy,
                    interactionSource = restorePress,
                    modifier = Modifier.fillMaxWidth().height(56.dp).pressSquish(restorePress),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(2.dp, AppColors.Ink),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
                ) { Text("Restore from a backup", style = MaterialTheme.typography.labelLarge) }
                if (backupMessage != null) {
                    Text(
                        backupMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Muted,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            HorizontalDivider(color = AppColors.Border)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("App version", style = MaterialTheme.typography.titleMedium)
                    Text(version, style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
                }
                TextButton(
                    onClick = onCheckForUpdates,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.AccentText),
                ) { Text("Check for updates", style = MaterialTheme.typography.titleMedium) }
            }
            if (updateMessage != null) {
                Text(
                    updateMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Offline artwork", style = MaterialTheme.typography.titleMedium)
        Text(
            "Card pictures are saved on this phone, so your binders look the same without internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Muted,
        )
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
            art.saveMissing,
            onSaveMissing,
        )
        ArtSwitch(
            "Use mobile data",
            if (art.useMobileData) "On: Missing cards also download on mobile data."
            else "Off: Missing cards wait for Wi-Fi. Binder cards always save.",
            art.useMobileData,
            onUseMobileData,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Using ${megabytes(art.bytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Muted,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onDownloadAgain,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = AppColors.AccentText),
            ) { Text("Download again", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** One line of progress: a label, "164 of 431" and a foil bar. */
@Composable
private fun ArtProgress(label: String, saved: Int, total: Int, waiting: ArtWaiting?, busy: Boolean, off: Boolean = false) {
    val shape = RoundedCornerShape(12.dp)
    val fraction = if (total == 0) (if (off) 0f else 1f) else saved.toFloat() / total
    val shown by animateFloatAsState(fraction, tween(600), label = "art progress")
    Column(
        Modifier.fillMaxWidth().clip(shape).background(AppColors.Surface).border(1.dp, AppColors.Border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            val done = !off && saved >= total
            Text(
                when {
                    off -> "Off"
                    total == 0 -> "Nothing to save"
                    done -> if (total == 1) "Saved" else "All $total saved"
                    waiting == ArtWaiting.NoInternet -> "$saved of $total · waiting for internet"
                    waiting == ArtWaiting.Wifi -> "$saved of $total · waiting for Wi-Fi"
                    busy -> "$saved of $total"
                    // Finished, but a few cards have no picture on either website (brand-new sets).
                    else -> "$saved of $total · ${total - saved} ${if (total - saved == 1) "has" else "have"} no picture online"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (done && total > 0) AppColors.SuccessText else AppColors.Muted,
            )
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.Surface2)) {
            Box(
                Modifier.fillMaxWidth(shown.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .drawBehind { drawRect(holoBrush(size.width)) },
            )
        }
    }
}

/** A setting with a short explanation under it and an on/off switch. */
@Composable
private fun ArtSwitch(title: String, explain: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch, onValueChange = onChange).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(explain, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
        Spacer(Modifier.width(12.dp))
        // The whole row handles the tap, so the switch itself doesn't need to.
        Switch(checked = on, onCheckedChange = null)
    }
}

/** 14_200_000 bytes -> "14 MB"; small amounts in KB. */
private fun megabytes(bytes: Long): String = when {
    bytes < 1_000_000 -> "${(bytes + 999) / 1_000} KB"
    bytes < 10_000_000 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
    else -> "${bytes / 1_000_000} MB"
}

/**
 * Three buttons side by side (Follow phone, Light, Dark). The picked one is
 * filled in; tapping another switches the whole app at once.
 */
@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val press = remember { MutableInteractionSource() }
            // Light mode: violet with white words. Dark mode: pale violet with dark words.
            val chosenFill = if (AppColors.isDark) AppColors.AccentText else AppColors.Accent
            Surface(
                selected = isSelected,
                onClick = { onSelect(mode) },
                interactionSource = press,
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) chosenFill else AppColors.Surface,
                contentColor = if (isSelected) (if (AppColors.isDark) AppColors.Paper else AppColors.White) else AppColors.Ink,
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) chosenFill else AppColors.BorderStrong),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).pressSquish(press).semantics { role = Role.RadioButton },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp)) {
                    Text(mode.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
            }
        }
    }
}
