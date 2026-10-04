package com.tcnunes.szokert.core

import com.ibm.icu.text.Collator
import com.ibm.icu.util.ULocale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The cases in scripts/fixtures/core_cases.json, generated from the TypeScript functions. */
class CoreCasesTest {
    private val cases = Fixtures.json("core_cases.json").jsonObject

    @BeforeTest
    fun useIcu() {
        val icu = Collator.getInstance(ULocale("hu"))
        hungarianOrder = Comparator { a, b -> icu.compare(a, b) }
    }

    private fun group(name: String) = cases.getValue(name).jsonArray.map { it.jsonObject }

    private val JsonElement.str: String get() = jsonPrimitive.content
    private val JsonElement.strOrNull: String? get() = if (this is JsonNull) null else jsonPrimitive.content

    private fun partsJson(parts: List<Part>) = parts.map { listOf(it.label, it.hint) }

    private fun expectedParts(e: JsonElement) = e.jsonArray.map { p -> p.jsonArray.let { listOf(it[0].str, it[1].strOrNull) } }

    @Test
    fun stemmer() {
        for (c in group("stemmer")) {
            val input = c.getValue("input").str
            val expected = c.getValue("candidates").jsonArray.map { cand ->
                val o = cand.jsonObject
                o.getValue("stem").str to o.getValue("steps").jsonArray.map { s ->
                    val a = s.jsonArray
                    listOf(a[0].str, a[1].str, a[2].strOrNull, a[3].jsonPrimitive.boolean.toString())
                }
            }
            val actual = candidates(input).take(80).map { cand ->
                cand.stem to cand.steps.map { listOf(it.piece, it.part.label, it.part.hint, it.prefix.toString()) }
            }
            assertEquals(expected, actual, input)
        }
    }

    @Test
    fun english() {
        for (c in group("deinflect")) {
            assertEquals(c.getValue("output").jsonArray.map { it.str }, deinflect(c.getValue("input").str), c.toString())
        }
        for (c in group("normalizeEnglish")) {
            assertEquals(c.getValue("output").str, normalizeEnglish(c.getValue("input").str), c.toString())
        }
    }

    @Test
    fun queries() {
        for (c in group("cleanQuery")) {
            assertEquals(c.getValue("output").str, cleanQuery(c.getValue("input").str), c.toString())
        }
        for (c in group("partialKey")) {
            assertEquals(c.getValue("output").strOrNull, partialKey(c.getValue("input").str), c.toString())
        }
    }

    @Test
    fun frequency() {
        for (c in group("frequency")) {
            val rank = c.getValue("rank").jsonPrimitive.int
            assertEquals(c.getValue("bonus").jsonPrimitive.double, frequencyBonus(rank), "bonus $rank")
            val expected = (c.getValue("commonness") as? JsonObject)?.let { o ->
                Commonness(o.getValue("label").str, o.getValue("tag").str, o.getValue("title").str)
            }
            assertEquals(expected, commonness(rank), "commonness $rank")
        }
    }

    @Test
    fun describeTagSets() {
        for (c in group("describe")) {
            val tags = c.getValue("tags").str.let { if (it.isEmpty()) emptyList() else it.split(" ") }
            assertEquals(expectedParts(c.getValue("parts")), partsJson(describe(tags)), tags.toString())
            assertEquals(c.getValue("base").jsonPrimitive.boolean, isBaseForm(tags), tags.toString())
        }
    }

    @Test
    fun glossaryText() {
        for (c in group("emphasisRuns")) {
            val expected = c.getValue("output").jsonArray.map { r ->
                Run(r.jsonObject.getValue("text").str, r.jsonObject.getValue("em").jsonPrimitive.boolean)
            }
            assertEquals(expected, emphasisRuns(c.getValue("input").str), c.toString())
        }
        for (c in group("labelName")) {
            assertEquals(c.getValue("output").str, labelName(c.getValue("input").str))
        }
    }

    @Test
    fun partialRanking() {
        fun rows(e: JsonElement) = e.jsonArray.map { r ->
            val a = r.jsonArray
            HeadRow(a[0].str, a[1].jsonPrimitive.int, a[2].str, a[3].jsonPrimitive.int, a[4].str)
        }
        for (c in group("rankPartial")) {
            val expected = c.getValue("output").jsonArray.map { m ->
                val a = m.jsonArray
                Triple(a[0].jsonPrimitive.int, a[1].jsonPrimitive.boolean, a[2].jsonPrimitive.boolean)
            }
            val exclude = c.getValue("exclude").jsonArray.map { it.jsonPrimitive.int }.toSet()
            val actual = rankPartial(c.getValue("query").str, rows(c.getValue("starts")), rows(c.getValue("ends")), exclude)
                .map { Triple(it.lemmaId, it.start, it.end) }
            assertEquals(expected, actual, c.getValue("query").str)
        }
    }

    @Test
    fun ankiExport() {
        val o = cases.getValue("ankiTsv").jsonObject
        val saved = o.getValue("saved").jsonArray.map { DictionaryJson.decodeFromJsonElement(SavedWord.serializer(), it) }
        assertEquals(o.getValue("output").str, ankiTsv(saved))
    }
}
