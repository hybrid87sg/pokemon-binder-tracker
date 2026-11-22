package com.hybrid87sg.bindertracker.ui.screens

import com.hybrid87sg.bindertracker.ui.theme.appBackground
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.sync.InviteCodes
import com.hybrid87sg.bindertracker.sync.Member
import com.hybrid87sg.bindertracker.sync.SharingInfo
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.Eyebrow
import com.hybrid87sg.bindertracker.ui.components.holoTopStripe
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.DisplayFont

/**
 * Share a binder you own: pick whether people join as Editor or Viewer,
 * show them the QR code (or send the code), and see who has joined.
 */
@Composable
fun ShareScreen(
    binder: Binder,
    // Who it's shared with, and its share codes. Null until the binder is online.
    sharing: SharingInfo?,
    // False when signed out of Google ("phone only"), so sharing can't work.
    online: Boolean,
    // Why online saving isn't working, or null.
    problem: String?,
    onBack: () -> Unit,
    onCreateInvite: (role: String) -> Unit,
    onSetRole: (uid: String, role: String) -> Unit,
    onRemove: (uid: String) -> Unit,
    onStopSharing: () -> Unit,
) {
    var role by rememberSaveable { mutableStateOf(Binder.ROLE_EDITOR) }
    val invite = sharing?.invites?.get(role)?.takeIf { it.isValid }
    // Ask for a code once per role while this screen is open.
    val asked = remember { mutableSetOf<String>() }
    LaunchedEffect(role, invite == null, sharing != null, online) {
        if (online && sharing != null && invite == null && role !in asked) {
            asked += role
            onCreateInvite(role)
        }
    }
    var removing by remember { mutableStateOf<Member?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().appBackground().statusBarsPadding()) {
        Box(Modifier.padding(start = 8.dp, top = 12.dp)) { BackButton(binder.name, onBack) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("Share binder")
                Text("Invite a friend", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "They scan this code with the Binder Tracker app to add ${binder.name} to their shelf.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                )
            }

            if (!online) {
                Notice("Sign in with Google to share binders. Your binders are then also kept safe in your Google account.")
            } else {
                problem?.let { Notice(it) }

                // Editor or Viewer, for people who join with the code.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("They join as", style = MaterialTheme.typography.titleSmall, color = AppColors.Muted)
                    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoleOption(
                            title = "Editor",
                            hint = "Can add, move and remove cards",
                            icon = R.drawable.ic_edit,
                            selected = role == Binder.ROLE_EDITOR,
                            onClick = { role = Binder.ROLE_EDITOR },
                            modifier = Modifier.weight(1f),
                        )
                        RoleOption(
                            title = "Viewer",
                            hint = "Can look, but not change anything",
                            icon = R.drawable.ic_search,
                            selected = role == Binder.ROLE_VIEWER,
                            onClick = { role = Binder.ROLE_VIEWER },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // The QR code and the code to type.
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = AppColors.Surface,
                    border = BorderStroke(1.dp, AppColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.holoTopStripe(4f).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (invite == null) {
                            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AppColors.Accent)
                            }
                            Text(
                                if (sharing == null) "Putting your binder online…" else "Making a code…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.Muted,
                            )
                        } else {
                            QrCode(InviteCodes.link(invite.code), Modifier.size(200.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("OR TYPE THIS CODE", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = AppColors.Muted)
                                Text(
                                    InviteCodes.pretty(invite.code),
                                    fontFamily = DisplayFont,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 28.sp,
                                    letterSpacing = 3.sp,
                                    color = AppColors.Ink,
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { copyCode(context, invite.code) },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, AppColors.BorderStrong),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Icon(painterResource(R.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Copy code")
                                }
                                androidx.compose.material3.Button(
                                    onClick = { sendCode(context, binder.name, invite.code) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.White),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Icon(painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Send code")
                                }
                            }
                            Text(
                                "Anyone with this code joins as ${if (role == Binder.ROLE_EDITOR) "an Editor" else "a Viewer"}. It stops working in 7 days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Muted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // Who uses this binder.
                val members = sharing?.members.orEmpty()
                if (members.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Text("People", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            if (members.size > 1) Text("Tap a role to change it", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AppColors.Surface,
                            border = BorderStroke(1.dp, AppColors.Border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                members.forEachIndexed { i, m ->
                                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = AppColors.Border)
                                    MemberRow(
                                        member = m,
                                        isOwner = m.uid == sharing?.ownerId,
                                        onToggleRole = {
                                            onSetRole(m.uid, if (m.role == Binder.ROLE_EDITOR) Binder.ROLE_VIEWER else Binder.ROLE_EDITOR)
                                        },
                                        onRemove = { removing = m },
                                    )
                                }
                            }
                        }
                        if (members.size == 1) {
                            Text("Nobody has joined yet.", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
                        }
                    }
                }

                if (members.size > 1 || sharing?.invites?.isNotEmpty() == true) {
                    TextButton(
                        onClick = { confirmStop = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger),
                        modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
                    ) { Text("Stop sharing this binder") }
                }
            }
        }
    }

    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${m.name}?") },
            text = { Text("${binder.name} leaves their shelf. You can invite them again later.") },
            confirmButton = {
                TextButton(
                    onClick = { removing = null; onRemove(m.uid) },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger),
                ) { Text("Remove ${m.name}") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop sharing?") },
            text = { Text("${binder.name} leaves everyone else's shelf, and the codes stop working. It stays on your shelf.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmStop = false; onStopSharing() },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger),
                ) { Text("Stop sharing") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep sharing") } },
        )
    }
}

@Composable
private fun RoleOption(
    title: String,
    hint: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface,
        border = BorderStroke(2.dp, if (selected) AppColors.Accent else AppColors.Border),
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = null, tint = if (selected) AppColors.AccentText else AppColors.Ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.Ink)
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
    }
}

@Composable
private fun MemberRow(member: Member, isOwner: Boolean, onToggleRole: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(AppColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(member.name.take(1).uppercase(), color = AppColors.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (isOwner) "${member.name} (you)" else member.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (isOwner) {
            Text(
                "Owner",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Check,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.CheckFill).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        } else {
            val roleName = if (member.role == Binder.ROLE_EDITOR) "Editor" else "Viewer"
            Surface(
                onClick = onToggleRole,
                shape = RoundedCornerShape(50),
                color = AppColors.Surface2,
                border = BorderStroke(1.dp, AppColors.BorderStrong),
                modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = "${member.name} is $roleName. Change role" },
            ) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(roleName, style = MaterialTheme.typography.titleSmall, color = AppColors.Ink, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_trash), contentDescription = "Remove ${member.name}", tint = AppColors.Muted)
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface2,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppColors.Ink, modifier = Modifier.padding(14.dp))
    }
}

/**
 * Draws [text] as a QR code. It's always black on white, in light and dark
 * mode, because that is what cameras read most reliably.
 */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
    }
    Canvas(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(8.dp)
            .semantics { contentDescription = "QR code to join this binder" },
    ) {
        val cells = matrix.width
        val cell = size.minDimension / cells
        for (y in 0 until cells) {
            for (x in 0 until cells) {
                if (matrix.get(x, y)) {
                    // A hair bigger than the cell, so no thin gaps show between squares.
                    drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

private fun copyCode(context: Context, code: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("Binder code", InviteCodes.pretty(code)))
}

/** Opens Android's share sheet (WhatsApp, Messages, email…) with the code. */
private fun sendCode(context: Context, binderName: String, code: String) {
    val text = "Join my Pokémon binder “$binderName” in Binder Tracker. Tap Join on the shelf and type this code: " +
        "${InviteCodes.pretty(code)} (it works for 7 days)."
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Send the binder code"))
}
