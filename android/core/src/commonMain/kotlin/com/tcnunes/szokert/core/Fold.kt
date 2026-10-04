package com.tcnunes.szokert.core

/** Lowercase and strip accents (á→a, ő→o, ü→u). Must match fold() in src/lib/fold.ts and scripts/build_data.py. */
fun fold(s: String): String = nfc(stripMarks(nfd(s.lowercase())))

/** Filename-safe version of a folded form. Must match shardChars() in src/lib/fold.ts. */
fun shardChars(folded: String): String =
    codePoints(folded).joinToString("") { c -> if (c.length == 1 && (c[0] in 'a'..'z' || c[0] in '0'..'9')) c else "_" }

/** True when the text contains any accented letter. */
fun hasAccents(s: String): Boolean = fold(s) != s.lowercase()

/** The string split into code points (JavaScript's [...s]), so surrogate pairs stay whole. */
fun codePoints(s: String): List<String> {
    val out = ArrayList<String>(s.length)
    var i = 0
    while (i < s.length) {
        val n = if (s[i].isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) 2 else 1
        out.add(s.substring(i, i + n))
        i += n
    }
    return out
}

private val MARKS = Regex("\\p{Mn}+")

private fun stripMarks(s: String): String = MARKS.replace(s, "")

/** Unicode NFD / NFC normalisation (platform-specific). */
internal expect fun nfd(s: String): String
internal expect fun nfc(s: String): String
