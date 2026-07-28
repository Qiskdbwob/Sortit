package com.sortit

import com.sortit.data.TemplateEntity
import com.sortit.domain.FakeFileOps
import com.sortit.domain.FileScanner
import com.sortit.domain.ScanSortUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileScannerExcludeTest {

    @Test
    fun `prefix path excludes children`() {
        val patterns = setOf("/storage/emulated/0/Download")
        assertTrue(FileScanner.isExcluded("/storage/emulated/0/Download", "Download", patterns))
        assertTrue(FileScanner.isExcluded("/storage/emulated/0/Download/a.pdf", "a.pdf", patterns))
        assertTrue(FileScanner.isExcluded("/storage/emulated/0/Download/sub/x.txt", "x.txt", patterns))
        assertFalse(FileScanner.isExcluded("/storage/emulated/0/DCIM/a.jpg", "a.jpg", patterns))
    }

    @Test
    fun `exact file name exclude`() {
        val patterns = setOf("secret.bak")
        assertTrue(FileScanner.isExcluded("/a/secret.bak", "secret.bak", patterns))
        assertFalse(FileScanner.isExcluded("/a/other.bak", "other.bak", patterns))
    }

    @Test
    fun `root under global exclude is skipped`() {
        val patterns = setOf("/storage/emulated/0/Download")
        assertTrue(FileScanner.isRootExcluded("/storage/emulated/0/Download", patterns))
        assertTrue(FileScanner.isRootExcluded("/storage/emulated/0/Download/Inbox", patterns))
        assertFalse(FileScanner.isRootExcluded("/storage/emulated/0/DCIM", patterns))
        assertFalse(FileScanner.isRootExcluded("/storage/emulated/0", patterns))
    }

    @Test
    fun `scan skips global excluded folder`() = runBlocking {
        val files = listOf(
            File("/storage/emulated/0/Download/skip.txt"),
            File("/storage/emulated/0/Docs/keep.txt")
        )
        val ops = FakeFileOps(files)
        val use = ScanSortUseCase(ops)
        val tpl = TemplateEntity(
            name = "t",
            extensions = "txt",
            targetTreeUri = "/out",
            sourceMode = "FOLDERS",
            sourceDirs = "/storage/emulated/0/Download,/storage/emulated/0/Docs"
        )
        var found = 0
        use.execute(tpl, setOf("/storage/emulated/0/Download")).collect { found = it.found }
        assertEquals(1, found)
    }

    @Test
    fun `scanTemplate merges exclude like global`() {
        val files = listOf(
            File("/a/keep.txt"),
            File("/b/skip.txt")
        )
        val ops = FakeFileOps(files)
        val tpl = TemplateEntity(
            id = 1,
            name = "t",
            extensions = "txt",
            targetTreeUri = "/out",
            sourceMode = "FOLDERS",
            sourceDirs = "/a,/b"
        )
        val out = FileScanner.scanTemplate(tpl, ops, setOf("/b"), mutableSetOf())
        assertEquals(listOf("/a/keep.txt"), out.map { it.item.path })
    }

    @Test
    fun `matchesMeta size and age`() {
        val base = TemplateEntity(
            name = "m", extensions = "txt", targetTreeUri = "/out",
            sourceMode = "FOLDERS", sourceDirs = "/a",
            minSizeBytes = 100, maxSizeBytes = 1000, maxAgeDays = 2
        )
        val now = 10_000_000L
        assertFalse(FileScanner.matchesMeta(base, 50, now - 10L * 86_400_000L, now))
        assertFalse(FileScanner.matchesMeta(base, 5000, now - 10L * 86_400_000L, now))
        assertFalse(FileScanner.matchesMeta(base, 200, now - 1L * 86_400_000L, now))
        assertTrue(FileScanner.matchesMeta(base, 200, now - 5L * 86_400_000L, now))
    }
}
