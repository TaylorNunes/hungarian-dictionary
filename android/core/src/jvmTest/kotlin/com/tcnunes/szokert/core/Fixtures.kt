package com.tcnunes.szokert.core

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Test cases shared with the TypeScript and Python tests, from scripts/fixtures. */
object Fixtures {
    private val dir = File(System.getProperty("szokert.fixtures") ?: error("szokert.fixtures is not set"))

    fun json(name: String): JsonElement = Json.parseToJsonElement(File(dir, name).readText())
}
