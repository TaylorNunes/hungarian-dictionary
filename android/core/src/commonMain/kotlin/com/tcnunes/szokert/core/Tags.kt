package com.tcnunes.szokert.core

import kotlinx.serialization.Serializable

/**
 * Turns Kaikki-style tag sets into readable word breakdowns and inflection tables. Verb tags are
 * rebuilt by scripts/build_data.py into the same vocabulary as Kaikki's form-of entries: mood,
 * tense, definiteness, person and number. Matches tags.ts.
 */

class CaseInfo(val tag: String, val label: String, val hint: String)

val CASES = listOf(
    CaseInfo("nominative", "nominative", "subject"),
    CaseInfo("accusative", "accusative", "direct object"),
    CaseInfo("dative", "dative", "to, for"),
    CaseInfo("instrumental", "instrumental", "with"),
    CaseInfo("causal-final", "causal-final", "for, because of"),
    CaseInfo("translative", "translative", "into (becoming)"),
    CaseInfo("terminative", "terminative", "as far as, until"),
    CaseInfo("essive-formal", "essive-formal", "as, in the role of"),
    CaseInfo("essive-modal", "essive-modal", "as, -ly"),
    CaseInfo("inessive", "inessive", "in"),
    CaseInfo("superessive", "superessive", "on"),
    CaseInfo("adessive", "adessive", "at, by"),
    CaseInfo("illative", "illative", "into"),
    CaseInfo("sublative", "sublative", "onto"),
    CaseInfo("allative", "allative", "to (towards)"),
    CaseInfo("elative", "elative", "out of"),
    CaseInfo("delative", "delative", "off, about"),
    CaseInfo("ablative", "ablative", "from"),
    CaseInfo("temporal", "temporal", "at (time)"),
    CaseInfo("locative", "locative", "in (place names)"),
)
private val CASE_INFO = CASES.associate { it.tag to Part(it.label, it.hint) }

class PersonInfo(val key: String, val pronoun: String, val english: String)

val PERSONS = listOf(
    PersonInfo("first-person singular", "én", "I"),
    PersonInfo("second-person singular", "te", "you"),
    PersonInfo("third-person singular", "ő / Ön", "he, she, it / formal you"),
    PersonInfo("first-person plural", "mi", "we"),
    PersonInfo("second-person plural", "ti", "you all"),
    PersonInfo("third-person plural", "ők / Önök", "they / formal you all"),
)
private val POSSESSORS = listOf("my", "your", "his/her/its", "our", "your (pl.)", "their")

val MOODS = linkedMapOf(
    "indicative present" to Part("present"),
    "indicative past" to Part("past"),
    "indicative future" to Part("future"),
    "conditional present" to Part("conditional", "would …"),
    "conditional past" to Part("conditional past", "would have …"),
    "subjunctive present" to Part("subjunctive", "imperative; let …, that … should"),
)

private val NONFINITE = mapOf(
    "noun-from-verb" to Part("verbal noun", "-ás/-és, the act of …"),
    "causative" to Part("causative", "make/let someone …"),
    "adverbial participle" to Part("adverbial participle", "-va/-ve, …ing, having …"),
    "present participle" to Part("present participle", "-ó/-ő, …ing"),
    "past participle" to Part("past participle", "-t/-tt, …ed"),
    "future participle" to Part("future participle", "-andó/-endő, to be …ed"),
    "privative participle" to Part("privative", "un…able"),
)

private val GRAMMAR_TAGS = CASES.map { it.tag }.toSet() + setOf(
    "singular", "plural", "first-person", "second-person", "third-person", "second-person-semantically",
    "indicative", "conditional", "subjunctive", "present", "past", "future", "definite", "indefinite",
    "possessive", "possessed-single", "possessed-many", "potential", "infinitive", "personal",
    "object-second-person", "participle", "adverbial", "noun-from-verb", "causative", "privative",
    "formal", "informal", "canonical",
)

private fun List<String>.has(vararg want: String) = want.all { it in this }

