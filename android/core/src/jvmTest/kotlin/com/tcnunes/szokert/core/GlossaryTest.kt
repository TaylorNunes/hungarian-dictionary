package com.tcnunes.szokert.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GlossaryTest {
    // The same file the website uses; the app copies it in at build time.
    private val glossary = Glossary.parse(File(System.getProperty("szokert.fixtures"), "../../src/lib/glossary.json").readText())

    @Test
    fun readsTheSharedGlossary() {
        assertEquals(listOf("Grammar", "Style and tone"), glossary.labelGroups.take(2))
        assertEquals("főnév", glossary.describePos("noun").hu)
        assertNotNull(glossary.describeLabel("transitive"))
        assertEquals("not comparable", glossary.describePos("not-comparable").label)
        val grammar = glossary.labelsInGroup("Grammar").map { labelName(it.first) }
        assertEquals(grammar.sortedWith(String.CASE_INSENSITIVE_ORDER), grammar)
        assertTrue(glossary.allPos().size > 20)
    }
}
