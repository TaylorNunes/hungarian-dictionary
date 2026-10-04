package com.tcnunes.szokert.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** The dictionary's records, as written by scripts/build_data.py (see src/lib/types.ts). */

/** A Hungarian sentence and its English translation. */
typealias Example = List<String>

@Serializable
data class Sense(
    /** English gloss. */
    val g: String,
    /** Parent gloss, for sub-senses. */
    val p: String? = null,
    /** Usage labels (transitive, archaic…). */
    val t: List<String>? = null,
    val ex: List<Example>? = null,
)

/** One cell of an inflection table: a form and the index of its tag set. */
@Serializable(with = TableEntrySerializer::class)
data class TableEntry(val form: String, val tag: Int)

@Serializable
data class Lemma(
    val w: String,
    val pos: String,
    val ipa: String? = null,
    val s: List<Sense>,
    /** Inflection table. */
    val t: List<TableEntry>? = null,
    /** Example sentences from Tatoeba. */
    val ex: List<Example>? = null,
    /** Frequency rank in subtitles (1 = most common); absent for words not seen there. */
    val fr: Int? = null,
)

/** A form-index row: the form, its lemma, and its tag set index (0 = the dictionary form). */
data class FormRow(val form: String, val lemmaId: Int, val tag: Int)

/** An English-index row: the lemma, the sense whose gloss has the term, 0 if the term leads it else 1. */
data class EnglishRow(val lemmaId: Int, val sense: Int, val position: Int)

/** A headword row for partial matches: rank 0 when unranked; gloss is the first meaning, cut short. */
data class HeadRow(val word: String, val lemmaId: Int, val pos: String, val rank: Int, val gloss: String)

/** A label in a word breakdown, e.g. "accusative" with the hint "direct object". */
@Serializable
data class Part(val label: String, val hint: String? = null)

val DictionaryJson = Json { ignoreUnknownKeys = true }

object TableEntrySerializer : KSerializer<TableEntry> {
    private val delegate = ListSerializer(JsonPrimitive.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): TableEntry {
        val array = (decoder as JsonDecoder).decodeJsonElement().jsonArray
        return TableEntry(array[0].jsonPrimitive.content, array[1].jsonPrimitive.int)
    }

    override fun serialize(encoder: Encoder, value: TableEntry) {
        (encoder as JsonEncoder).encodeJsonElement(JsonArray(listOf(JsonPrimitive(value.form), JsonPrimitive(value.tag))))
    }
}
