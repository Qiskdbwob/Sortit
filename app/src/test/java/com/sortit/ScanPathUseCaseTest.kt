package com.sortit

import com.sortit.domain.ScanPathUseCase
import com.sortit.repo.FileOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun scanTestFile(
    path: String,
    size: Long = 10L,
    lastMod: Long = 0L
): File = object : File(path) {
    override fun isFile(): Boolean = true
    override fun isDirectory(): Boolean = false
    override fun exists(): Boolean = true
    override fun length(): Long = size
    override fun lastModified(): Long = lastMod
    override fun getAbsolutePath(): String = path
    override fun getName(): String = path.substringAfterLast('/')
    override fun canRead(): Boolean = true
}

/** FileOps in-memory untuk ScanPathUseCase — tanpa mockito File. */
private class ScanFakeOps : FileOps {
    private val byDir = linkedMapOf<String, MutableList<File>>()
    private val readable = mutableSetOf<String>()
    private val existing = mutableSetOf<String>()

    fun setDir(path: String, files: List<File>, readableDir: Boolean = true, exists: Boolean = true) {
        val p = path.trimEnd('/')
        byDir[p] = files.toMutableList()
        if (exists) existing += p
        if (readableDir) readable += p else readable -= p
    }

    override fun listFiles(dir: String): List<File> = byDir[dir.trimEnd('/')]?.toList() ?: emptyList()
    override fun walkDeep(dir: String): Sequence<File> = listFiles(dir).asSequence()
    override fun exists(path: String): Boolean =
        existing.contains(path.trimEnd('/')) || byDir.containsKey(path.trimEnd('/'))
    override fun size(path: String): Long = 0
    override fun lastModified(path: String): Long = 0
    override fun mimeOf(file: File): String? =
        if (file.name.endsWith(".jpg", true)) "image/jpeg" else null
    override fun move(src: String, dstDir: String): String? = null
    override fun moveToTrash(src: String): String? = null
    override fun mkdirs(dir: String): Boolean = true
    override fun isReadableDir(path: String): Boolean = readable.contains(path.trimEnd('/'))
    override fun childCount(path: String): Int = listFiles(path).size
    override fun restore(src: String, dstDir: String): String? = null
    override fun listTrashFiles(): List<File> = emptyList()
    override fun deleteFile(path: String): Boolean = false
}

class ScanPathUseCaseTest {

    @Test
    fun emptyPath_isNotReadable() {
        val r = ScanPathUseCase(ScanFakeOps()).inspect("")

        assertFalse(r.readable)
        assertEquals("Path kosong", r.message)
        assertEquals(0, r.fileCount)
        assertEquals(0, r.pathsScanned)
    }

    @Test
    fun systemPath_isExcluded() {
        val r = ScanPathUseCase(ScanFakeOps()).inspect("/system")

        assertFalse(r.readable)
        assertEquals("Path sistem dikecualikan", r.message)
        assertEquals(0, r.fileCount)
        assertEquals(0, r.pathsReadable)
    }

    @Test
    fun singleReadablePath_reportsAccurateFileCount() {
        val ops = ScanFakeOps().also {
            it.setDir(
                "/d",
                listOf(
                    scanTestFile("/d/a.txt", lastMod = 1),
                    scanTestFile("/d/b.txt", lastMod = 2),
                    scanTestFile("/d/c.txt", lastMod = 3)
                )
            )
        }
        val r = ScanPathUseCase(ops).inspect("/d")

        assertTrue(r.readable)
        assertEquals(3, r.fileCount)
        assertEquals(3, r.files.size)
        assertEquals("OK", r.message)
        assertFalse(r.truncated)
        assertEquals(1, r.pathsReadable)
    }

    @Test
    fun moreFilesThanMax_truncatesList() {
        val ops = ScanFakeOps().also {
            it.setDir(
                "/d",
                (1..5).map { n -> scanTestFile("/d/f$n.txt", lastMod = n.toLong()) }
            )
        }
        val r = ScanPathUseCase(ops).inspect("/d", maxFiles = 2)

        assertTrue(r.truncated)
        assertEquals(2, r.files.size)
        assertEquals(5, r.fileCount)
    }

    @Test
    fun multiPath_mergesAndSortsNewestFirst() {
        val ops = ScanFakeOps().also {
            it.setDir("/d1", listOf(scanTestFile("/d1/old.txt", lastMod = 10)))
            it.setDir(
                "/d2",
                listOf(
                    scanTestFile("/d2/new.txt", lastMod = 99),
                    scanTestFile("/d2/mid.txt", lastMod = 50)
                )
            )
        }
        val r = ScanPathUseCase(ops).inspect("/d1\n/d2")

        assertTrue(r.readable)
        assertEquals(2, r.pathsScanned)
        assertEquals(2, r.pathsReadable)
        assertEquals(3, r.fileCount)
        assertEquals(
            listOf("/d2/new.txt", "/d2/mid.txt", "/d1/old.txt"),
            r.files.map { it.path }
        )
    }

    @Test
    fun multiPath_countsReadableDirsCorrectly() {
        val ops = ScanFakeOps().also {
            it.setDir("/d1", listOf(scanTestFile("/d1/a.txt", lastMod = 1)), readableDir = true)
            it.setDir("/d2", emptyList(), readableDir = false, exists = true)
        }
        val r = ScanPathUseCase(ops).inspect("/d1\n/d2")

        assertTrue(r.readable)
        assertEquals(2, r.pathsScanned)
        assertEquals(1, r.pathsReadable)
        assertEquals(1, r.fileCount)
    }

    @Test
    fun emptyDirectory_hasFileCountZero() {
        val ops = ScanFakeOps().also { it.setDir("/d", emptyList()) }
        val r = ScanPathUseCase(ops).inspect("/d")

        assertTrue(r.readable)
        assertEquals(0, r.fileCount)
        assertTrue(r.files.isEmpty())
        assertFalse(r.truncated)
    }
}
