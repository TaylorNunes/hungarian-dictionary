package com.tcnunes.szokert.core

import kotlinx.serialization.Serializable

/**
 * A saved word, in the same shape the website stores (saved.svelte.ts), so lists can move between
 * the two. lemmaId is only a hint: ids shift between data builds, so entries are found again by
 * word and part of speech (findEntry).
 */
@Serializable
data class SavedWord(
    val lemmaId: Int,
    val word: String,
    val pos: String,
    val meaning: String,
    val example: List<String>? = null,
    /** The form that was looked up, if different from the headword. */
    val lookedUp: String? = null,
    /** Frequency rank (1 = most common). */
    val fr: Int? = null,
    val added: Long,
)

fun meaningOf(lemma: Lemma, max: Int = 3): String = lemma.s.take(max).joinToString("; ") { it.g }

fun savedWord(lemmaId: Int, lemma: Lemma, lookedUp: String?, now: Long): SavedWord = SavedWord(
    lemmaId = lemmaId,
    word = lemma.w,
    pos = lemma.pos,
    meaning = meaningOf(lemma),
    example = lemma.ex?.firstOrNull() ?: lemma.s.firstOrNull { !it.ex.isNullOrEmpty() }?.ex?.firstOrNull(),
    lookedUp = lookedUp?.takeIf { it.lowercase() != lemma.w.lowercase() },
    fr = lemma.fr,
    added = now,
)

private val FIELD_BREAKS = Regex("[\\t\\r\\n]+")

private fun field(s: String): String = FIELD_BREAKS.replace(s, " ").jsTrim()

/** Tab-separated text Anki imports directly (File → Import); header lines set the options. */
fun ankiTsv(list: List<SavedWord>): String {
    val lines = mutableListOf("#separator:tab", "#html:false", "#columns:Hungarian\tEnglish\tExample\tTags", "#tags column:4")
    for (s in list) {
        val example = s.example?.let { "${it[0]} — ${it[1]}" } ?: ""
        val tags = listOfNotNull("hungarian", commonness(s.fr)?.tag).joinToString(" ")
        lines.add(listOf(s.word, "${s.meaning} (${s.pos})", example, tags).joinToString("\t") { field(it) })
    }
    return lines.joinToString("\n") + "\n"
}
