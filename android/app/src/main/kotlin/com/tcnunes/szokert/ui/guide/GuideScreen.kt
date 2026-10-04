package com.tcnunes.szokert.ui.guide

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcnunes.szokert.core.CASES
import com.tcnunes.szokert.core.Glossary
import com.tcnunes.szokert.core.MOODS
import com.tcnunes.szokert.core.PERSONS
import com.tcnunes.szokert.core.labelName
import com.tcnunes.szokert.db.SqliteDictionarySource
import com.tcnunes.szokert.ui.components.Pill
import com.tcnunes.szokert.ui.components.SectionHeading
import com.tcnunes.szokert.ui.components.Serif
import com.tcnunes.szokert.ui.components.rich
import com.tcnunes.szokert.ui.theme.Szokert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the labels on entries mean (Guide.svelte). [anchor] scrolls to an entry and flashes it:
 * a label tag ("proscribed"), "pos-<pos>", or a section ("pos", "commonness", "labels", "breakdown").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuideScreen(glossary: Glossary, source: SqliteDictionarySource?, anchor: String?) {
    val c = Szokert.colors
    val counts by produceState(emptyMap<String, Int>(), source) {
        value = source?.let { s -> withContext(Dispatchers.IO) { runCatching { s.posCounts().toMap() }.getOrDefault(emptyMap()) } }.orEmpty()
    }
    val posRows = glossary.allPos()
        .map { (pos, info) -> Triple(pos, info, counts[pos] ?: 0) }
        .filter { counts.isEmpty() || it.third > 0 }
        .sortedWith(compareByDescending<Triple<String, *, Int>> { it.third }.thenBy { glossary.describePos(it.first).label })

    // Item keys double as anchors.
    val keys = buildList {
        add("intro"); add("pos")
        posRows.forEach { add("pos-${it.first}") }
        add("commonness")
        add("labels")
        glossary.labelGroups.forEach { g -> add("group-$g"); glossary.labelsInGroup(g).forEach { add(it.first) } }
        add("breakdown")
    }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var flash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(anchor, posRows.size) {
        val i = anchor?.let { keys.indexOf(it) } ?: -1
        if (i >= 0) {
            list.scrollToItem(i)
            flash = anchor
            delay(1600)
            flash = null
        }
    }
    val jump: (String) -> Unit = { key -> scope.launch { list.animateScrollToItem(keys.indexOf(key).coerceAtLeast(0)) } }

    LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("intro") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text("Guide", color = c.text, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
                Text("What the labels on entries mean, and where the data comes from.", color = c.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((key, label) in listOf("pos" to "Parts of speech", "commonness" to "“Very common” and “common”", "labels" to "Usage labels", "breakdown" to "Terms in word breakdowns")) {
                        Text(label, color = c.accent, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(c.surface).clickable { jump(key) }.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }
        heading("pos", "Parts of speech", "Shown in italics next to each headword. The Hungarian grammar term is given in case you meet it in a textbook.")
        for ((pos, info, count) in posRows) {
            item("pos-$pos") {
                Flashing(flash == "pos-$pos") {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.accent, fontStyle = FontStyle.Italic)) { append(info.label) }
                            if (info.hu.isNotEmpty()) withStyle(SpanStyle(color = c.muted)) { append("  ${info.hu}") }
                            if (count > 0) withStyle(SpanStyle(color = c.faint, fontSize = 13.sp)) { append("  ${"%,d".format(count)} entries") }
                        },
                        fontSize = 15.sp,
                    )
                    Text(rich(info.text), color = c.text, fontSize = 15.sp)
                }
            }
        }
        item("commonness") {
            Flashing(flash == "commonness") {
                Commonness()
            }
        }
        heading("labels", "Usage labels", "The grey labels before a meaning come from Wiktionary. They describe how the word is used in that meaning.")
        for (group in glossary.labelGroups) {
            item("group-$group") { SectionHeading(group, Modifier.padding(top = 12.dp)) }
            for ((tag, info) in glossary.labelsInGroup(group)) {
                item(tag) {
                    Flashing(flash == tag) {
                        Pill(labelName(tag))
                        Text(rich(info.text), color = c.text, fontSize = 15.sp)
                    }
                }
            }
        }
        item("breakdown") { Breakdown() }
    }
}

