package com.sortit.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemExcludesTest {
    @Test
    fun `detects exact system path`() {
        assertTrue(SystemExcludes.isSystemPath("/system"))
        assertTrue(SystemExcludes.isSystemPath("/data"))
    }

    @Test
    fun `detects nested under system path`() {
        assertTrue(SystemExcludes.isSystemPath("/system/bin/sh"))
        assertTrue(SystemExcludes.isSystemPath("/storage/emulated/0/Android/data/com.x"))
    }

    @Test
    fun `allows user storage`() {
        assertFalse(SystemExcludes.isSystemPath("/storage/emulated/0/Download"))
        assertFalse(SystemExcludes.isSystemPath("/storage/emulated/0/Sortit/sampah"))
    }
}
