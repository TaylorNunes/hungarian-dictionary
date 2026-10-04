package com.tcnunes.szokert.core

/** Headwords that start or end with the query, listed below the results. Matches search.ts. */

data class PartialMatch(
    val word: String,
    val lemmaId: Int,
    val pos: String,
    /** Frequency rank, 0 when unranked. */
    val rank: Int,
    val gloss: String,
    /** Where the query matched, for highlighting. */
    val start: Boolean,
    val end: Boolean,
)

private const val MAX_PARTIAL = 200
private const val MIN_PARTIAL_LETTERS = 2
private val LETTER = Regex("\\p{L}")

/** The folded query to match headwords against, or null when it is too short for partial matches. */
fun partialKey(raw: String): String? {
    val folded = fold(cleanQuery(raw))
    return if (LETTER.findAll(folded).count() >= MIN_PARTIAL_LETTERS) folded else null
}

/** Headwords starting with the query and headwords ending with it; empty for short queries or on a read error. */
internal suspend fun DictionarySource.partialRows(raw: String): Pair<List<HeadRow>, List<HeadRow>> {
    val folded = partialKey(raw) ?: return emptyList<HeadRow>() to emptyList()
    // An extra: never let it hide the main results.
    return try {
        headsStarting(folded) to headsEnding(codePoints(folded).reversed().joinToString(""))
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        emptyList<HeadRow>() to emptyList()
    }
}

/** How partial matches with the same rank and length are ordered: Hungarian alphabetical order. */
var hungarianOrder: Comparator<String> = defaultHungarianOrder()

/**
 * Merge starts-with and ends-with rows into one list, weighting both sides the same: common words
 * first by frequency rank, then unranked ones by length and alphabet. Drops the query's own
 * headword and lemmas listed in [exclude]. With accents typed, the accents must match as well.
 */
fun rankPartial(query: String, starts: List<HeadRow>, ends: List<HeadRow>, exclude: Set<Int> = emptySet()): List<PartialMatch> {
    val lower = query.lowercase()
    val folded = fold(query)
    val accents = hasAccents(query)
    val byId = LinkedHashMap<Int, PartialMatch>()
    fun add(rows: List<HeadRow>, start: Boolean) {
        for (r in rows) {
            if (r.lemmaId in exclude || fold(r.word) == folded) continue
            val w = r.word.lowercase()
            if (accents && !(if (start) w.startsWith(lower) else w.endsWith(lower))) continue
            val m = byId[r.lemmaId] ?: PartialMatch(r.word, r.lemmaId, r.pos, r.rank, r.gloss, start = false, end = false)
            byId[r.lemmaId] = if (start) m.copy(start = true) else m.copy(end = true)
        }
    }
    add(starts, start = true)
    add(ends, start = false)
    val order = hungarianOrder
    return byId.values
        .sortedWith(
            compareBy<PartialMatch> { if (it.rank == 0) Int.MAX_VALUE else it.rank }
                .thenBy { it.word.length }
                .then { a, b -> order.compare(a.word, b.word) }
                .thenBy { it.lemmaId },
        )
        .take(MAX_PARTIAL)
}

/** Hungarian collation (platform-specific); JavaScript's localeCompare(…, 'hu'). */
internal expect fun defaultHungarianOrder(): Comparator<String>
