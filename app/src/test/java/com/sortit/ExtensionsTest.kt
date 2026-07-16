package com.sortit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionsTest {
    @Test
    fun `parseExtensions normalizes dots spaces and case`() {
        assertEquals(setOf("txt", "bak", "tmp"), parseExtensions(" .TXT, .bak ;tmp "))
    }

    @Test
    fun `parseExtensions drops invalid tokens`() {
        assertEquals(setOf("pdf"), parseExtensions("pdf, , ., @#@"))
    }

    @Test
    fun `matchesExtension true for known ext`() {
        assertTrue(matchesExtension("note.TXT", setOf("txt")))
        assertTrue(matchesExtension("a.bak", setOf("bak")))
    }

    @Test
    fun `matchesExtension false for no ext or mismatch`() {
        assertFalse(matchesExtension("noext", setOf("txt")))
        assertFalse(matchesExtension("a.txt", setOf("bak")))
    }
}
