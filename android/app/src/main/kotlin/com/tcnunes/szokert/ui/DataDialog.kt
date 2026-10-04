package com.tcnunes.szokert.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.tcnunes.szokert.data.DataManager
import com.tcnunes.szokert.data.DataState
import com.tcnunes.szokert.data.Task
import com.tcnunes.szokert.ui.theme.Szokert
import kotlinx.coroutines.launch

/** The installed dictionary's version, with update and re-download. Becomes part of About later. */
@Composable
fun DataDialog(state: DataState, data: DataManager, onDismiss: () -> Unit) {
    val c = Szokert.colors
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val installed = state.installed
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dictionary data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (installed != null) {
                    Text("Version ${installed.version}, built ${day(installed.built)}", color = c.text)
                    Text("${megabytes(installed.bytes)} on this phone", color = c.muted)
                }
                when (val task = state.task) {
                    is Task.Running -> Text(describe(task.progress), color = c.accent)
                    is Task.Failed -> Text(task.message, color = c.secondary)
                    null -> {}
                }
                state.update?.let { Text("A newer version is available (${megabytes(it.bytes)}).", color = c.accent) }
                message?.let { Text(it, color = c.muted) }
                Text(
                    "Wiktionary (via Kaikki), Tatoeba and OpenSubtitles frequency data. New versions download " +
                        "by themselves once a day on wifi.",
                    color = c.faint,
                )
            }
        },
        confirmButton = {
            if (state.update != null) {
                TextButton(onClick = { data.download(); onDismiss() }) { Text("Update now") }
            } else {
                TextButton(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        scope.launch {
                            message = runCatching { data.checkForUpdate(force = true) }
                                .fold({ if (it == null) "You have the latest version." else null }, { it.message })
                            checking = false
                        }
                    },
                ) { Text(if (checking) "Checking…" else "Check for updates") }
            }
        },
        dismissButton = {
            TextButton(onClick = { data.downloadAgain(); onDismiss() }) { Text("Download again") }
        },
    )
}
