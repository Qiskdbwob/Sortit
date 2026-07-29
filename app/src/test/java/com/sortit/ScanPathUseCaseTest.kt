package com.sortit

import com.sortit.domain.ScanPathUseCase
import com.sortit.repo.FileOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class ScanPathUseCaseTest {

    private val fileOps = mock<FileOps>()
    private val useCase = ScanPathUseCase(fileOps)

    private fun fakeFile(path: String, size: Long = 10L, lastMod: Long = 0L): File {
        val f = mock<File>()
        whenever(f.absolutePath).thenReturn(path)
        whenever(f.name).thenReturn(path.substringAfterLast('/'))
        whenever(f.isFile).thenReturn(true)
        whenever(f.length()).thenReturn(size)
        whenever(f.lastModified()).thenReturn(lastMod)
        return f
    }

    @Test
    fun `empty path is not readable`() {
        val r = useCase.inspect("")

        assertFalse(r.readable)
        assertEquals("Path kosong", r.message)
        assertEquals(0, r.fileCount)
        assertEquals(0, r.pathsScanned)
    }

    @Test
    fun `system path is excluded`() {
        val r = useCase.inspect("/system")

        assertFalse(r.readable)
        assertEquals("Path sistem dikecualikan", r.message)
        assertEquals(0, r.fileCount)
        assertEquals(0, r.pathsReadable)
    }

    @Test
    fun `single readable path reports accurate fileCount`() {
        val files = listOf(
            fakeFile("/d/a.txt", lastMod = 1),
            fakeFile("/d/b.txt", lastMod = 2),
            fakeFile("/d/c.txt", lastMod = 3)
        )
        whenever(fileOps.isReadableDir("/d")).thenReturn(true)
        whenever(fileOps.listFiles("/d")).thenReturn(files)

        val r = useCase.inspect("/d")

        assertTrue(r.readable)
        assertEquals(3, r.fileCount)
        assertEquals(3, r.files.size)
        assertEquals("OK", r.message)
        assertFalse(r.truncated)
        assertEquals(1, r.pathsReadable)
    }

    @Test
    fun `more files than maxFiles truncates list`() {
        val files = (1..5).map { fakeFile("/d/f$it.txt", lastMod = it.toLong()) }
        whenever(fileOps.isReadableDir("/d")).thenReturn(true)
        whenever(fileOps.listFiles("/d")).thenReturn(files)

        val r = useCase.inspect("/d", maxFiles = 2)

        assertTrue(r.truncated)
        assertEquals(2, r.files.size)
        assertEquals(5, r.fileCount)
    }

    @Test
    fun `multi path merges and sorts newest first`() {
        val a = listOf(fakeFile("/d1/old.txt", lastMod = 10))
        val b = listOf(fakeFile("/d2/new.txt", lastMod = 99), fakeFile("/d2/mid.txt", lastMod = 50))
        whenever(fileOps.isReadableDir("/d1")).thenReturn(true)
        whenever(fileOps.isReadableDir("/d2")).thenReturn(true)
        whenever(fileOps.listFiles("/d1")).thenReturn(a)
        whenever(fileOps.listFiles("/d2")).thenReturn(b)

        val r = useCase.inspect("/d1\n/d2")

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
    fun `multi path counts readable dirs correctly`() {
        whenever(fileOps.isReadableDir("/d1")).thenReturn(true)
        whenever(fileOps.listFiles("/d1")).thenReturn(listOf(fakeFile("/d1/a.txt", lastMod = 1)))
        whenever(fileOps.isReadableDir("/d2")).thenReturn(false)
        whenever(fileOps.exists("/d2")).thenReturn(true)

        val r = useCase.inspect("/d1\n/d2")

        assertTrue(r.readable)
        assertEquals(2, r.pathsScanned)
        assertEquals(1, r.pathsReadable)
        assertEquals(1, r.fileCount)
    }

    @Test
    fun `empty directory has fileCount zero`() {
        whenever(fileOps.isReadableDir("/d")).thenReturn(true)
        whenever(fileOps.listFiles("/d")).thenReturn(emptyList())

        val r = useCase.inspect("/d")

        assertTrue(r.readable)
        assertEquals(0, r.fileCount)
        assertTrue(r.files.isEmpty())
        assertFalse(r.truncated)
    }
}
