package com.tcnunes.szokert.data

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.tukaani.xz.XZInputStream

/** public/data/manifest.json, as far as the app needs it (scripts/build_data.py: write_output). */
@Serializable
data class DataManifest(val version: String, val built: String = "", val databases: List<DatabaseInfo> = emptyList())

@Serializable
data class DatabaseInfo(
    val schema: Int,
    /** Relative to the data URL: "<version>/szokert.db.xz". */
    val path: String,
    val bytes: Long,
    val sha256: String,
    val rawBytes: Long,
    val rawSha256: String,
)

sealed interface Progress {
    data class Downloading(val done: Long, val total: Long) : Progress
    data object Verifying : Progress
    data class Unpacking(val done: Long, val total: Long) : Progress
}

sealed interface UpdateOutcome {
    data class Installed(val version: String) : UpdateOutcome
    data class UpToDate(val version: String) : UpdateOutcome
}

/** Why an update failed, in words for the person using the app. [retryable]: worth trying again by itself later. */
class UpdateException(message: String, cause: Throwable? = null, val retryable: Boolean = false) : IOException(message, cause)

/**
 * Downloads, checks and installs the dictionary database. The download resumes where it stopped
 * (HTTP Range); both the download and the unpacked database are checked against the manifest's
 * checksums; the new database replaces the old one only once it is complete, so a failure at any
 * point leaves the installed dictionary working.
 *
 * Plain Kotlin (no Android APIs) so it can be tested against a local web server.
 */
class DictionaryUpdater(
    private val dataUrl: String,
    private val store: DictionaryStore,
    private val schema: Int,
    /** Opens the unpacked file and confirms it is a dictionary database of this version. */
    private val validate: (File, String) -> Boolean,
    /** Bytes that can be written in the store's directory. */
    private val freeSpace: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchManifest(): DataManifest = withContext(Dispatchers.IO) {
        try {
            val conn = open("manifest.json", from = 0)
            conn.useStream { json.decodeFromString(DataManifest.serializer(), it.readBytes().decodeToString()) }
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            throw UpdateException("Couldn't reach the dictionary server. Check your internet connection.", e, retryable = true)
        }
    }

    /** The database this app can read, or null when the data needs a newer app. */
    fun pick(manifest: DataManifest): DatabaseInfo? = manifest.databases.firstOrNull { it.schema == schema }

    /** Bring the installed dictionary up to the server's version. */
    suspend fun update(onProgress: suspend (Progress) -> Unit = {}): UpdateOutcome = withContext(Dispatchers.IO) {
        // The server keeps only the latest version, so a file vanishing mid-download means a new
        // version was published: read the manifest again and start on that one.
        repeat(MAX_ATTEMPTS) {
            val manifest = fetchManifest()
            val db = pick(manifest) ?: throw UpdateException("This dictionary version needs a newer version of the app.")
            if (store.current()?.version == manifest.version) return@withContext UpdateOutcome.UpToDate(manifest.version)
            try {
                install(manifest.version, db, onProgress)
                return@withContext UpdateOutcome.Installed(manifest.version)
            } catch (_: VanishedException) {
                // try again with the new manifest
            }
        }
        throw UpdateException("The dictionary changed on the server during the download. Try again.")
    }

    private suspend fun install(version: String, db: DatabaseInfo, onProgress: suspend (Progress) -> Unit) {
        val part = store.partFile(db.sha256)
        store.removePartsExcept(part)
        val needed = (db.bytes - part.length()).coerceAtLeast(0) + db.rawBytes + SPACE_MARGIN
        if (freeSpace() < needed) {
            throw UpdateException("Not enough free space: the dictionary needs about ${needed / 1_000_000} MB.")
        }

        download(db, part, onProgress)
        // Some HTTP clients end a dropped connection quietly instead of with an error: a short file is
        // an interrupted download (kept, to resume), not a damaged one.
        if (part.length() < db.bytes) throw UpdateException(INTERRUPTED, retryable = true)
        onProgress(Progress.Verifying)
        if (part.length() != db.bytes || sha256(part) != db.sha256) {
            part.delete()
            throw UpdateException("The download was damaged. Try again.")
        }

        val unpacked = store.unpackFile(version)
        try {
            unpack(part, unpacked, db, onProgress)
            if (!validate(unpacked, version)) throw UpdateException("The downloaded dictionary couldn't be opened. Try again.")
            store.install(version, unpacked)
        } finally {
            unpacked.delete()
        }
        part.delete()
    }

    private suspend fun download(db: DatabaseInfo, part: File, onProgress: suspend (Progress) -> Unit) {
        if (part.length() >= db.bytes) return
        val conn = try {
            open(db.path, from = part.length())
        } catch (e: VanishedException) {
            part.delete()
            throw e
        } catch (e: IOException) {
            throw UpdateException(INTERRUPTED, e, retryable = true)
        }
        // 206: continuing; 200: the server sent the whole file, so start again.
        val append = conn.responseCode == HttpURLConnection.HTTP_PARTIAL
        var done = if (append) part.length() else 0L
        try {
            conn.useStream { input ->
                java.io.FileOutputStream(part, append).use { out ->
                    val buffer = ByteArray(BUFFER)
                    var reported = 0L
                    onProgress(Progress.Downloading(done, db.bytes))
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (done - reported >= REPORT_EVERY) {
                            reported = done
                            onProgress(Progress.Downloading(done, db.bytes))
                        }
                    }
                }
            }
        } catch (e: IOException) {
            throw UpdateException(INTERRUPTED, e, retryable = true)
        }
        onProgress(Progress.Downloading(done, db.bytes))
    }

    private suspend fun unpack(packed: File, out: File, db: DatabaseInfo, onProgress: suspend (Progress) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        var reported = 0L
        XZInputStream(packed.inputStream().buffered(BUFFER)).use { input ->
            out.outputStream().buffered(BUFFER).use { output ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    output.write(buffer, 0, n)
                    done += n
                    if (done - reported >= REPORT_EVERY * 4) {
                        reported = done
                        onProgress(Progress.Unpacking(done, db.rawBytes))
                    }
                }
            }
        }
        if (done != db.rawBytes || digest.digest().toHex() != db.rawSha256) {
            packed.delete()
            throw UpdateException("The downloaded dictionary was damaged. Try again.")
        }
    }

    private fun open(path: String, from: Long): HttpURLConnection {
        val conn = URI(dataUrl + path).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept-Encoding", "identity")
        if (from > 0) conn.setRequestProperty("Range", "bytes=$from-")
        when (val code = conn.responseCode) {
            HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL -> return conn
            HttpURLConnection.HTTP_NOT_FOUND -> {
                conn.disconnect()
                throw VanishedException()
            }
            else -> {
                conn.disconnect()
                throw UpdateException("The dictionary server answered $code. Try again later.")
            }
        }
    }

    private inline fun <T> HttpURLConnection.useStream(block: (InputStream) -> T): T =
        try {
            inputStream.use(block)
        } finally {
            disconnect()
        }

    private class VanishedException : IOException("not found")

    companion object {
        private const val BUFFER = 64 * 1024
        private const val MAX_ATTEMPTS = 3
        private const val INTERRUPTED = "The download stopped. Check your internet connection; it will continue where it left off."
        private const val REPORT_EVERY = 256L * 1024
        /** Room for SQLite and the rest of the phone. */
        private const val SPACE_MARGIN = 20L * 1024 * 1024

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(BUFFER).use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
