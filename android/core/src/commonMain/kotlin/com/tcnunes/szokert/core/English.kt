package com.tcnunes.szokert.core

/**
 * English query handling for English → Hungarian lookup. The index (built by build_english_index in
 * scripts/build_data.py) holds gloss terms like "house" or "see", so queries are normalised the same
 * way and inflected words reduced to them. Matches english.ts.
 */

private val ENGLISH_SPACES = Regex("[$JS_SPACE]+")
private val EDGE_NON_ALNUM = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")
private val LEADING_WORD = Regex("^(?:to|a|an|the) ")

/** Lowercase, trim, collapse spaces and drop a leading "to"/article: "To See" → "see". */
fun normalizeEnglish(q: String): String {
    val spaced = ENGLISH_SPACES.replace(nfc(q).lowercase(), " ").jsTrim()
    return LEADING_WORD.replaceFirst(EDGE_NON_ALNUM.replace(spaced, ""), "")
}

private val IRREGULAR = mapOf(
    "went" to "go", "gone" to "go", "goes" to "go", "saw" to "see", "seen" to "see", "ate" to "eat", "eaten" to "eat",
    "was" to "be", "were" to "be", "been" to "be", "am" to "be", "are" to "be", "did" to "do", "done" to "do", "does" to "do",
    "had" to "have", "has" to "have", "made" to "make", "took" to "take", "taken" to "take", "came" to "come",
    "gave" to "give", "given" to "give", "knew" to "know", "known" to "know", "thought" to "think", "bought" to "buy",
    "brought" to "bring", "told" to "tell", "said" to "say", "found" to "find", "got" to "get", "gotten" to "get",
    "left" to "leave", "felt" to "feel", "kept" to "keep", "ran" to "run", "wrote" to "write", "written" to "write",
    "spoke" to "speak", "spoken" to "speak", "drank" to "drink", "drunk" to "drink", "sang" to "sing", "sung" to "sing",
    "swam" to "swim", "slept" to "sleep", "sat" to "sit", "stood" to "stand", "understood" to "understand",
    "began" to "begin", "begun" to "begin", "broke" to "break", "broken" to "break", "chose" to "choose",
    "chosen" to "choose", "drove" to "drive", "driven" to "drive", "fell" to "fall", "fallen" to "fall", "flew" to "fly",
    "flown" to "fly", "forgot" to "forget", "forgotten" to "forget", "held" to "hold", "met" to "meet", "paid" to "pay",
    "sold" to "sell", "sent" to "send", "spent" to "spend", "taught" to "teach", "caught" to "catch", "fought" to "fight",
    "won" to "win", "lost" to "lose", "heard" to "hear", "meant" to "mean", "built" to "build", "lay" to "lie", "lain" to "lie",
    "rode" to "ride", "ridden" to "ride", "wore" to "wear", "worn" to "wear", "grew" to "grow", "grown" to "grow",
    "threw" to "throw", "thrown" to "throw", "children" to "child", "men" to "man", "women" to "woman", "feet" to "foot",
    "teeth" to "tooth", "mice" to "mouse", "geese" to "goose", "people" to "person", "better" to "good", "best" to "good",
    "worse" to "bad", "worst" to "bad",
)

private fun isVowel(c: Char) = c in "aeiou"

/**
 * Possible base forms of an inflected English term, most likely first (excluding the term itself).
 * Only the last word changes: "running shoes" → "running shoe".
 */
fun deinflect(term: String): List<String> {
    val words = term.split(" ").toMutableList()
    val last = words.removeAt(words.lastIndex)
    val head = if (words.isNotEmpty()) words.joinToString(" ") + " " else ""
    val out = mutableListOf<String>()
    fun add(w: String) {
        if (w.length >= 2 && w != last && (head + w) !in out) out.add(head + w)
    }
    fun undouble(stem: String) {
        // running → runn → run, stopped → stopp → stop
        if (stem.length >= 3 && stem[stem.length - 1] == stem[stem.length - 2] && !isVowel(stem.last())) add(stem.dropLast(1))
    }

    IRREGULAR[last]?.let(::add)
    if (last.endsWith("'s")) add(last.dropLast(2))
    if (last.endsWith("ies")) add(last.dropLast(3) + "y")
    if (last.endsWith("ves")) {
        add(last.dropLast(3) + "f")
        add(last.dropLast(3) + "fe")
    }
    if (last.endsWith("s") && !last.endsWith("ss")) add(last.dropLast(1))
    if (last.endsWith("es")) add(last.dropLast(2))
    if (last.endsWith("ied")) add(last.dropLast(3) + "y")
    if (last.endsWith("ed")) {
        val stem = last.dropLast(2)
        add(stem + "e")
        add(stem)
        undouble(stem)
    }
    if (last.endsWith("ing")) {
        val stem = last.dropLast(3)
        add(stem)
        add(stem + "e")
        undouble(stem)
    }
    if (last.endsWith("ier")) add(last.dropLast(3) + "y")
    if (last.endsWith("iest")) add(last.dropLast(4) + "y")
    if (last.endsWith("er") || last.endsWith("est")) {
        val stem = last.dropLast(if (last.endsWith("er")) 2 else 3)
        add(stem)
        add(stem + "e")
        undouble(stem)
    }
    if (last.endsWith("ly")) add(last.dropLast(2))
    return out
}
