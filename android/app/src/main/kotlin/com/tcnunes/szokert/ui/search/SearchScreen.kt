package com.tcnunes.szokert.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcnunes.szokert.R
import com.tcnunes.szokert.core.Glossary
import com.tcnunes.szokert.core.Lang
import com.tcnunes.szokert.core.PartialMatch
import com.tcnunes.szokert.core.Result
import com.tcnunes.szokert.core.codePoints
import com.tcnunes.szokert.core.fold
import com.tcnunes.szokert.data.DataState
import com.tcnunes.szokert.data.Task
import com.tcnunes.szokert.saved.SavedStore
import com.tcnunes.szokert.ui.components.Breakdown
import com.tcnunes.szokert.ui.components.Senses
import com.tcnunes.szokert.ui.components.Serif
import com.tcnunes.szokert.ui.components.SzokertCard
import com.tcnunes.szokert.ui.components.WordHeader
import com.tcnunes.szokert.ui.describe
import com.tcnunes.szokert.ui.megabytes
import com.tcnunes.szokert.ui.theme.Szokert

private val EXAMPLES = listOf("házat", "könyveimben", "láttalak", "szeretném", "house", "beautiful", "to see")
private const val PARTIAL_PAGE = 20

/** Search: one box for Hungarian or English, results as cards, then partial matches (App.svelte). */
@Composable
fun SearchScreen(
    vm: SearchViewModel,
    data: DataState,
    saved: SavedStore,
    glossary: Glossary,
    listState: LazyListState,
    onOpen: (Result, String?) -> Unit,
    onOpenPartial: (PartialMatch) -> Unit,
    onUpdate: () -> Unit,
) {
    val c = Szokert.colors
    val query by vm.query.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val savedList by saved.list.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var partialShown by rememberSaveable(ui.response?.query) { mutableIntStateOf(PARTIAL_PAGE) }

    // A new search starts at the top; coming back from a word page (same query) keeps the place.
    var shownQuery by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.response?.query) {
        val q = ui.response?.query
        if (shownQuery != null && q != shownQuery) listState.scrollToItem(0)
        shownQuery = q
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = { Text("Hungarian or English word…") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.query.value = "" }) { Icon(painterResource(R.drawable.ic_close), "Clear search", tint = c.faint) }
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        DataBanner(data, onUpdate)

        val r = ui.response
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        ) {
            ui.error?.let { e -> item { Text(e, color = c.secondary) } }
            if (r == null) {
                if (query.isBlank()) item { Welcome(onTry = { vm.lookUp(it) }) }
                return@LazyColumn
            }
            if (r.tokens.isNotEmpty()) {
                item { Tokens(r.tokens, empty = r.sections.isEmpty(), onTap = { vm.lookUp(it) }) }
            }
            if (r.sections.isEmpty() && r.tokens.isEmpty()) {
                item { Text("No entry for “${r.query}” in Hungarian or English.", color = c.muted, modifier = Modifier.padding(top = 8.dp)) }
            }
            for (section in r.sections) {
                item(key = "head-${section.lang}") {
                    Column {
                        if (section.lang == Lang.EN) {
                            SectionTitle("English “${section.term}” → Hungarian")
                        } else if (r.sections.size > 1) {
                            SectionTitle("Hungarian ${section.term}")
                        }
                        val n = section.results.size
                        val first = section.results.first()
                        Text(
                            buildAnnotatedString {
                                append(if (n == 1) "1 entry" else "$n entries")
                                val note = when {
                                    section.lang == Lang.HU && first.guessed -> "no exact form in the tables; best guess by removing suffixes"
                                    section.lang == Lang.EN && first.guessed -> "matched the base form “${section.term}”"
                                    else -> null
                                }
                                if (note != null) {
                                    append(" · ")
                                    withStyle(SpanStyle(color = c.secondary)) { append(note) }
                                }
                            },
                            color = c.muted,
                            fontSize = 14.sp,
                        )
                    }
                }
                items(section.results, key = { "${section.lang}-${it.lemmaId}" }) { result ->
                    val isSaved = savedList.any { it.word == result.lemma.w && it.pos == result.lemma.pos }
                    SzokertCard(onClick = { onOpen(result, if (result.sense == null) r.query else null) }) {
                        WordHeader(result.lemma, glossary, isSaved, onToggleSave = { saved.toggle(result.lemmaId, result.lemma, r.query) })
                        Breakdown(result.analyses, result.lemma.w, r.query, result.guessed, compact = true)
                        Senses(result.lemma.s, highlight = result.sense)
                    }
                }
            }
            if (r.partial.isNotEmpty()) {
                item(key = "partial-head") {
                    Column(Modifier.padding(top = 12.dp)) {
                        SectionTitle("Words starting or ending with “${r.query}”")
                        Text(if (r.partial.size >= 200) "200+ words" else "${r.partial.size} words", color = c.muted, fontSize = 14.sp)
                    }
                }
                items(r.partial.take(partialShown), key = { "p-${it.lemmaId}" }) { PartialRow(it, r.query) { onOpenPartial(it) } }
                if (r.partial.size > partialShown) {
                    item(key = "partial-more") {
                        TextButton(onClick = { partialShown += PARTIAL_PAGE }, modifier = Modifier.fillMaxWidth()) {
                            Text("Show ${minOf(PARTIAL_PAGE, r.partial.size - partialShown)} more")
                        }
                    }
                }
            }
            item { Text("", modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Szokert.colors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
}

/** A dictionary update running in the background, or one waiting to be fetched. */
@Composable
private fun DataBanner(state: DataState, onUpdate: () -> Unit) {
    val c = Szokert.colors
    val task = state.task
    when {
        task is Task.Running -> Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Updating the dictionary: ${describe(task.progress)}", color = c.muted, fontSize = 13.sp)
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        state.update != null -> Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("New dictionary data (${megabytes(state.update.bytes)})", color = c.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onUpdate) { Text("Update") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(onTry: (String) -> Unit) {
    val c = Szokert.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        Text("Szókert", color = c.accent, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 38.sp)
        Text("Cultivate your knowledge.", color = c.secondary, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 19.sp)
        Text("Hungarian ⇄ English dictionary", color = c.text, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
        Text(
            "Type any form of a Hungarian word to see its dictionary form, meaning, how it's built and example sentences, " +
                "or type an English word to find the Hungarian.",
            color = c.muted,
        )
        Text("Try", color = c.muted, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in EXAMPLES) Chip(w) { onTry(w) }
        }
        Text(
            buildAnnotatedString {
                append("Accents are optional: ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("orom") }
                append(" finds ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("öröm") }
                append(". Below the results you'll also find words that start or end with what you typed.")
            },
            color = c.muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The words of a multi-word query, each a separate lookup. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tokens(tokens: List<String>, empty: Boolean, onTap: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (empty) Text("Tap a word to look it up.", color = Szokert.colors.muted, fontSize = 14.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in tokens) Chip(t) { onTap(t) }
        }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    val c = Szokert.colors
    Text(
        text,
        color = c.accent,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(c.surface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** One compact partial match, the typed part highlighted (PartialList.svelte). */
@Composable
private fun PartialRow(m: PartialMatch, query: String, onClick: () -> Unit) {
    val c = Szokert.colors
    val n = codePoints(fold(query)).size
    val chars = codePoints(m.word)
    val word = buildAnnotatedString {
        chars.forEachIndexed { i, ch ->
            val hit = (m.start && i < n) || (m.end && i >= chars.size - n)
            if (hit) withStyle(SpanStyle(color = c.accent)) { append(ch) } else append(ch)
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(word, color = c.text, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1)
        Text(m.pos, color = c.faint, fontStyle = FontStyle.Italic, fontSize = 13.sp, maxLines = 1)
        Text(m.gloss, color = c.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
