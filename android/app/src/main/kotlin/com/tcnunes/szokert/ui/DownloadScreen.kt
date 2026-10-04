package com.tcnunes.szokert.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcnunes.szokert.data.DownloadWorker
import com.tcnunes.szokert.data.Task
import com.tcnunes.szokert.ui.theme.Szokert

/** First launch: the dictionary lives on the phone, so it is downloaded once (then works offline). */
@Composable
fun DownloadScreen(task: Task?, onDownload: () -> Unit) {
    val c = Szokert.colors
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Szókert", color = c.accent, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 40.sp)
        Text("Cultivate your knowledge.", color = c.secondary, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 19.sp)
        Text("Hungarian ⇄ English dictionary", color = c.text, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
        Text(
            "The dictionary is stored on your phone, so it works offline. It's a one-time download of about " +
                "23 MB (about 95 MB once installed); updates come by themselves on wifi.",
            color = c.muted,
        )
        when (task) {
            is Task.Running -> {
                val percent = DownloadWorker.percentOf(task.progress)
                Text(describe(task.progress), color = c.text, modifier = Modifier.padding(top = 16.dp))
                if (percent >= 0) {
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text("You can leave the app; the download carries on.", color = c.faint, fontSize = 14.sp)
            }
            is Task.Failed -> {
                Text(task.message, color = c.secondary, modifier = Modifier.padding(top = 16.dp))
                Button(onClick = onDownload) { Text("Try again") }
            }
            null -> Button(onClick = onDownload, modifier = Modifier.padding(top = 16.dp)) { Text("Download the dictionary") }
        }
    }
}
