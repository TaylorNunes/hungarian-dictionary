package com.tcnunes.szokert.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.ibm.icu.text.Collator
import com.ibm.icu.util.ULocale
import com.tcnunes.szokert.core.FormList
import com.tcnunes.szokert.core.Grid
import com.tcnunes.szokert.core.Lang
import com.tcnunes.szokert.core.Result
import com.tcnunes.szokert.core.buildSections
import com.tcnunes.szokert.core.findEntry
import com.tcnunes.szokert.core.search
import com.tcnunes.szokert.core.hungarianOrder
import com.tcnunes.szokert.core.searchBoth
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assume.assumeTrue
import org.tukaani.xz.XZInputStream

/**
 * The Android search must return exactly what the web search returns. scripts/golden.test.ts runs the
 * web search over scripts/fixtures/parity_queries.json against the built data; this runs the Kotlin
 * port over the same queries against the database built from the same data, and compares.
 */
class GoldenParityTest {
    private val dataDir = File(System.getProperty("szokert.data"))
    private val goldenFile = File(System.getProperty("szokert.golden"))

    @Test
    fun searchMatchesTheWebApp() {
        assumeTrue("no built data at $dataDir", File(dataDir, "manifest.json").exists())
        assumeTrue("no golden results at $goldenFile", goldenFile.exists())
        hungarianOrder = Collator.getInstance(ULocale("hu")).let { icu -> Comparator { a, b -> icu.compare(a, b) } }

        val manifest = Json.parseToJsonElement(File(dataDir, "manifest.json").readText()).jsonObject
        val golden = Json.parseToJsonElement(goldenFile.readText()).jsonObject
        assertEquals(manifest.str("version"), golden.str("version"), "golden.json is for other data: rerun the golden vitest")

        val source = SqliteDictionarySource(BundledSQLiteDriver().open(unpackedDatabase(manifest).path))
        source.use { db ->
            assertEquals(DB_SCHEMA, db.schema)
            assertEquals(manifest.str("version"), db.meta["version"])
            val mismatches = mutableListOf<String>()
            val cases = golden.getValue("cases").jsonArray
            runBlocking {
                val tagSets = db.tags()
                for (case in cases.map { it.jsonObject }) {
                    val query = case.str("query")
                    val r = db.searchBoth(query)
                    val first = r.sections.firstOrNull { it.lang == Lang.HU }?.results?.firstOrNull()
                    val actual = buildJsonObject {
                        put("query", query)
                        put("cleaned", r.query)
                        putJsonArray("tokens") { r.tokens.forEach { add(it) } }
                        putJsonArray("sections") {
                            for (s in r.sections) add(buildJsonObject {
                                put("lang", s.lang.name.lowercase())
                                put("term", s.term)
                                putJsonArray("results") { s.results.forEach { add(resultJson(it)) } }
                            })
                        }
                        putJsonArray("partial") {
                            r.partial.forEach { p -> add(buildJsonArray { add(p.lemmaId); add(p.start); add(p.end) }) }
                        }
                        val table = first?.lemma?.t
                        if (first != null && table != null) {
                            put("tables", buildJsonObject {
                                put("lemmaId", first.lemmaId)
                                putJsonArray("sections") { buildSections(table, tagSets).forEach { add(sectionJson(it)) } }
                            })
                        } else {
                            put("tables", JsonNull)
                        }
                    }
                    if (actual != case) mismatches.add(describe(case, actual))
                }
            }
            if (mismatches.isNotEmpty()) {
                fail("${mismatches.size} of ${cases.size} queries differ from the web app:\n" + mismatches.take(15).joinToString("\n\n"))
            }
            println("parity: all ${cases.size} queries match the web app")
        }
    }

    /** Word pages find their entry by id, or by word and part of speech when the id is stale. */
    @Test
    fun findEntryFallsBackWhenIdsShift() {
        assumeTrue("no built data at $dataDir", File(dataDir, "manifest.json").exists())
        val manifest = Json.parseToJsonElement(File(dataDir, "manifest.json").readText()).jsonObject
        SqliteDictionarySource(BundledSQLiteDriver().open(unpackedDatabase(manifest).path)).use { db ->
            runBlocking {
                val haz = db.search("ház").results.first()
                assertEquals("ház", haz.lemma.w)
                assertEquals(haz.lemmaId, db.findEntry("ház", "noun", haz.lemmaId)?.first)
                assertEquals(haz.lemmaId, db.findEntry("ház", "noun", haz.lemmaId + 1)?.first, "stale id")
                assertEquals(haz.lemmaId, db.findEntry("ház", "noun")?.first, "no id")
                assertEquals("ház", db.findEntry("ház", "verb")?.second?.w, "other part of speech falls back to the word")
                assertNull(db.findEntry("qwrtzx", "noun"))
            }
        }
    }

    private fun resultJson(r: Result) = buildJsonObject {
        put("lemmaId", r.lemmaId)
        put("word", r.lemma.w)
        put("exact", r.exact)
        put("guessed", r.guessed)
        put("sense", r.sense)
        putJsonArray("analyses") {
            for (a in r.analyses) add(buildJsonObject {
                put("form", a.form)
                putJsonArray("parts") { a.parts.forEach { p -> add(buildJsonArray { add(p.label); add(p.hint) }) } }
            })
        }
    }

    private fun sectionJson(s: com.tcnunes.szokert.core.Section): JsonElement = when (s) {
        is Grid -> buildJsonObject {
            put("kind", "grid")
            put("title", s.title)
            putJsonArray("cols") { s.cols.forEach { add(it) } }
            putJsonArray("rows") {
                for (row in s.rows) add(buildJsonObject {
                    put("head", row.head)
                    put("sub", row.sub)
                    putJsonArray("cells") { row.cells.forEach { c -> add(buildJsonArray { c.forEach { add(it) } }) } }
                })
            }
        }
        is FormList -> buildJsonObject {
            put("kind", "list")
            put("title", s.title)
            putJsonArray("items") {
                for (item in s.items) add(buildJsonObject {
                    put("head", item.head)
                    putJsonArray("forms") { item.forms.forEach { add(it) } }
                })
            }
        }
    }

    /** Which parts differ, with both versions, trimmed to stay readable. */
    private fun describe(expected: JsonObject, actual: JsonObject): String {
        val keys = (expected.keys + actual.keys).filter { expected[it] != actual[it] }
        return "query \"${expected.str("query")}\":\n" + keys.joinToString("\n") { k ->
            "  $k\n    web:     ${expected[k].toString().take(600)}\n    android: ${actual[k].toString().take(600)}"
        }
    }

    /** The database, unpacked once per version into the build directory. */
    private fun unpackedDatabase(manifest: JsonObject): File {
        val info = manifest.getValue("databases").jsonArray.first().jsonObject
        val packed = File(dataDir, info.str("path"))
        val out = File("build/tmp/szokert-${info.str("rawSha256").take(16)}.db")
        if (out.exists()) return out
        out.parentFile.mkdirs()
        val tmp = File(out.path + ".tmp")
        val sha = MessageDigest.getInstance("SHA-256")
        XZInputStream(packed.inputStream().buffered()).use { input ->
            tmp.outputStream().use { output ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    sha.update(buffer, 0, n)
                    output.write(buffer, 0, n)
                }
            }
        }
        assertEquals(info.str("rawSha256"), sha.digest().joinToString("") { "%02x".format(it) }, "unpacked database checksum")
        tmp.renameTo(out)
        return out
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
