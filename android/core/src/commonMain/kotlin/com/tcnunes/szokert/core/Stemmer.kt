package com.tcnunes.szokert.core

/**
 * Suffix-stripper fallback for words the inflection tables miss, e.g. possessive + case
 * (könyveimben = könyveim + -ben) or prefixed verbs (megláttam = meg- + láttam).
 * It only proposes candidate stems; search keeps the ones that exist in the index. Matches stemmer.ts.
 */

data class Step(
    /** The piece that was removed, e.g. "ben" or "meg". */
    val piece: String,
    val part: Part,
    val prefix: Boolean = false,
)

data class Candidate(
    val stem: String,
    /** Steps from the stem outwards (inner suffix first). */
    val steps: List<Step>,
)

private class Rule(val endings: List<String>, val part: Part)

private fun rule(endings: List<String>, label: String, hint: String) = Rule(endings, Part(label, hint))

private val CASE_RULES = listOf(
    rule(listOf("ban", "ben"), "inessive", "in"),
    rule(listOf("ba", "be"), "illative", "into"),
    rule(listOf("ból", "ből"), "elative", "out of"),
    rule(listOf("on", "en", "ön", "n"), "superessive", "on"),
    rule(listOf("ra", "re"), "sublative", "onto"),
    rule(listOf("ról", "ről"), "delative", "off, about"),
    rule(listOf("nál", "nél"), "adessive", "at, by"),
    rule(listOf("hoz", "hez", "höz"), "allative", "to (towards)"),
    rule(listOf("tól", "től"), "ablative", "from"),
    rule(listOf("nak", "nek"), "dative", "to, for"),
    rule(listOf("val", "vel"), "instrumental", "with"),
    rule(listOf("vá", "vé"), "translative", "into (becoming)"),
    rule(listOf("ért"), "causal-final", "for, because of"),
    rule(listOf("ig"), "terminative", "as far as, until"),
    rule(listOf("ként"), "essive-formal", "as, in the role of"),
    rule(listOf("kor"), "temporal", "at (time)"),
    rule(listOf("ul", "ül"), "essive-modal", "as, -ly; in (a language)"),
    rule(listOf("at", "ot", "et", "öt", "t"), "accusative", "direct object"),
)

private val DERIVATION_RULES = listOf(
    rule(listOf("ság", "ség"), "-ság/-ség", "-ness, abstract noun"),
    rule(listOf("talan", "telen", "tlan", "tlen"), "-talan/-telen", "without, -less"),
    rule(listOf("beli"), "-beli", "in/of the …"),
    rule(listOf("nyi"), "-nyi", "an amount of"),
    rule(listOf("ék"), "-ék", "… and family/group"),
    rule(listOf("é"), "-é", "…'s (belonging to)"),
    rule(listOf("i"), "-i", "of, from (adjective)"),
    rule(listOf("s"), "-s", "having …, with …"),
)

private val RULES = CASE_RULES + DERIVATION_RULES

private val PREVERBS = listOf(
    "meg" to "completed action", "el" to "away; completed", "ki" to "out", "be" to "in",
    "fel" to "up", "föl" to "up", "le" to "down", "át" to "across, over", "rá" to "onto",
    "össze" to "together", "vissza" to "back", "oda" to "there", "ide" to "here", "szét" to "apart",
    "túl" to "over, beyond", "végig" to "all the way", "hozzá" to "to", "bele" to "into",
    "alá" to "under", "elő" to "forth", "fölé" to "above", "mellé" to "beside", "körül" to "around",
    "keresztül" to "through", "haza" to "home", "újra" to "again", "abba" to "stop",
    "agyon" to "to death, excessively", "tovább" to "on, further", "félre" to "aside",
    "közbe" to "in between", "utána" to "after", "neki" to "against, at",
)

private val DIGRAPHS = listOf("sz", "zs", "cs", "gy", "ly", "ny", "ty", "dz")
private const val VOWELS = "aáeéiíoóöőuúüű"
private const val MIN_STEM = 2
private const val MAX_STEPS = 3
private val QUESTION = Regex("^(.+)-e$")

/** Stems the ending may have been attached to (handles -val/-vá assimilation and á/é lengthening). */
private fun stemsFor(word: String, ending: String, rule: Rule): List<String> {
    val out = mutableListOf<String>()
    if (word.endsWith(ending)) out.add(word.dropLast(ending.length))
    // házzal = ház + -val, busszal = busz + -val, házzá = ház + -vá: the v becomes a copy of the last consonant.
    val label = rule.part.label
    if ((label == "instrumental" || label == "translative") && ending.startsWith("v")) {
        val assimilated = ending.substring(1)
        if (word.endsWith(assimilated)) {
            val r = word.dropLast(assimilated.length)
            val dig = DIGRAPHS.firstOrNull { d -> r.endsWith(d[0] + d) }
            if (dig != null) {
                out.add(r.dropLast(dig.length + 1) + dig)
            } else if (r.length >= 2 && r[r.length - 1] == r[r.length - 2] && r.last() !in VOWELS) {
                out.add(r.dropLast(1))
            }
        }
    }
    // almát → almá → alma, kefében → kefé → kefe
    for (s in out.toList()) {
        if (s.endsWith("á")) out.add(s.dropLast(1) + "a")
        if (s.endsWith("é")) out.add(s.dropLast(1) + "e")
    }
    return out.filter { codePoints(it).size >= MIN_STEM }
}

private fun stripSuffixes(word: String, depth: Int): Sequence<Candidate> = sequence {
    if (depth >= MAX_STEPS) return@sequence
    for (rule in RULES) {
        for (ending in rule.endings) {
            for (stem in stemsFor(word, ending, rule)) {
                val step = Step(ending, rule.part)
                yield(Candidate(stem, listOf(step)))
                for (inner in stripSuffixes(stem, depth + 1)) {
                    yield(Candidate(inner.stem, inner.steps + step))
                }
            }
        }
    }
}

/** Candidate analyses of a word, most plausible (fewest steps, longest stem) first. */
fun candidates(input: String): List<Candidate> {
    val word = nfc(input.lowercase())
    val seen = HashSet<String>()
    val out = mutableListOf<Candidate>()
    fun push(c: Candidate) {
        val key = c.stem + "|" + c.steps.joinToString("+") { s -> s.piece + if (s.prefix) "-" else "" }
        if (c.stem != word && seen.add(key)) out.add(c)
    }

    // Question particle: tudod-e
    val bases = mutableListOf(Candidate(word, emptyList()))
    QUESTION.find(word)?.let { m ->
        bases.add(Candidate(m.groupValues[1], listOf(Step("e", Part("question -e", "whether …?")))))
    }

    for (base in bases) {
        if (base.steps.isNotEmpty()) push(base)
        val forms = mutableListOf(base)
        for ((pv, hint) in PREVERBS) {
            if (base.stem.startsWith(pv) && base.stem.length - pv.length >= MIN_STEM) {
                val c = Candidate(
                    base.stem.substring(pv.length),
                    listOf(Step(pv, Part("$pv-", "verbal prefix: $hint"), prefix = true)) + base.steps,
                )
                push(c)
                forms.add(c)
            }
        }
        for (f in forms) {
            for (c in stripSuffixes(f.stem, f.steps.size)) {
                val prefixSteps = f.steps.filter { it.prefix }
                val suffixSteps = f.steps.filter { !it.prefix }
                push(Candidate(c.stem, prefixSteps + c.steps + suffixSteps))
            }
        }
    }

    return out.sortedWith(compareBy<Candidate> { it.steps.size }.thenByDescending { it.stem.length })
}
