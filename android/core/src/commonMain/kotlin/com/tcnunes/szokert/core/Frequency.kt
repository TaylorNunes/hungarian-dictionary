package com.tcnunes.szokert.core

/**
 * Word frequency ranks come from subtitle counts (FrequencyWords, OpenSubtitles 2018), summed over
 * each word's forms by scripts/build_data.py. Rank 1 is the most common word. Matches frequency.ts.
 */

data class Commonness(
    val label: String,
    /** Anki tag, e.g. "top1000". */
    val tag: String,
    val title: String,
)

private val TIERS = listOf(1000 to "very common", 5000 to "common")

fun commonness(rank: Int?): Commonness? {
    if (rank == null || rank == 0) return null
    for ((limit, label) in TIERS) {
        if (rank <= limit) {
            return Commonness(label, "top$limit", "#${groupThousands(rank)} most frequent word in Hungarian film and TV subtitles")
        }
    }
    return null
}

/** Ranking bonus for search results: about 12 for the commonest words, 0 beyond rank 10,000. */
fun frequencyBonus(rank: Int?): Double =
    if (rank == null || rank == 0) 0.0 else maxOf(0.0, 12 - 3 * log10Exact(rank.toDouble()))

/** log10 with the same result as JavaScript's Math.log10 (fdlibm), so scores tie identically. */
internal expect fun log10Exact(x: Double): Double
