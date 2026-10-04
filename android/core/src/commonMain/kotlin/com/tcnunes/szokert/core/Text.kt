package com.tcnunes.szokert.core

/**
 * JavaScript-compatible text helpers. The web app's regexes use JavaScript's \s, trim() and \b,
 * which differ from Java's, so they are spelled out explicitly here to give identical results.
 */

/** The characters JavaScript's \s and String.prototype.trim() treat as whitespace. */
internal const val JS_SPACE = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"

internal fun isJsSpace(c: Char): Boolean =
    c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r' || c == ' ' || c == ' ' ||
        c == ' ' || c in ' '..' ' || c == ' ' || c == ' ' || c == ' ' ||
        c == ' ' || c == '　' || c == '﻿'

/** JavaScript's String.prototype.trim(). */
internal fun String.jsTrim(): String = trim(::isJsSpace)

private val EDGE_PUNCTUATION = Regex("^[\\p{P}$JS_SPACE]+|[\\p{P}$JS_SPACE]+$")
private val SPACES = Regex("[$JS_SPACE]+")

/** The query as searched: NFC, trimmed, without punctuation at either end, single spaces. Matches cleanQuery() in search.ts. */
fun cleanQuery(q: String): String = SPACES.replace(EDGE_PUNCTUATION.replace(nfc(q).jsTrim(), ""), " ")

/** Words of a multi-word query (letters, optionally hyphenated). */
internal val TOKEN = Regex("\\p{L}+(?:-\\p{L}+)*")

/** Thousands separators as in JavaScript's toLocaleString('en'): 12345 → "12,345". */
internal fun groupThousands(n: Int): String {
    val digits = kotlin.math.abs(n).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (n < 0) "-$grouped" else grouped
}
