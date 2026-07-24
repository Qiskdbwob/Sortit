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

    @Test
    fun `truncateFileName keeps extension visible`() {
        val longName = "very_long_filename_photo_export.jpg"
        val out = truncateFileName(longName, maxLen = 24)
        assertTrue(out.endsWith(".jpg"))
        assertTrue(out.contains("\u2026"))
        assertTrue(out.length <= 24)
    }

    @Test
    fun `truncateFileName short name unchanged`() {
        assertEquals("a.txt", truncateFileName("a.txt"))
    }

    @Test
    fun `splitMonitorPaths supports newline and pipe`() {
        val raw = "/a/b\n/c/d|/e/f"
        assertEquals(listOf("/a/b", "/c/d", "/e/f"), splitMonitorPaths(raw))
    }
}
