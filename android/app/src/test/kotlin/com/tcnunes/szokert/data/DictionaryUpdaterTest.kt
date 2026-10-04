package com.tcnunes.szokert.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

class DictionaryUpdaterTest {
    private lateinit var server: HttpServer
    private lateinit var dir: File
    private val files = mutableMapOf<String, ByteArray>()
    private val requests = mutableListOf<String>()
    /** Requests for the database to answer 404 (a deploy replaced it). */
    private var vanishTimes = 0
    /** Cut the database response off after this many bytes, once. */
    private var cutAfter: Int? = null

    @BeforeTest
    fun start() {
        dir = Files.createTempDirectory("szokert").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/data/") { ex -> serve(ex) }
        server.start()
    }

    @AfterTest
    fun stop() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private val url get() = "http://127.0.0.1:${server.address.port}/data/"

    private fun serve(ex: HttpExchange) {
        val path = ex.requestURI.path.removePrefix("/data/")
        val range = ex.requestHeaders.getFirst("Range")
        requests.add(path + (range?.let { " $it" } ?: ""))
        val body = files[path]
        if (body == null || (path.endsWith(".xz") && vanishTimes > 0).also { if (it) vanishTimes-- }) {
            ex.sendResponseHeaders(404, -1)
            ex.close()
            return
        }
        val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
        val slice = body.copyOfRange(from, body.size)
        if (from > 0) ex.responseHeaders.add("Content-Range", "bytes $from-${body.size - 1}/${body.size}")
        ex.sendResponseHeaders(if (from > 0) 206 else 200, slice.size.toLong())
        val cut = cutAfter
        if (cut != null && path.endsWith(".xz")) {
            cutAfter = null
            ex.responseBody.write(slice, 0, cut)
            ex.responseBody.flush()
            ex.close() // the connection drops mid-download
            return
        }
        ex.responseBody.use { it.write(slice) }
    }

    /** Publish a version with a random "database" of [size] bytes; returns its raw bytes. */
    private fun publish(version: String, size: Int = 400_000, schema: Int = 1, corrupt: Boolean = false): ByteArray {
        val raw = Random(version.hashCode()).nextBytes(size)
        val packed = ByteArrayOutputStream().also { out -> XZOutputStream(out, LZMA2Options(0)).use { it.write(raw) } }.toByteArray()
        val path = "$version/szokert.db.xz"
        files[path] = if (corrupt) packed.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() } else packed
        files["manifest.json"] = """{"version":"$version","built":"now","databases":[{"schema":$schema,"path":"$path",
            "bytes":${packed.size},"sha256":"${sha(packed)}","rawBytes":${raw.size},"rawSha256":"${sha(raw)}"}]}""".toByteArray()
        return raw
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun updater(free: Long = Long.MAX_VALUE, valid: Boolean = true) =
        DictionaryUpdater(url, DictionaryStore(dir), schema = 1, validate = { _, _ -> valid }, freeSpace = { free })

    @Test
    fun installsAndThenIsUpToDate() = runTest {
        val raw = publish("v1")
        val progress = mutableListOf<Progress>()
        assertEquals(UpdateOutcome.Installed("v1"), updater().update { progress.add(it) })
        val installed = DictionaryStore(dir).current()!!
        assertEquals("v1", installed.version)
        assertTrue(raw.contentEquals(installed.file.readBytes()))
        assertTrue(progress.any { it is Progress.Downloading } && progress.contains(Progress.Verifying))
        assertEquals(listOf("dict-v1.db", "current"), dir.list()!!.sortedDescending(), "no leftovers")
        assertEquals(UpdateOutcome.UpToDate("v1"), updater().update())
    }

    @Test
    fun resumesAnInterruptedDownload() = runTest {
        val raw = publish("v1")
        cutAfter = 10_000
        val first = assertFailsWith<UpdateException> { updater().update() }
        assertTrue("continue where it left off" in first.message!!, first.message)
        assertEquals(null, DictionaryStore(dir).current())
        assertEquals(UpdateOutcome.Installed("v1"), updater().update())
        assertTrue(requests.any { it == "v1/szokert.db.xz bytes=10000-" }, requests.toString())
        assertTrue(raw.contentEquals(DictionaryStore(dir).current()!!.file.readBytes()))
    }

    @Test
    fun removesLeftoversOfTheCheck() = runTest {
        publish("v1")
        // SQLite may leave a journal next to the unpacked file it was asked to check.
        val u = DictionaryUpdater(url, DictionaryStore(dir), schema = 1,
            validate = { f, _ -> File(f.path + "-journal").writeText(""); true }, freeSpace = { Long.MAX_VALUE })
        u.update()
        assertEquals(listOf("dict-v1.db", "current"), dir.list()!!.sortedDescending())
    }

    @Test
    fun replacesAnOlderVersionAndRemovesIt() = runTest {
        publish("v1")
        updater().update()
        val raw2 = publish("v2")
        assertEquals(UpdateOutcome.Installed("v2"), updater().update())
        assertTrue(raw2.contentEquals(DictionaryStore(dir).current()!!.file.readBytes()))
        assertFalse(File(dir, "dict-v1.db").exists())
    }

    @Test
    fun startsAgainWhenTheVersionVanishesMidDownload() = runTest {
        publish("v2")
        vanishTimes = 1
        assertEquals(UpdateOutcome.Installed("v2"), updater().update())
        assertEquals(2, requests.count { it == "manifest.json" })
    }

    @Test
    fun rejectsADamagedDownloadAndKeepsTheOldDictionary() = runTest {
        publish("v1")
        updater().update()
        publish("v2", corrupt = true)
        val e = assertFailsWith<UpdateException> { updater().update() }
        assertTrue("damaged" in e.message!!, e.message)
        assertEquals("v1", DictionaryStore(dir).current()!!.version)
        assertFalse(dir.list()!!.any { it.endsWith(".part") || it.endsWith(".tmp") }, dir.list()!!.toList().toString())
    }

    @Test
    fun rejectsADatabaseThatWontOpen() = runTest {
        publish("v1")
        assertFailsWith<UpdateException> { updater(valid = false).update() }
        assertEquals(null, DictionaryStore(dir).current())
    }

    @Test
    fun checksFreeSpaceFirst() = runTest {
        publish("v1")
        val e = assertFailsWith<UpdateException> { updater(free = 1_000_000).update() }
        assertTrue("space" in e.message!!)
        assertTrue(requests.none { it.endsWith(".xz") }, "nothing downloaded")
    }

    @Test
    fun needsANewerAppForANewSchema() = runTest {
        publish("v9", schema = 2)
        val e = assertFailsWith<UpdateException> { updater().update() }
        assertTrue("newer version of the app" in e.message!!)
    }

    @Test
    fun reportsNoConnection() = runTest {
        server.stop(0)
        val e = assertFailsWith<UpdateException> { updater().update() }
        assertTrue("internet" in e.message!!)
    }
}