private fun personIndex(tags: List<String>): Int {
    val p = when {
        tags.has("first-person") -> 0
        tags.has("second-person") -> 1
        tags.has("third-person") -> 2
        else -> return -1
    }
    return when {
        tags.has("plural") -> p + 3
        tags.has("singular") -> p
        else -> -1
    }
}

private fun humanize(tag: String) = tag.replace('-', ' ')

private fun moodKey(tags: List<String>): String? {
    val mood = listOf("indicative", "conditional", "subjunctive").firstOrNull { it in tags }
    val tense = listOf("present", "past", "future").firstOrNull { it in tags }
    return if (mood != null && tense != null) "$mood $tense" else null
}

private fun nonfiniteKey(tags: List<String>): String? {
    if (tags.has("noun-from-verb")) return "noun-from-verb"
    if (tags.has("causative")) return "causative"
    if (tags.has("adverbial", "participle")) return "adverbial participle"
    if (tags.has("privative")) return "privative participle"
    for (t in listOf("present", "past", "future")) if (tags.has(t, "participle")) return "$t participle"
    return null
}

/** Describe one tag set as a list of short labels with hints. */
fun describe(tags: List<String>): List<Part> {
    val parts = mutableListOf<Part>()
    val person = personIndex(tags)

    if (tags.has("potential")) parts.add(Part("can", "potential -hat/-het"))

    val mood = moodKey(tags)
    val nonfinite = nonfiniteKey(tags)
    if (mood != null) {
        parts.add(MOODS[mood] ?: Part(mood))
        if (tags.has("object-second-person")) {
            parts.add(Part("I → you", "-lak/-lek: I do it to you"))
        } else {
            if (person >= 0) parts.add(Part(PERSONS[person].english, PERSONS[person].pronoun))
            if (tags.has("definite")) parts.add(Part("definite", "with a specific object"))
            else if (tags.has("indefinite")) parts.add(Part("indefinite", "no specific object"))
        }
    } else if (tags.has("infinitive")) {
        if (person >= 0) {
            val whom = listOf("me", "you", "him/her", "us", "you all", "them")[person]
            parts.add(Part("infinitive (for $whom)", "personal infinitive"))
        } else {
            parts.add(Part("infinitive", "to …"))
        }
    } else if (nonfinite != null) {
        parts.add(NONFINITE.getValue(nonfinite))
    } else {
        if (tags.has("possessive") && person >= 0) {
            val many = tags.has("possessed-many")
            parts.add(Part(POSSESSORS[person] + if (many) " (several)" else "", if (many) "possessive, several things" else "possessive"))
        } else if (tags.has("plural")) {
            parts.add(Part("plural"))
        }
        for (t in tags) {
            val c = CASE_INFO[t]
            if (c != null && !(t == "nominative" && parts.isNotEmpty())) parts.add(c)
        }
    }

    for (t in tags) {
        if (t !in GRAMMAR_TAGS) parts.add(Part(humanize(t)))
    }
    return parts
}

/** True when a tag set only restates the dictionary form (nominative singular, plain infinitive…). */
fun isBaseForm(tags: List<String>): Boolean {
    if (tags.isEmpty()) return true
    return tags.filter { it in GRAMMAR_TAGS }.all { it == "nominative" || it == "singular" || it == "canonical" }
}

// ----------------------------------------------------------------------------- tables

@Serializable
data class GridRow(val head: String, val sub: String? = null, val cells: List<List<String>>)

sealed interface Section {
    val title: String
}

data class Grid(override val title: String, val cols: List<String>, val rows: List<GridRow>) : Section

data class ListItem(val head: String, val forms: List<String>)

data class FormList(override val title: String, val items: List<ListItem>) : Section

private class MutableGrid(val title: String, var cols: List<String>, heads: List<Pair<String, String?>>) {
    val rows = heads.map { (head, sub) -> MutableRow(head, sub, cols.map { mutableListOf<String>() }) }.toMutableList()
    fun freeze() = Grid(title, cols, rows.map { GridRow(it.head, it.sub, it.cells.map { c -> c.toList() }) })
}

private class MutableRow(val head: String, val sub: String?, var cells: List<MutableList<String>>)

