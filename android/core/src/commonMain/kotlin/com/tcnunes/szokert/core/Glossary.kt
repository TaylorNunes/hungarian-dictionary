package com.tcnunes.szokert.core

import kotlinx.serialization.Serializable

/**
 * Descriptions for parts of speech and Wiktionary usage labels (src/lib/glossary.json, copied into
 * the app at build time so there is one source). Matches glossary.ts.
 */

@Serializable
data class LabelInfo(val group: String, val text: String)

@Serializable
data class PosInfo(
    /** Shown in entry headers, e.g. "adjective" for "adj". */
    val label: String,
    /** Hungarian grammatical term, e.g. "melléknév". */
    val hu: String,
    val text: String,
)

@Serializable
class Glossary(
    val labelGroups: List<String>,
    private val labels: Map<String, LabelInfo>,
    private val pos: Map<String, PosInfo>,
) {
    fun describeLabel(tag: String): LabelInfo? = labels[tag]

    fun describePos(pos: String): PosInfo = this.pos[pos] ?: PosInfo(labelName(pos), "", "")

    /** Labels of one group, alphabetically by displayed name. */
    fun labelsInGroup(group: String): List<Pair<String, LabelInfo>> =
        labels.entries.filter { it.value.group == group }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { labelName(it.key) })
            .map { it.key to it.value }

    fun allPos(): List<Pair<String, PosInfo>> = pos.entries.map { it.key to it.value }

    companion object {
        fun parse(json: String): Glossary = DictionaryJson.decodeFromString(serializer(), json)
    }
}

/** A label as displayed: "not-comparable" → "not comparable". */
fun labelName(tag: String): String = tag.replace('-', ' ')

data class Run(val text: String, val em: Boolean)

private val EMPHASIS = Regex("\\*([^*]+)\\*")

/** Text with *asterisks* marking Hungarian words, split into plain and emphasised runs. */
fun emphasisRuns(text: String): List<Run> {
    // JavaScript's split() with a capture group: plain, captured, plain, captured, …
    val pieces = mutableListOf<String>()
    var at = 0
    for (m in EMPHASIS.findAll(text)) {
        pieces.add(text.substring(at, m.range.first))
        pieces.add(m.groupValues[1])
        at = m.range.last + 1
    }
    pieces.add(text.substring(at))
    return pieces.mapIndexed { i, t -> Run(t, i % 2 == 1) }.filter { it.text.isNotEmpty() }
}
