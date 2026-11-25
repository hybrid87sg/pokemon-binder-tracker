package com.hybrid87sg.bindertracker.ui.screens

import com.hybrid87sg.bindertracker.ui.theme.appBackground
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.hybrid87sg.bindertracker.AppViewModel.JoinState
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.sync.InviteCodes
import com.hybrid87sg.bindertracker.sync.InvitePreview
import com.hybrid87sg.bindertracker.ui.components.BackButton
import com.hybrid87sg.bindertracker.ui.components.BottomBar
import com.hybrid87sg.bindertracker.ui.components.Eyebrow
import com.hybrid87sg.bindertracker.ui.components.PrimaryButton
import com.hybrid87sg.bindertracker.ui.components.holoTopStripe
import com.hybrid87sg.bindertracker.ui.theme.AppColors

/**
 * Join a binder a friend shared: scan their QR code, or type the code.
 * The app shows which binder it is and what you'll be able to do before
 * you join.
 */
@Composable
fun JoinScreen(
    state: JoinState,
    // A code from a QR link opened outside the app, looked up straight away.
    startCode: String?,
    onBack: () -> Unit,
    onLookUp: (String) -> Unit,
    onJoin: (InvitePreview) -> Unit,
    onReset: () -> Unit,
    // Called once the binder has been joined (go back to the shelf).
    onJoined: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    var scanError by rememberSaveable { mutableStateOf<String?>(null) }
    // True while our own camera screen for binder codes is open.
    var ringScanning by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        onReset()
        startCode?.let {
            typed = InviteCodes.pretty(it)
            onLookUp(it)
        }
    }
    LaunchedEffect(state) {
        if (state is JoinState.Joined) onJoined()
    }

    fun scan() {
        scanError = null
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val text = barcode.rawValue.orEmpty()
                InviteCodes.parse(text)?.let { typed = InviteCodes.pretty(it) }
                onLookUp(text)
            }
            .addOnFailureListener { scanError = "Couldn't open the QR code scanner. Type the code instead." }
    }

    if (ringScanning) {
        RingScanScreen(
            onFound = { code ->
                ringScanning = false
                typed = InviteCodes.pretty(code)
                onLookUp(code)
            },
            onTypeInstead = { ringScanning = false },
            onBack = { ringScanning = false },
        )
        return
    }

    Column(Modifier.fillMaxSize().appBackground().statusBarsPadding()) {
        Box(Modifier.padding(start = 8.dp, top = 12.dp)) {
            BackButton("Shelf", onClick = { if (state is JoinState.Found) onReset() else onBack() })
        }

        val found = (state as? JoinState.Found)?.invite
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (found == null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Eyebrow("Join a binder")
                    Text("Scan the binder code", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "On your friend's phone: open their binder and tap Share. Then scan the code it shows.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Muted,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PrimaryButton(
                        text = "Scan binder code",
                        onClick = { scanError = null; ringScanning = true },
                        icon = R.drawable.ic_camera,
                        enabled = state !is JoinState.Busy,
                    )
                    // For a friend who switched their Share screen to a QR code.
                    TextButton(onClick = ::scan, enabled = state !is JoinState.Busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Scan a QR code instead", color = AppColors.AccentText)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Or type the code", style = MaterialTheme.typography.titleSmall, color = AppColors.Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { if (it.length <= 12) typed = it },
                            placeholder = { Text("7Q4K-M2PX") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { onLookUp(typed) }),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = AppColors.Surface,
                                focusedContainerColor = AppColors.Surface,
                                unfocusedBorderColor = AppColors.BorderStrong,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Button(
                            onClick = { onLookUp(typed) },
                            enabled = typed.isNotBlank() && state !is JoinState.Busy,
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.White),
                            modifier = Modifier.height(56.dp),
                        ) { Text("Find") }
                    }
                }

                if (state is JoinState.Busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = AppColors.Accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("One moment…", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
                    }
                }
                val error = (state as? JoinState.Failed)?.message ?: scanError
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.Danger, fontWeight = FontWeight.Medium) }
            } else {
                InvitePreviewCard(found)
            }
        }

        if (found != null) {
            BottomBar {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PrimaryButton(text = "Join binder", onClick = { onJoin(found) })
                    TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Not now", color = AppColors.Ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun InvitePreviewCard(invite: InvitePreview) {
    val editor = invite.role == Binder.ROLE_EDITOR
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Eyebrow("You're invited")
        Text("${invite.ownerName} shared a binder with you", style = MaterialTheme.typography.headlineLarge)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.holoTopStripe(4f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(invite.binderName, style = MaterialTheme.typography.headlineMedium)
                Text("${invite.columns} × ${invite.rows} pockets · Owner: ${invite.ownerName}", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
            }
            Surface(shape = RoundedCornerShape(16.dp), color = AppColors.Surface2, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        painterResource(if (editor) R.drawable.ic_edit else R.drawable.ic_search),
                        contentDescription = null,
                        tint = AppColors.AccentText,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (editor) {
                            "You'll join as an Editor. You can add, move and remove cards. ${invite.ownerName} sees your changes, and you see theirs."
                        } else {
                            "You'll join as a Viewer. You can look through the binder and see new cards as they're added, but not change anything."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Ink,
                    )
                }
            }
        }
    }
}