private fun LazyListScope.heading(key: String, title: String, text: String) {
    item(key) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 20.dp)) {
            Text(title, color = Szokert.colors.text, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp)
            Text(text, color = Szokert.colors.muted, fontSize = 15.sp)
        }
    }
}

/** Briefly tinted when a link from a word page lands on it. */
@Composable
private fun Flashing(on: Boolean, content: @Composable () -> Unit) {
    val bg by animateColorAsState(if (on) Szokert.colors.secondarySoft else Color.Transparent, tween(if (on) 200 else 1200), label = "flash")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(bg).padding(vertical = 4.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

@Composable
private fun Commonness() {
    val c = Szokert.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 20.dp)) {
        Text("“Very common” and “common”", color = c.text, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp)
        Text(
            "“Very common” marks the 1,000 most frequent Hungarian words, and “common” the next 4,000 (ranks 1,001–5,000). " +
                "Words without a badge are less frequent, or never appeared in the source. There is no separate “uncommon” badge.",
            color = c.text, fontSize = 15.sp,
        )
        SectionHeading("How the ranking is calculated", Modifier.padding(top = 4.dp))
        for (step in listOf(
            "Source. About 3.15 million Hungarian word forms with their counts, taken from film and TV subtitles (FrequencyWords, OpenSubtitles 2018). Subtitles are close to everyday spoken language. Forms seen only once are ignored as likely typos.",
            "Forms → dictionary words. The list counts word forms, not dictionary words: *ház*, *házat* and *házban* are counted separately. Each form's count is credited to the dictionary word(s) it belongs to, using this dictionary's own inflection tables.",
            "Shared forms are split by weight. When several words share a form (*fog* is both “tooth” and “will / to hold”), its count is divided between them. A word gets a bigger share when it has more everyday meanings, and when its dictionary form is itself common in the subtitles.",
            "Ranking. Words are sorted by their total, giving a rank (1 = most frequent). For example *van* “to be” is #2, *ház* “house” #245, *vonat* “train” around #1,400.",
        )) Text(rich(step), color = c.text, fontSize = 15.sp)
        SectionHeading("Limits", Modifier.padding(top = 4.dp))
        Text(rich("Subtitles favour conversation, so formal and technical words rank lower than they would in newspapers. Words spelled the same can't be told apart reliably: *ment* is both the past of *megy* “went” and a verb “to save”. Phrases of several words have no rank."), color = c.text, fontSize = 15.sp)
        Text("The rank also orders search results (common words first), and adds top1000 / top5000 tags to the Anki export.", color = c.muted, fontSize = 15.sp)
    }
}

@Composable
private fun Breakdown() {
    val c = Szokert.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 20.dp, bottom = 32.dp)) {
        Text("Terms in word breakdowns", color = c.text, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp)
        Text(rich("When you look up an inflected form, its breakdown names the endings: *házat* = ház + accusative."), color = c.muted, fontSize = 15.sp)
        SectionHeading("Cases (noun endings)", Modifier.padding(top = 8.dp))
        for (case in CASES) Term(case.label, case.hint)
        SectionHeading("Verb forms", Modifier.padding(top = 8.dp))
        for (mood in MOODS.values) {
            Term(mood.label, mood.hint ?: when (mood.label) { "present" -> "happens now or habitually"; "past" -> "happened"; else -> "will happen" })
        }
        Term("indefinite", "Used when there is no object or a non-specific one: *látok egy házat* “I see a house”.")
        Term("definite", "Used with a specific object (the, this, him/her…): *látom a házat* “I see the house”.")
        Term("I → you", "The *-lak/-lek* form: “I … you”, e.g. *szeretlek* “I love you”.")
        Term("can", "The potential *-hat/-het*: *láthat* “can see, may see”.")
        Term("infinitive", "“To …”: *látni* “to see”. The personal infinitive adds a person: *látnom kell* “I have to see”.")
        SectionHeading("Persons", Modifier.padding(top = 8.dp))
        for (p in PERSONS) Term(p.pronoun, p.english)
    }
}

@Composable
private fun Term(term: String, text: String) {
    val c = Szokert.colors
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.text)) { append(term) }
            append("  ")
            append(rich(text))
        },
        color = c.muted,
        fontSize = 15.sp,
    )
}
