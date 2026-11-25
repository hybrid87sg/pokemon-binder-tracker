package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hybrid87sg.bindertracker.data.ExpansionOption
import com.hybrid87sg.bindertracker.ui.theme.AppColors

/**
 * A panel for choosing which expansions a binder collects. Tap expansions to
 * tick them (as many as you like), then "Done".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectingSheet(
    expansions: List<ExpansionOption>,
    selected: List<String>,
    onDismiss: () -> Unit,
    onDone: (List<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Kept in the order they were ticked, so the first one stays first on the Missing page.
    var picked by remember { mutableStateOf(selected) }
    var find by rememberSaveable { mutableStateOf("") }
    val text = find.trim()
    val shown = if (text.isEmpty()) expansions else expansions.filter {
        it.name.contains(text, ignoreCase = true) || it.seriesName.contains(text, ignoreCase = true)
    }
    val groups = shown.groupBy { it.seriesId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppColors.Surface) {
        Column(Modifier.fillMaxHeight(0.9f).navigationBarsPadding()) {
            Column(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("What does this binder collect?", style = MaterialTheme.typography.titleLarge, color = AppColors.Ink)
                        Text(
                            if (picked.isEmpty()) "Tick one or more expansions" else "${picked.size} ticked",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Muted,
                        )
                    }
                    TextButton(onClick = { onDone(picked) }) { Text("Done", fontWeight = FontWeight.Bold, color = AppColors.AccentText) }
                }
                OutlinedTextField(
                    value = find,
                    onValueChange = { find = it },
                    label = { Text("Find an expansion") },
                    placeholder = { Text("e.g. 151") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = AppColors.Surface,
                        focusedContainerColor = AppColors.Surface,
                        unfocusedBorderColor = AppColors.BorderStrong,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                )
            }
            if (expansions.isEmpty()) {
                Text(
                    "The card list hasn't downloaded yet. Connect to the internet and open Search once, then try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted,
                    modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
                groups.forEach { (seriesId, sets) ->
                    item(key = "series-$seriesId") {
                        Text(
                            sets.first().seriesName.ifEmpty { "Other" }.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Muted,
                            modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(sets, key = { "set-${it.id}" }) { set ->
                        val on = set.id in picked
                        Surface(
                            onClick = { picked = if (on) picked - set.id else picked + set.id },
                            shape = RoundedCornerShape(10.dp),
                            color = if (on) AppColors.Surface2 else AppColors.Surface,
                            modifier = Modifier.fillMaxWidth().semantics { role = Role.Checkbox; this.selected = on },
                        ) {
                            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    set.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (on) FontWeight.SemiBold else null,
                                    color = if (on) AppColors.AccentText else AppColors.Ink,
                                    modifier = Modifier.weight(1f),
                                )
                                TickBox(on)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A small rounded square that fills in with a tick when [on]. */
@Composable
fun TickBox(on: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.size(22.dp).clip(shape)
            .background(if (on) AppColors.Accent else AppColors.Surface)
            .border(1.5.dp, if (on) AppColors.Accent else AppColors.BorderStrong, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) com.hybrid87sg.bindertracker.ui.components.SmallIcon(com.hybrid87sg.bindertracker.R.drawable.ic_check, AppColors.White, size = 16.dp)
    }
}
