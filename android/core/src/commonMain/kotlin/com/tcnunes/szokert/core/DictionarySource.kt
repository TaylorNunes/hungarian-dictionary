package com.tcnunes.szokert.core

/** Where search reads the dictionary from: the downloaded database in the app (android/db). */
interface DictionarySource {
    /** Form rows whose folded spelling equals [folded], in index order. */
    suspend fun forms(folded: String): List<FormRow>

    /** English-index rows for the folded term, best first. */
    suspend fun english(term: String): List<EnglishRow>

    /** Headwords whose folded spelling starts with [prefix]. */
    suspend fun headsStarting(prefix: String): List<HeadRow>

    /** Headwords whose reversed folded spelling starts with [reversedSuffix]. */
    suspend fun headsEnding(reversedSuffix: String): List<HeadRow>

    suspend fun lemma(id: Int): Lemma?

    /** Tag sets, indexed by the numbers in form rows and tables. */
    suspend fun tags(): List<List<String>>
}

/**
 * The entry for a word page. Tries the lemma id from the link first; ids can shift when the data is
 * rebuilt, so if that entry no longer matches, falls back to the word itself (same part of speech
 * first). Matches findEntry() in data.ts.
 */
suspend fun DictionarySource.findEntry(word: String, pos: String, id: Int? = null): Pair<Int, Lemma>? {
    if (id != null) {
        val lemma = runCatching { lemma(id) }.getOrNull()
        if (lemma != null && lemma.w == word && lemma.pos == pos) return id to lemma
    }
    val ids = forms(fold(word)).filter { it.form == word && it.tag == 0 }.map { it.lemmaId }.distinct().sorted()
    val lemmas = ids.map { lemma(it) }
    val index = lemmas.indexOfFirst { it?.pos == pos }.takeIf { it >= 0 } ?: lemmas.indexOfFirst { it != null }
    return if (index >= 0) ids[index] to lemmas[index]!! else null
}
