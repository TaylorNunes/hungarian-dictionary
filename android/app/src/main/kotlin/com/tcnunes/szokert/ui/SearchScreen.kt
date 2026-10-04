package com.tcnunes.szokert.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcnunes.szokert.core.CombinedResponse
import com.tcnunes.szokert.core.DictionarySource
import com.tcnunes.szokert.core.Lang
import com.tcnunes.szokert.core.Result
import com.tcnunes.szokert.core.cleanQuery
import com.tcnunes.szokert.core.searchBoth
import com.tcnunes.szokert.data.DataState
import com.tcnunes.szokert.data.Task
import com.tcnunes.szokert.ui.theme.Szokert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A first, plain search screen that proves the downloaded data works; the full UI is milestone M4. */
@Composable
fun SearchScreen(source: DictionarySource, state: DataState, onUpdate: () -> Unit, onShowData: () -> Unit) {
    val c = Szokert.colors
    var query by rememberSaveable { mutableStateOf("") }
    var response by remember { mutableStateOf<CombinedResponse?>(null) }

    LaunchedEffect(query, source) {
        val q = cleanQuery(query)
        if (q.isEmpty()) {
            response = null
            return@LaunchedEffect
        }
        delay(180)
        response = withContext(Dispatchers.IO) { source.searchBoth(q) }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Szókert", color = c.accent, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onShowData) { Text("Data") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Hungarian or English word…") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        when (val task = state.task) {
            is Task.Running -> {
                Text("Updating the dictionary: ${describe(task.progress)}", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            else -> state.update?.let { up ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("New dictionary data (${megabytes(up.bytes)})", color = c.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onUpdate) { Text("Update") }
                }
            }
        }
        val r = response
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            if (r != null && r.sections.isEmpty()) {
                item { Text("No entry for “${r.query}” in Hungarian or English.", color = c.muted) }
            }
            for (section in r?.sections.orEmpty()) {
                if (section.lang == Lang.EN) {
                    item { Text("English “${section.term}” → Hungarian", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
                items(section.results, key = { "${section.lang}-${it.lemmaId}" }) { ResultRow(it) }
            }
            if (r != null && r.partial.isNotEmpty()) {
                item {
                    Text("Words starting or ending with “${r.query}” (${r.partial.size})", color = c.muted, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                items(r.partial.take(30), key = { "p-${it.lemmaId}" }) { p ->
                    Text(
                        "${p.word}  ·  ${p.gloss}",
                        color = c.text,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(r: Result) {
    val c = Szokert.colors
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(r.lemma.w, color = c.text, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Text("  ${r.lemma.pos}", color = c.accent, fontStyle = FontStyle.Italic)
        }
        r.analyses.firstOrNull()?.let { a ->
            Text("${a.form} = ${r.lemma.w} + ${a.parts.joinToString(" + ") { it.label }}", color = c.muted, fontSize = 14.sp)
        }
        r.lemma.s.take(3).forEachIndexed { i, s ->
            Text("${i + 1}. ${s.g}", color = if (i == r.sense) c.accentStrong else c.text, fontSize = 15.sp)
        }
    }
}
