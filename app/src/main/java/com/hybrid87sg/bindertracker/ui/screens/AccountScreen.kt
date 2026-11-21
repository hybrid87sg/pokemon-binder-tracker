package com.hybrid87sg.bindertracker.ui.screens

import androidx.compose.foundation.BorderStroke
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
import com.hybrid87sg.bindertracker.ui.theme.AppColors

@Composable
fun AccountScreen(
    user: AppUser,
    version: String,
    updateMessage: String?,
    onCheckForUpdates: () -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(AppColors.Paper).statusBarsPadding()) {
        Box(Modifier.padding(start = 8.dp, top = 12.dp)) { BackButton("Shelf", onBack) }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Account", style = MaterialTheme.typography.headlineMedium)

            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.White)
                    .border(1.dp, AppColors.Border, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(AppColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = AppColors.White, modifier = Modifier.size(26.dp))
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

            Text(
                if (user.isLocalOnly) "Your binders are saved on this phone only. Sign in with Google to keep them on every phone you use."
                else "Your binders are saved to this account on this phone. Syncing them to your other phones comes in a later update.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Muted,
            )

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(2.dp, AppColors.Ink),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
            ) {
                Text(if (user.isLocalOnly) "Go to sign-in" else "Sign out", style = MaterialTheme.typography.labelLarge)
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
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Accent),
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
