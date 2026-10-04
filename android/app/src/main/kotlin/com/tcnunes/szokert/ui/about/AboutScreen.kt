package com.tcnunes.szokert.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcnunes.szokert.data.DataManager
import com.tcnunes.szokert.data.DownloadWorker
import com.tcnunes.szokert.data.Task
import com.tcnunes.szokert.settings.Settings
import com.tcnunes.szokert.settings.ThemeChoice
import com.tcnunes.szokert.ui.components.SectionHeading
import com.tcnunes.szokert.ui.components.Serif
import com.tcnunes.szokert.ui.day
import com.tcnunes.szokert.ui.describe
import com.tcnunes.szokert.ui.megabytes
import com.tcnunes.szokert.ui.theme.Szokert
import kotlinx.coroutines.launch

/** About Szókert (About.svelte): what it does, the dictionary data, the theme, sources and licences. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(data: DataManager, settings: Settings, onGuide: () -> Unit) {
    val c = Szokert.colors
    val context = LocalContext.current
    val state by data.state.collectAsStateWithLifecycle()
    val theme by settings.theme.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("About Szókert", color = c.text, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
        Text("Cultivate your knowledge.", color = c.secondary, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 18.sp)
        Body(
            "Szókert (“word garden”) is a free Hungarian ⇄ English dictionary. Type any form of a Hungarian word to see its " +
                "dictionary form, meaning, a breakdown of its endings, its full inflection table and example sentences.",
        )
        Body(
            "Type an English word to find the Hungarian. English lookup searches the English meanings of the Hungarian entries, " +
                "so it finds words by their definitions. Common English endings (houses, running) and frequent irregular forms " +
                "(went, children) are reduced to the base word. Words without accents are looked up in both languages; anything " +
                "with Hungarian accents is treated as Hungarian.",
        )
        Text(
            buildAnnotatedString {
                append("What do labels like proscribed, not comparable or very common mean? See the ")
                withLink(LinkAnnotation.Clickable("guide", TextLinkStyles(SpanStyle(color = c.accent))) { onGuide() }) { append("Guide") }
                append(".")
            },
            color = c.text, fontSize = 15.sp,
        )

        SectionHeading("Dictionary data", Modifier.padding(top = 12.dp))
        state.installed?.let { inst ->
            Body("Version ${inst.version}, built ${day(inst.built)} · ${megabytes(inst.bytes)} on this phone")
        }
        when (val task = state.task) {
            is Task.Running -> {
                Text(describe(task.progress), color = c.accent, fontSize = 15.sp)
                val p = DownloadWorker.percentOf(task.progress)
                if (p >= 0) LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            is Task.Failed -> Text(task.message, color = c.secondary, fontSize = 15.sp)
            null -> {}
        }
        state.update?.let { Text("A newer version is available (${megabytes(it.bytes)}).", color = c.accent, fontSize = 15.sp) }
        message?.let { Text(it, color = c.muted, fontSize = 15.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.update != null) {
                OutlinedButton(onClick = { data.download() }) { Text("Update now") }
            } else {
                OutlinedButton(
                    enabled = !checking && state.task !is Task.Running,
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
            OutlinedButton(enabled = state.task !is Task.Running, onClick = { data.downloadAgain() }) { Text("Download again") }
        }
        Text("New versions download by themselves once a day on wifi.", color = c.faint, fontSize = 14.sp)

        SectionHeading("Theme", Modifier.padding(top = 12.dp))
        SingleChoiceSegmentedButtonRow {
            ThemeChoice.entries.forEachIndexed { i, choice ->
                SegmentedButton(
                    selected = theme == choice,
                    onClick = { settings.setTheme(choice) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeChoice.entries.size),
                ) { Text(choice.label) }
            }
        }

        SectionHeading("Sources and licences", Modifier.padding(top = 12.dp))
        Linked(
            "Definitions and inflection tables: " to null, "English Wiktionary" to "https://en.wiktionary.org/",
            " contributors, extracted by " to null, "Kaikki.org" to "https://kaikki.org/dictionary/Hungarian/",
            " (Wiktextract). Licensed " to null, "CC BY-SA 4.0" to "https://creativecommons.org/licenses/by-sa/4.0/",
            ". Verb conjugation labels were reconstructed from the table layout and may occasionally be wrong." to null,
        )
        Linked(
            "Example sentences: " to null, "Tatoeba" to "https://tatoeba.org/", " contributors, licensed " to null,
            "CC BY 2.0 FR" to "https://creativecommons.org/licenses/by/2.0/fr/", "." to null,
        )
        Linked(
            "Word frequencies (the “common” labels and result ranking): counts from Hungarian film and TV subtitles, " to null,
            "FrequencyWords" to "https://github.com/hermitdave/FrequencyWords", " by Hermit Dave, based on OpenSubtitles 2018, licensed " to null,
            "CC BY-SA 4.0" to "https://creativecommons.org/licenses/by-sa/4.0/", "." to null,
        )
        Linked("App code: MIT licence. Source on " to null, "GitHub" to "https://github.com/TaylorNunes/szokert", "." to null)
        version?.let { Text("Szókert $it", color = c.faint, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun Body(text: String) = Text(text, color = Szokert.colors.text, fontSize = 15.sp)

/** Text with some runs linking to web pages. */
@Composable
private fun Linked(vararg runs: Pair<String, String?>) {
    val c = Szokert.colors
    val text: AnnotatedString = buildAnnotatedString {
        for ((t, url) in runs) {
            if (url == null) append(t)
            else withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = c.accent)))) { append(t) }
        }
    }
    Text(text, color = c.text, fontSize = 15.sp)
}
