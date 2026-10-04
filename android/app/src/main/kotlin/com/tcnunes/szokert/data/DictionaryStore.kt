package com.tcnunes.szokert.data

import java.io.File

/**
 * The dictionary's files on the phone, in [dir]:
 *   current                    the installed version
 *   dict-<version>.db          the installed database
 *   download-<sha>.xz.part     a download in progress, named after its checksum so it can resume
 *   unpack-<version>.db.tmp    a database being unpacked
 */
class DictionaryStore(val dir: File) {
    data class Installed(val version: String, val file: File)

    init {
        dir.mkdirs()
    }

    private val pointer = File(dir, "current")

    fun current(): Installed? {
        val version = pointer.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val file = databaseFile(version)
        return if (file.exists()) Installed(version, file) else null
    }

    fun databaseFile(version: String) = File(dir, "dict-$version.db")

    fun partFile(sha256: String) = File(dir, "download-${sha256.take(16)}.xz.part")

    fun unpackFile(version: String) = File(dir, "unpack-$version.db.tmp")

    fun removePartsExcept(keep: File) {
        dir.listFiles { f -> f.name.endsWith(".xz.part") && f != keep }?.forEach { it.delete() }
    }

    /** Move a complete, checked database into place and point at it; older versions are removed. */
    fun install(version: String, unpacked: File) {
        val target = databaseFile(version)
        if (!unpacked.renameTo(target)) error("couldn't move the new dictionary into place")
        val tmp = File(dir, "current.tmp")
        tmp.writeText(version)
        if (!tmp.renameTo(pointer)) error("couldn't switch to the new dictionary")
        // An open connection to an old file keeps working until it is closed. Checking the unpacked
        // file opened it with SQLite, which can leave a journal beside it.
        dir.listFiles { f -> (f.name.startsWith("dict-") && f != target) || f.name.startsWith("unpack-") }?.forEach { it.delete() }
    }

    /** Remove everything, e.g. to download again from scratch. */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
