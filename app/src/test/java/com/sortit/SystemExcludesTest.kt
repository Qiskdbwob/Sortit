package com.sortit.util
import com.sortit.repo.FileOps

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
    fun `detects app root as excluded`() {
        assertTrue(SystemExcludes.isSystemPath("/storage/emulated/0/Sortit"))
        assertTrue(SystemExcludes.isSystemPath("/storage/emulated/0/Sortit/sampah"))
        assertTrue(SystemExcludes.isSystemPath("/storage/emulated/0/Sortit/templates"))
    }

    @Test
    fun `detects trash root as excluded`() {
        val trash = FileOps.TRASH_ROOT
        assertTrue(SystemExcludes.isSystemPath(trash))
        assertTrue(SystemExcludes.isSystemPath("$trash/foto.jpg"))
        assertTrue(SystemExcludes.isSystemPath("$trash/subfolder/file.pdf"))
    }

    @Test
    fun `allows normal user storage`() {
        assertFalse(SystemExcludes.isSystemPath("/storage/emulated/0/Download"))
        assertFalse(SystemExcludes.isSystemPath("/storage/emulated/0/DCIM/Camera"))
        assertFalse(SystemExcludes.isSystemPath("/storage/emulated/0/WhatsApp/Media"))
    }
}
