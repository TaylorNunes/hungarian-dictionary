package com.tcnunes.szokert.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcnunes.szokert.R
import com.tcnunes.szokert.core.Analysis
import com.tcnunes.szokert.core.Glossary
import com.tcnunes.szokert.core.Lemma
import com.tcnunes.szokert.core.Section
import com.tcnunes.szokert.core.buildSections
import com.tcnunes.szokert.core.findEntry
import com.tcnunes.szokert.core.search
import com.tcnunes.szokert.db.SqliteDictionarySource
import com.tcnunes.szokert.saved.SavedStore
import com.tcnunes.szokert.ui.components.Breakdown
import com.tcnunes.szokert.ui.components.InflectionTables
import com.tcnunes.szokert.ui.components.SectionHeading
import com.tcnunes.szokert.ui.components.Senses
import com.tcnunes.szokert.ui.components.Sentence
import com.tcnunes.szokert.ui.components.WordHeader
import com.tcnunes.szokert.ui.theme.Szokert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface EntryUi {
    data object Loading : EntryUi
    data object Missing : EntryUi
    data class Ready(
        val id: Int,
        val lemma: Lemma,
        val analyses: List<Analysis>,
        val guessed: Boolean,
        val tables: List<Section>,
    ) : EntryUi
}

/**
 * A word's page (Entry.svelte): header, the breakdown of the searched form, meanings with their
 * examples, example sentences, and the inflection tables. [from]: the form that was searched.
 */
@Composable
fun EntryScreen(
    word: String,
    pos: String,
    id: Int?,
    from: String?,
    source: SqliteDictionarySource,
    glossary: Glossary,
    saved: SavedStore,
    onBack: () -> Unit,
    onWord: (String) -> Unit,
    onGuide: (String) -> Unit,
) {
    val c = Szokert.colors
    val savedList by saved.list.collectAsStateWithLifecycle()
    val ui by produceState<EntryUi>(EntryUi.Loading, word, pos, id, from, source) {
        value = withContext(Dispatchers.IO) {
            val (lemmaId, lemma) = source.findEntry(word, pos, id) ?: return@withContext EntryUi.Missing
            // Re-run the search the page was opened from, for this entry's full breakdown.
            val match = from?.let { f -> source.search(f).results.firstOrNull { it.lemmaId == lemmaId } }
            val tables = lemma.t?.let { buildSections(it, source.tags()) }.orEmpty()
            EntryUi.Ready(lemmaId, lemma, match?.analyses.orEmpty(), match?.guessed ?: false, tables)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp)) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = c.accent) }
            Text(if (from != null) "Results for “$from”" else "Back", color = c.accent, fontSize = 15.sp)
        }
        when (val e = ui) {
            EntryUi.Loading -> Text("Loading…", color = c.muted, modifier = Modifier.padding(16.dp))
            EntryUi.Missing -> Text("No entry for $word.", color = c.muted, modifier = Modifier.padding(16.dp))
            is EntryUi.Ready -> Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val lemma = e.lemma
                WordHeader(
                    lemma, glossary,
                    saved = savedList.any { it.word == lemma.w && it.pos == lemma.pos },
                    onToggleSave = { saved.toggle(e.id, lemma, from) },
                    large = true,
                    onGuide = onGuide,
                )
                if (from != null) Breakdown(e.analyses, lemma.w, from, e.guessed, compact = false)

                Part("Meanings") { Senses(lemma.s, examples = true, onLabel = onGuide, onWord = onWord) }
                lemma.ex?.takeIf { it.isNotEmpty() }?.let { examples ->
                    Part("Examples") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (ex in examples) Sentence(ex, onWord = onWord)
                        }
                    }
                }
                if (e.tables.isNotEmpty()) {
                    Part(if (lemma.pos == "verb") "Conjugation" else "Forms") { InflectionTables(e.tables) }
                }
            }
        }
    }
}

@Composable
private fun Part(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = Szokert.colors.border, modifier = Modifier.padding(top = 8.dp))
        SectionHeading(title)
        content()
    }
}
