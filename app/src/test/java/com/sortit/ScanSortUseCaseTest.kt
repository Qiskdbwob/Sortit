package com.sortit.domain

import com.sortit.data.TemplateEntity
import com.sortit.repo.FileOps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

// Fake FileOps yang tidak menyentuh disk sungguhan.
class FakeFileOps(private val files: List<File>) : FileOps {
    val moved = mutableListOf<Pair<String, String>>()
    override fun listFiles(dir: String): List<File> = files
    override fun walkDeep(dir: String): Sequence<File> = files.asSequence()
    override fun exists(path: String): Boolean = files.any { it.absolutePath == path }
    override fun size(path: String): Long = files.firstOrNull { it.absolutePath == path }?.length() ?: 0
    override fun lastModified(path: String): Long = 0
    override fun mimeOf(file: File): String? = if (file.name.endsWith(".jpg")) "image/jpeg" else null
    override fun move(src: String, dstDir: String): String? {
        moved.add(src to dstDir)
        return "$dstDir/${File(src).name}"
    }
    override fun mkdirs(dir: String): Boolean = true
}

class ScanSortUseCaseTest {
    private fun tpl(mode: String, dirs: String? = null) = TemplateEntity(
        name = "t", extensions = "txt,bak", targetTreeUri = "/out",
        sourceMode = mode, sourceDirs = dirs
    )

    @Test
    fun `mode FOLDERS only scans given dirs`() = runBlocking {
        val files = listOf(File("/a/x.txt"), File("/b/y.txt"), File("/a/z.bak"))
        val ops = FakeFileOps(files)
        val use = ScanSortUseCase(ops)
        val prog = use.execute(tpl("FOLDERS", "/a"), emptySet())
        var found = 0
        prog.collect { found = it.found }
        assertEquals(2, found) // /a/x.txt + /a/z.bak, bukan /b/y.txt
    }

    @Test
    fun `excludes patterns are skipped`() = runBlocking {
        val files = listOf(File("/a/keep.txt"), File("/a/skip.txt"))
        val ops = FakeFileOps(files)
        val use = ScanSortUseCase(ops)
        val prog = use.execute(tpl("FOLDERS", "/a"), setOf("/a/skip.txt"))
        var found = 0
        prog.collect { found = it.found }
        assertEquals(1, found)
    }

    @Test
    fun `mode ALL skips system paths`() = runBlocking {
        val files = listOf(File("/storage/emulated/0/Download/ok.txt"), File("/system/x.txt"))
        val ops = FakeFileOps(files)
        val use = ScanSortUseCase(ops)
        val prog = use.execute(tpl("ALL"), emptySet())
        var found = 0
        prog.collect { found = it.found }
        assertEquals(1, found) // /system dikecualikan
    }
}
