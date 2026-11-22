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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.data.SpineColor
import com.hybrid87sg.bindertracker.ui.components.ShelfPlank
import com.hybrid87sg.bindertracker.ui.components.colorFromHex
import com.hybrid87sg.bindertracker.ui.theme.AppColors

@Composable
fun SignInScreen(
    firebaseReady: Boolean,
    signingIn: Boolean,
    error: String?,
    onGoogleSignIn: () -> Unit,
    onUseLocalOnly: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .appBackground()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 24.dp),
    ) {
        // A small shelf with three binders as the picture.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.ShelfBackground).padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                listOf(44 to SpineColor.Red, 52 to SpineColor.Navy, 48 to SpineColor.Green).forEach { (w, c) ->
                    Box(
                        Modifier.width(w.dp).height(150.dp)
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(colorFromHex(c.hex)),
                    )
                }
            }
            ShelfPlank(Modifier.padding(bottom = 20.dp), sideMargin = 20.dp)
        }

        Spacer(Modifier.height(32.dp))
        Text("Your binders, on every phone", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(10.dp))
        Text(
            if (firebaseReady) "Sign in so your shelf follows you to any phone."
            else "Google sign-in isn't connected yet. You can still use the app, and binders stay on this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.Muted,
        )

        Spacer(Modifier.weight(1f).height(32.dp))

        if (error != null) {
            Text(
                error,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = AppColors.Danger,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        OutlinedButton(
            onClick = onGoogleSignIn,
            enabled = firebaseReady && !signingIn,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(2.dp, if (firebaseReady) AppColors.Ink else AppColors.Border),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = AppColors.Surface, contentColor = AppColors.Ink),
        ) {
            if (signingIn) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.Ink)
                Spacer(Modifier.width(12.dp))
                Text("Signing in…", style = MaterialTheme.typography.labelLarge)
            } else {
                Text("Continue with Google", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onUseLocalOnly,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Ink),
        ) { Text("Use on this phone only") }
        Text(
            "Binders made without signing in are not backed up online.",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.Muted,
            textAlign = TextAlign.Center,
        )
    }
}
