package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.update.UpdateInfo

/**
 * "A new version is ready" pop-up.
 * [progress] is null before the download starts, then 0.0 to 1.0 while downloading.
 */
@Composable
fun UpdateDialog(
    update: UpdateInfo,
    currentVersion: String,
    progress: Float?,
    error: String?,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    val downloading = progress != null
    AlertDialog(
        onDismissRequest = { if (!downloading) onLater() },
        properties = DialogProperties(dismissOnClickOutside = false),
        containerColor = AppColors.White,
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Version ${update.versionName} is ready. You have version $currentVersion.")
                if (downloading) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading… ${(progress * 100).toInt()}%", color = AppColors.Muted)
                }
                if (error != null) Text(error, color = AppColors.Danger)
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate, enabled = !downloading) {
                Text(if (error != null) "Try again" else "Update now")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !downloading) { Text("Later") }
        },
    )
}