private fun MutableList<String>.addUnique(form: String) {
    if (form !in this) add(form)
}

/** Group a lemma's inflection table into declension/conjugation grids plus a list of other forms. */
fun buildSections(table: List<TableEntry>, tagSets: List<List<String>>): List<Section> {
    val personHeads = PERSONS.map { it.pronoun to it.english as String? }
    val declension = MutableGrid("Declension", listOf("singular", "plural"), CASES.map { it.tag to it.hint })
    val possessive = MutableGrid("Possessive", listOf("one thing", "several things"), PERSONS.mapIndexed { i, p -> POSSESSORS[i] to p.pronoun })
    val verbGrids = LinkedHashMap<String, MutableGrid>()
    val infinitive = MutableGrid("Personal infinitive", listOf("form"), personHeads)
    val other = mutableListOf<Pair<String, MutableList<String>>>()
    var usedDecl = false
    var usedPoss = false
    var usedInf = false

    for ((form, idx) in table) {
        val tags = tagSets.getOrNull(idx) ?: emptyList()
        val person = personIndex(tags)
        val mood = moodKey(tags)
        val potential = tags.has("potential")
        val caseIdx = CASES.indexOfFirst { it.tag in tags }

        if (mood != null) {
            val key = (if (potential) "potential " else "") + mood
            val g = verbGrids.getOrPut(key) {
                val m = MOODS[mood]?.label ?: mood
                val title = (if (potential) "Potential (can) · $m" else m).replaceFirstChar { it.uppercase() }
                MutableGrid(title, listOf("indefinite", "definite"), personHeads)
            }
            if (tags.has("object-second-person")) {
                val lak = g.rows.firstOrNull { it.head == "én → téged" }
                    ?: MutableRow("én → téged", "I … you", listOf(mutableListOf(), mutableListOf())).also { g.rows.add(it) }
                lak.cells[1].addUnique(form)
            } else if (person >= 0) {
                g.rows[person].cells[if (tags.has("definite")) 1 else 0].addUnique(form)
            }
        } else if (tags.has("infinitive") && person >= 0 && !potential) {
            infinitive.rows[person].cells[0].addUnique(form)
            usedInf = true
        } else if (tags.has("possessive") && person >= 0 && !tags.any { CASE_INFO.containsKey(it) && it != "nominative" }) {
            possessive.rows[person].cells[if (tags.has("possessed-many")) 1 else 0].addUnique(form)
            usedPoss = true
        } else if (caseIdx >= 0 && !tags.has("possessive") && (tags.has("singular") || tags.has("plural"))) {
            declension.rows[caseIdx].cells[if (tags.has("plural")) 1 else 0].addUnique(form)
            usedDecl = true
        } else if (tags.isNotEmpty()) {
            val head = describe(tags).joinToString(" · ") { it.label }
            val item = other.firstOrNull { it.first == head }
            if (item != null) item.second.addUnique(form) else other.add(head to mutableListOf(form))
        }
    }

    val sections = mutableListOf<Section>()
    val order = listOf("indicative present", "indicative past", "indicative future", "conditional present", "conditional past", "subjunctive present")
    val verbKeys = verbGrids.keys.sortedWith(
        compareBy<String> { if (it.startsWith("potential")) 1 else 0 }.thenBy { order.indexOf(it.replace("potential ", "")) },
    )
    for (k in verbKeys) {
        val g = verbGrids.getValue(k)
        // Intransitive verbs have no definite column.
        if (g.rows.all { it.cells[1].isEmpty() }) {
            g.cols = listOf("form")
            g.rows.forEach { it.cells = listOf(it.cells[0]) }
        }
        sections.add(g.freeze())
    }
    if (usedInf) sections.add(infinitive.freeze())
    if (usedDecl) {
        declension.rows.retainAll { r -> r.cells.any { it.isNotEmpty() } }
        sections.add(declension.freeze())
    }
    if (usedPoss) sections.add(possessive.freeze())
    if (other.isNotEmpty()) sections.add(FormList("Other forms", other.map { (head, forms) -> ListItem(head, forms.toList()) }))
    return sections
}
