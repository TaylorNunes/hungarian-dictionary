package com.tcnunes.szokert.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class FoldTest {
    @Test
    fun matchesSharedFixture() {
        val cases = Fixtures.json("fold_cases.json").jsonArray
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val (raw, expected) = case.jsonArray.map { it.jsonPrimitive.content }
            assertEquals(expected, fold(raw), raw)
        }
    }

    @Test
    fun shardCharsReplacesNonAsciiByCodePoint() {
        assertEquals("haz", shardChars("haz"))
        assertEquals("a_b", shardChars("a b"))
        assertEquals("x_", shardChars("x😀"))
    }

    @Test
    fun accents() {
        assertTrue(hasAccents("ház"))
        assertFalse(hasAccents("Haz"))
    }
}
