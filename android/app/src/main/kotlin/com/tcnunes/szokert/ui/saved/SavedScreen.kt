package com.tcnunes.szokert.ui.saved

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcnunes.szokert.R
import com.tcnunes.szokert.core.SavedWord
import com.tcnunes.szokert.core.ankiTsv
import com.tcnunes.szokert.saved.SavedStore
import com.tcnunes.szokert.ui.components.Serif
import com.tcnunes.szokert.ui.components.SzokertCard
import com.tcnunes.szokert.ui.theme.Szokert
import java.io.File
import java.time.LocalDate

/** Saved words, with an export Anki imports directly (SavedList.svelte). */
@Composable
fun SavedScreen(saved: SavedStore, onOpen: (SavedWord) -> Unit) {
    val c = Szokert.colors
    val context = LocalContext.current
    val list by saved.list.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    val fileName = "hungarian-words-${LocalDate.now()}.txt"
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            message = runCatching {
                context.contentResolver.openOutputStream(uri)!!.use { it.write(ankiTsv(list).toByteArray()) }
                "Saved. In Anki: File → Import, then choose this file."
            }.getOrElse { "Couldn't save the file (${it.message})." }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text("Saved words", color = c.text, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
                if (list.isEmpty()) {
                    Text("Tap ☆ on any entry to save it here. You can export saved words as a file Anki can import.", color = c.muted)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { saveFile.launch(fileName) }) { Text("Export for Anki") }
                        OutlinedButton(onClick = { share(context, fileName, ankiTsv(list)) }) {
                            Icon(painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Share")
                        }
                    }
                    message?.let { Text(it, color = c.muted, fontSize = 14.sp) }
                }
            }
        }
        items(list, key = { "${it.word}|${it.pos}" }) { s ->
            SzokertCard(onClick = { onOpen(s) }) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.word, color = c.accent, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        s.lookedUp?.let { Text("from $it", color = c.muted, fontStyle = FontStyle.Italic, fontSize = 14.sp) }
                        Text(s.meaning, color = c.text, fontSize = 15.sp)
                        s.example?.firstOrNull()?.let { Text(it, color = c.muted, fontSize = 14.sp) }
                    }
                    TextButton(onClick = { saved.remove(s) }) { Text("Remove", color = c.muted) }
                }
            }
        }
        if (list.isNotEmpty()) {
            item {
                Text(
                    "The file has Hungarian, English and an example sentence. In Anki: File → Import, choose the file, and " +
                        "map the columns to your note type's fields.",
                    color = c.muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

/** Send the export to another app (AnkiDroid, Drive, email…). */
private fun share(context: Context, name: String, text: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, name).apply { writeText(text) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Export saved words"))
}
