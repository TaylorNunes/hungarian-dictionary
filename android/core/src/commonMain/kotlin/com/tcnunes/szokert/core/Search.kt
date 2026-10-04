package com.tcnunes.szokert.core

/**
 * Hungarian and English lookup, and how the two are combined. A line-for-line port of search.ts:
 * android/db's GoldenParityTest checks both give the same results for hundreds of queries.
 */

data class Analysis(
    /** The form as found in the dictionary. */
    val form: String,
    val parts: List<Part>,
)

data class Result(
    val lemmaId: Int,
    val lemma: Lemma,
    val analyses: List<Analysis>,
    /** Matched with the accents exactly as typed. */
    val exact: Boolean,
    /** Found through the suffix stripper (or English de-inflection) rather than directly. */
    val guessed: Boolean,
    /** For English lookups: index of the sense whose gloss matched. */
    val sense: Int? = null,
)

data class SearchResponse(
    val query: String,
    val results: List<Result>,
    /** Words of a multi-word query, so each can be looked up. */
    val tokens: List<String>,
)

data class EnglishResponse(
    /** The English term that matched (after de-inflection), or the normalised query. */
    val term: String,
    val results: List<Result>,
    /** The query itself was in the index (not reached by de-inflection). */
    val exact: Boolean,
    /** The best match has the term leading its gloss ("house" in "house, building"). */
    val strong: Boolean,
)

enum class Lang { HU, EN }

data class ResultSection(
    val lang: Lang,
    /** For EN: the English term that matched. */
    val term: String,
    val results: List<Result>,
)

data class CombinedResponse(
    val query: String,
    val sections: List<ResultSection>,
    val tokens: List<String>,
    /** Other headwords starting or ending with the query, most common first. */
    val partial: List<PartialMatch>,
)

private const val MAX_RESULTS = 12
private const val MAX_STEM_CANDIDATES = 80
private val FORM_OF = Regex("(?<![A-Za-z0-9_])(form|spelling|misspelling) of(?![A-Za-z0-9_])")
private val AFFIX_POS = setOf("suffix", "prefix", "infix")

private class Hit(val row: FormRow, val steps: List<Step>)

private class Group(var steps: Int) {
    val hits = mutableListOf<Hit>()
    var exact = false
    var head = false
}

private suspend fun DictionarySource.hitsFor(word: String, accentsTyped: Boolean, steps: List<Step> = emptyList()): List<Hit> {
    val lower = word.lowercase()
    val rows = forms(fold(word))
    val exact = rows.filter { it.form.lowercase() == lower }
    val chosen = if (accentsTyped && exact.isNotEmpty()) exact else rows
    return chosen.map { Hit(it, steps) }
}

private suspend fun DictionarySource.stemmedHits(word: String, accentsTyped: Boolean): List<Hit> {
    var bestLevel = Int.MAX_VALUE
    val hits = mutableListOf<Hit>()
    for (c in candidates(word).take(MAX_STEM_CANDIDATES)) {
        if (c.steps.size > bestLevel) break
        val found = hitsFor(c.stem, accentsTyped, c.steps)
        if (found.isNotEmpty()) {
            hits.addAll(found)
            bestLevel = c.steps.size
        }
    }
    return hits
}

suspend fun DictionarySource.search(raw: String): SearchResponse {
    val query = cleanQuery(raw)
    val tokens = if (' ' in query) TOKEN.findAll(query).map { it.value }.distinct().toList() else emptyList()
    if (query.isEmpty()) return SearchResponse(query, emptyList(), tokens)

    val accentsTyped = hasAccents(query)
    var hits = hitsFor(query, accentsTyped)
    var guessed = false
    if (hits.isEmpty() && tokens.isEmpty()) {
        hits = stemmedHits(query, accentsTyped)
        guessed = hits.isNotEmpty()
    }

    val tagSets = tags()
    val lower = query.lowercase()

    // Group rows by lemma; rank before fetching lemma records.
    val byLemma = LinkedHashMap<Int, Group>()
    for (h in hits) {
        val g = byLemma.getOrPut(h.row.lemmaId) { Group(h.steps.size) }
        g.hits.add(h)
        if (h.row.form.lowercase() == lower) g.exact = true
        if (h.row.tag == 0) g.head = true
        g.steps = minOf(g.steps, h.steps.size)
    }
    val preRanked = byLemma.entries
        .sortedWith(compareBy<Map.Entry<Int, Group>> { it.value.steps }.thenByDescending { it.value.exact }.thenByDescending { it.value.head })
        .take(MAX_RESULTS * 2)

    val scored = mutableListOf<Pair<Result, Double>>()
    for ((lemmaId, g) in preRanked) {
        val lemma = lemma(lemmaId) ?: continue
        val analyses = mutableListOf<Analysis>()
        val seen = HashSet<String>()
        for (hit in g.hits) {
            val form = hit.row.form
            val tags = tagSets.getOrNull(hit.row.tag) ?: emptyList()
            val prefix = hit.steps.filter { it.prefix }.map { it.part }
            val suffix = hit.steps.filter { !it.prefix }.map { it.part }
            // For the dictionary form itself keep only usage labels (rare, dialectal…), which have no hint.
            val own = if (isBaseForm(tags)) describe(tags).filter { it.hint == null } else describe(tags)
            val parts = prefix + own + suffix
            if (parts.isEmpty() && form == lemma.w) continue
            val key = form + "|" + parts.joinToString("+") { it.label }
            if (!seen.add(key)) continue
            analyses.add(Analysis(form, parts))
        }
        var score = 0.0
        if (g.exact) score += 100
        if (g.head) score += 10
        score -= g.steps * 30
        score += minOf(lemma.s.size, 6) + (if (lemma.t != null) 3 else 0) + (if (lemma.ex != null) 2 else 0) + frequencyBonus(lemma.fr)
        if (lemma.pos == "name") score -= 6
        if (lemma.pos == "character") score -= 15
        if (lemma.pos in AFFIX_POS && !query.startsWith("-")) score -= 8
        if (lemma.s.all { FORM_OF.containsMatchIn(it.g) }) score -= 4
        scored.add(Result(lemmaId, lemma, analyses, g.exact, guessed) to score)
    }
    val results = scored.sortedWith(compareByDescending<Pair<Result, Double>> { it.second }.thenBy { it.first.lemmaId }).map { it.first }
    return SearchResponse(query, results.take(MAX_RESULTS), tokens)
}

