package com.sortit.domain

import com.sortit.data.TemplateEntity
import com.sortit.repo.FileOps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FakeFileOps(private val files: List<File>) : FileOps {
    val moved = mutableListOf<Pair<String, String>>()
    override fun listFiles(dir: String): List<File> = files
    override fun walkDeep(dir: String): Sequence<File> = files.asSequence().filter { it.absolutePath == dir || it.absolutePath.startsWith("$dir/") }
    override fun exists(path: String): Boolean = files.any { it.absolutePath == path }
    override fun size(path: String): Long = files.firstOrNull { it.absolutePath == path }?.length() ?: 0
    override fun lastModified(path: String): Long = 0
    override fun mimeOf(file: File): String? = if (file.name.endsWith(".jpg")) "image/jpeg" else null
    override fun move(src: String, dstDir: String): String? {
        moved.add(src to dstDir)
        return "$dstDir/${File(src).name}"
    }
    override fun moveToTrash(src: String): String? = move(src, FileOps.TRASH_ROOT)
    override fun mkdirs(dir: String): Boolean = true
    override fun isReadableDir(path: String): Boolean = true
    override fun childCount(path: String): Int = files.count { it.absolutePath == path || it.absolutePath.startsWith("$path/") }
}

class ScanSortUseCaseTest {
    private fun tpl(mode: String, dirs: String? = null, name: String = "t") = TemplateEntity(
        name = name, extensions = "txt,bak", targetTreeUri = "/out",
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
        assertEquals(2, found)
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
        assertEquals(1, found)
    }

    @Test
    fun `multi rule scan dedupes same file and keeps template id`() = runBlocking {
        val files = listOf(File("/a/x.txt"), File("/a/x.txt"), File("/a/y.bak"))
        val ops = FakeFileOps(files)
        val use = ScanSortUseCase(ops)
        val candidates = mutableListOf<Pair<Long, FileItem>>()
        val prog = use.executeMany(listOf(tpl("FOLDERS", "/a", "one"), tpl("FOLDERS", "/a", "two")), emptySet()) { templateId, item ->
            candidates.add(templateId to item)
        }
        var found = 0
        prog.collect { found = it.found }
        assertEquals(2, found)
        assertEquals(2, candidates.size)
        assertEquals(setOf("/a/x.txt", "/a/y.bak"), candidates.map { it.second.path }.toSet())
    }
}
