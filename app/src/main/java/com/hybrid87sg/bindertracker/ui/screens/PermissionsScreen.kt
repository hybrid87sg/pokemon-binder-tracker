package com.hybrid87sg.bindertracker.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.R
import com.hybrid87sg.bindertracker.ui.components.BottomBar
import com.hybrid87sg.bindertracker.ui.components.PrimaryButton
import com.hybrid87sg.bindertracker.ui.theme.AppColors

/**
 * Asks Android for the permissions and shows [PermissionsScreen].
 * [cameraGranted] and [installGranted] are re-checked by the caller every
 * time the app comes back to the front (for example, back from Settings).
 */
@Composable
fun PermissionsGate(
    cameraGranted: Boolean,
    installGranted: Boolean,
    onCameraResult: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // True after Android says "don't ask again": the pop-up won't show any
    // more, so the button opens the app's Settings page instead.
    var cameraBlocked by rememberSaveable { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        ) {
            cameraBlocked = true
        }
        onCameraResult()
    }

    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }

    fun openInstallSettings() {
        // Android shows its own "Allow from this source" switch for our app.
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null)),
        )
    }

    PermissionsScreen(
        cameraGranted = cameraGranted,
        installGranted = installGranted,
        cameraBlocked = cameraBlocked,
        onAllowCamera = {
            if (cameraBlocked) openAppSettings() else cameraLauncher.launch(Manifest.permission.CAMERA)
        },
        onAllowInstall = ::openInstallSettings,
    )
}

/** Just the picture of the screen: no Android calls, so tests can draw it. */
@Composable
fun PermissionsScreen(
    cameraGranted: Boolean,
    installGranted: Boolean,
    cameraBlocked: Boolean,
    onAllowCamera: () -> Unit,
    onAllowInstall: () -> Unit,
) {
    val remaining = listOf(cameraGranted, installGranted).count { !it }

    Column(Modifier.fillMaxSize().background(AppColors.Paper).statusBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Before you start", style = MaterialTheme.typography.headlineLarge)
            Text(
                "The app needs two permissions. You only need to do this once.",
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.Muted,
            )
            Spacer(Modifier.height(4.dp))
            PermissionRow(
                icon = R.drawable.ic_camera,
                title = "Camera",
                why = "To photograph your binder pages and read the cards.",
                granted = cameraGranted,
                actionLabel = if (cameraBlocked) "Open settings" else "Allow camera",
                onAction = onAllowCamera,
            )
            PermissionRow(
                icon = R.drawable.ic_download,
                title = "Install updates",
                why = "So the app can install its own new versions. Android opens a settings page: turn on \"Allow from this source\", then press back.",
                granted = installGranted,
                actionLabel = "Open settings",
                onAction = onAllowInstall,
            )
        }
        BottomBar {
            PrimaryButton(
                text = when (remaining) {
                    0 -> "All set"
                    1 -> "1 permission left"
                    else -> "$remaining permissions left"
                },
                onClick = {},
                enabled = false,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    @DrawableRes icon: Int,
    title: String,
    why: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.White)
            .border(1.dp, AppColors.Border, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (granted) AppColors.Success else AppColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (granted) R.drawable.ic_check else icon),
                    contentDescription = null,
                    tint = AppColors.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(why, style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
            }
        }
        if (granted) {
            Text("Allowed", style = MaterialTheme.typography.titleMedium, color = AppColors.Success, modifier = Modifier.padding(start = 58.dp))
        } else {
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.White),
            ) { Text(actionLabel, style = MaterialTheme.typography.titleMedium) }
        }
    }
}