// ----------------------------------------------------------------------------- English → Hungarian

suspend fun DictionarySource.searchEnglish(raw: String): EnglishResponse {
    val query = normalizeEnglish(cleanQuery(raw))
    val empty = EnglishResponse(query, emptyList(), exact = false, strong = false)
    if (query.isEmpty()) return empty

    var term = query
    var rows = english(fold(term))
    if (rows.isEmpty()) {
        for (base in deinflect(query)) {
            rows = english(fold(base))
            if (rows.isNotEmpty()) {
                term = base
                break
            }
        }
    }
    if (rows.isEmpty()) return empty

    val exact = term == query
    val results = rows.take(MAX_RESULTS).mapNotNull { row ->
        lemma(row.lemmaId)?.let { Result(row.lemmaId, it, emptyList(), exact, guessed = !exact, sense = row.sense) }
    }
    return EnglishResponse(term, results, exact, strong = exact && rows[0].position == 0)
}

// ----------------------------------------------------------------------------- both directions

private fun glossMentions(lemma: Lemma, term: String): Boolean {
    val re = Regex("(^|[^\\p{L}])" + Regex.escape(term) + "($|[^\\p{L}])", setOf(RegexOption.IGNORE_CASE))
    return lemma.s.any { re.containsMatchIn(it.g) }
}

/**
 * Order the Hungarian and English sections: the stronger match goes first, Hungarian on a tie.
 * Hungarian is strong when the query is a dictionary form as typed, English when the query
 * leads a gloss.
 */
fun rankSections(hu: SearchResponse, en: EnglishResponse?): List<ResultSection> {
    val huGuessed = hu.results.isNotEmpty() && hu.results[0].guessed
    val huExact = hu.results.any { it.exact && !it.guessed }
    // A Hungarian stemmer guess is noise when the word is English ("houses").
    val keepHu = !(huGuessed && en?.results?.isNotEmpty() == true)
    // A Hungarian word glossed with the same English word is a loan ("house" = house music): English first.
    val loan = huExact && en?.strong == true && glossMentions(hu.results[0].lemma, normalizeEnglish(hu.query))
    // Exact Hungarian 3; accent-folded only (car → cár) 1.5, below an exact English term; stemmer guess 1.
    val huScore = when {
        !keepHu || hu.results.isEmpty() -> 0.0
        huExact && !loan -> 3.0
        huGuessed -> 1.0
        else -> 1.5
    }
    val enScore = when {
        en == null || en.results.isEmpty() -> 0.0
        en.strong -> 3.0
        en.exact -> 2.0
        else -> 1.0
    }
    val huSection = ResultSection(Lang.HU, hu.query, if (keepHu) hu.results else emptyList())
    val enSection = ResultSection(Lang.EN, en?.term ?: "", en?.results ?: emptyList())
    val ordered = if (huScore >= enScore) listOf(huSection, enSection) else listOf(enSection, huSection)
    return ordered.filter { it.results.isNotEmpty() }
}

/**
 * Look the query up as Hungarian and as English, like Takoboto does for Latin-script input.
 * Accented letters mean Hungarian, so English is only tried for unaccented queries.
 */
suspend fun DictionarySource.searchBoth(raw: String): CombinedResponse {
    val hu = search(raw)
    val en = if (hasAccents(cleanQuery(raw))) null else searchEnglish(raw)
    val (starts, ends) = partialRows(raw)
    val sections = rankSections(hu, en)
    val shown = sections.flatMap { s -> s.results.map { it.lemmaId } }.toSet()
    return CombinedResponse(hu.query, sections, hu.tokens, rankPartial(hu.query, starts, ends, shown))
}
