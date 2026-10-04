package com.tcnunes.szokert.saved

import android.content.Context
import com.tcnunes.szokert.core.Lemma
import com.tcnunes.szokert.core.SavedWord
import com.tcnunes.szokert.core.savedWord
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Saved words, newest first, in a JSON file using the website's format. A word is identified by
 * headword and part of speech: lemma ids change when the dictionary is rebuilt.
 */
class SavedStore(context: Context) {
    private val file = File(context.filesDir, "saved.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(SavedWord.serializer())
    private val _list = MutableStateFlow(load())
    val list: StateFlow<List<SavedWord>> = _list

    fun isSaved(word: String, pos: String): Boolean = _list.value.any { it.word == word && it.pos == pos }

    @Synchronized
    fun toggle(lemmaId: Int, lemma: Lemma, lookedUp: String?) {
        _list.value = if (isSaved(lemma.w, lemma.pos)) {
            _list.value.filterNot { it.word == lemma.w && it.pos == lemma.pos }
        } else {
            listOf(savedWord(lemmaId, lemma, lookedUp, System.currentTimeMillis())) + _list.value
        }
        persist()
    }

    @Synchronized
    fun remove(word: SavedWord) {
        _list.value = _list.value.filterNot { it.word == word.word && it.pos == word.pos }
        persist()
    }

    private fun load(): List<SavedWord> =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    private fun persist() {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(serializer, _list.value))
        tmp.renameTo(file)
    }
}
