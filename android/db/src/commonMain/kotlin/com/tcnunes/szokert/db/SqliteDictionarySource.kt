package com.tcnunes.szokert.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.tcnunes.szokert.core.DictionaryJson
import com.tcnunes.szokert.core.DictionarySource
import com.tcnunes.szokert.core.EnglishRow
import com.tcnunes.szokert.core.FormRow
import com.tcnunes.szokert.core.HeadRow
import com.tcnunes.szokert.core.Lemma
import com.tcnunes.szokert.core.TableEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer

/** The layout this app reads: PRAGMA user_version, DB_SCHEMA in scripts/build_data.py. */
const val DB_SCHEMA = 1

/** U+10FFFF: sorts after every other code point, so key < prefix + MAX_CHAR bounds a prefix range. */
private const val MAX_CHAR = "􏿿"

/**
 * The dictionary database (scripts/build_data.py: write_sqlite). Rows come back in the same order as
 * the web app's JSON shards, which search ranking depends on (checked by scripts/check_db.py).
 * One connection, used by one caller at a time.
 */
class SqliteDictionarySource(private val db: SQLiteConnection) : DictionarySource, AutoCloseable {
    private val lock = Mutex()
    private var tagSets: List<List<String>>? = null
    private val lemmas = object : LinkedHashMap<Int, Lemma>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Lemma>?) = size > 512
    }

    /** The database's schema and meta table (version, built, counts). */
    val schema: Int get() = query("PRAGMA user_version") { it.getLong(0).toInt() }.single()
    val meta: Map<String, String> get() = query("SELECT key, value FROM meta") { it.getText(0) to it.getText(1) }.toMap()

    override suspend fun forms(folded: String): List<FormRow> = lock.withLock {
        query("SELECT form, lemma_id, tag_idx FROM forms WHERE folded = ? ORDER BY ord", folded) {
            FormRow(it.getText(0), it.getLong(1).toInt(), it.getLong(2).toInt())
        }
    }

    override suspend fun english(term: String): List<EnglishRow> = lock.withLock {
        query("SELECT lemma_id, sense, position FROM en WHERE term = ? ORDER BY ord", term) {
            EnglishRow(it.getLong(0).toInt(), it.getLong(1).toInt(), it.getLong(2).toInt())
        }
    }

    override suspend fun headsStarting(prefix: String): List<HeadRow> = lock.withLock {
        query("SELECT word, lemma_id, pos, rank, gloss FROM heads WHERE key >= ? AND key < ? ORDER BY key, lemma_id",
            prefix, prefix + MAX_CHAR, read = ::headRow)
    }

    override suspend fun headsEnding(reversedSuffix: String): List<HeadRow> = lock.withLock {
        query("SELECT word, lemma_id, pos, rank, gloss FROM heads WHERE rkey >= ? AND rkey < ? ORDER BY rkey, lemma_id",
            reversedSuffix, reversedSuffix + MAX_CHAR, read = ::headRow)
    }

    override suspend fun lemma(id: Int): Lemma? = lock.withLock {
        lemmas[id] ?: query("SELECT data, tbl FROM lemmas WHERE id = ?", id.toLong()) { row ->
            val table = if (row.isNull(1)) null else DictionaryJson.decodeFromString(TABLE, inflate(row.getBlob(1)).decodeToString())
            DictionaryJson.decodeFromString(Lemma.serializer(), row.getText(0)).copy(t = table)
        }.firstOrNull()?.also { lemmas[id] = it }
    }

    override suspend fun tags(): List<List<String>> = lock.withLock {
        tagSets ?: query("SELECT tags FROM tags ORDER BY idx") { row ->
            row.getText(0).let { if (it.isEmpty()) emptyList() else it.split(' ') }
        }.also { tagSets = it }
    }

    /** Entries per part of speech, most first (for the Guide). */
    suspend fun posCounts(): List<Pair<String, Int>> = lock.withLock {
        query("SELECT pos, count(*) AS n FROM lemmas GROUP BY pos ORDER BY n DESC, pos") { it.getText(0) to it.getLong(1).toInt() }
    }

    override fun close() = db.close()

    private fun headRow(row: SQLiteStatement) =
        HeadRow(row.getText(0), row.getLong(1).toInt(), row.getText(2), row.getLong(3).toInt(), row.getText(4))

    private fun <T> query(sql: String, vararg args: Any, read: (SQLiteStatement) -> T): List<T> =
        db.prepare(sql).use { st ->
            args.forEachIndexed { i, a ->
                when (a) {
                    is String -> st.bindText(i + 1, a)
                    is Long -> st.bindLong(i + 1, a)
                    else -> error("unsupported argument $a")
                }
            }
            buildList { while (st.step()) add(read(st)) }
        }

    private companion object {
        val TABLE = ListSerializer(TableEntry.serializer())
    }
}

/** zlib inflate (platform-specific). */
internal expect fun inflate(data: ByteArray): ByteArray
