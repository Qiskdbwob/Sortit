package com.sortit

import com.sortit.data.SortLogDao
import com.sortit.data.SortLogEntity
import com.sortit.data.TemplateEntity
import com.sortit.domain.AutoApplyUseCase
import com.sortit.repo.ExcludeRepository
import com.sortit.repo.FileOps
import com.sortit.repo.TemplateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.File

class AutoApplyUseCaseTest {

    private val fileOps = mock<FileOps>()
    private val templateRepo = mock<TemplateRepository>()
    private val excludeRepo = mock<ExcludeRepository>()
    private val logDao = mock<SortLogDao>()
    private val useCase = AutoApplyUseCase(fileOps, templateRepo, excludeRepo, logDao)

    private fun rule(
        id: Long = 1L,
        enabled: Boolean = true,
        autoEnabled: Boolean = true,
        autoAction: String = "MOVE",
        sourceMode: String = "FOLDERS",
        sourceDirs: String? = "/src",
        extensions: String = "txt",
        targetTreeUri: String = "/out",
        minSizeBytes: Long = 0L,
        maxSizeBytes: Long = 0L,
        maxAgeDays: Int = 0
    ) = TemplateEntity(
        id = id, name = "r$id", extensions = extensions, targetTreeUri = targetTreeUri,
        sourceMode = sourceMode, sourceDirs = sourceDirs, enabled = enabled,
        minSizeBytes = minSizeBytes, maxSizeBytes = maxSizeBytes, maxAgeDays = maxAgeDays,
        autoEnabled = autoEnabled, autoAction = autoAction
    )

    private fun fakeFile(path: String, size: Long = 100L, lastMod: Long = System.currentTimeMillis()): File {
        val f = mock<File>()
        whenever(f.absolutePath).thenReturn(path)
        whenever(f.name).thenReturn(path.substringAfterLast('/'))
        whenever(f.isFile).thenReturn(true)
        whenever(f.length()).thenReturn(size)
        whenever(f.lastModified()).thenReturn(lastMod)
        return f
    }

    private suspend fun stubNoExcludes() {
        whenever(excludeRepo.patternsForScan(any())).thenReturn(emptySet())
    }

    @Test
    fun `applyAllEnabled returns empty result when no auto rules`() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(emptyList())

        val r = useCase.applyAllEnabled()

        assertEquals(AutoApplyUseCase.Result(), r)
        verifyNoInteractions(fileOps)
        verifyNoInteractions(logDao)
    }

    @Test
    fun `rule with autoEnabled but disabled is skipped`() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(enabled = false)))

        val r = useCase.applyAllEnabled()

        assertEquals(AutoApplyUseCase.Result(), r)
        verifyNoInteractions(fileOps)
    }

    @Test
    fun `matching file is moved and logged OK`() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule()))
        stubNoExcludes()
        whenever(fileOps.isReadableDir("/src")).thenReturn(true)
        whenever(fileOps.listFiles("/src")).thenReturn(listOf(fakeFile("/src/a.txt")))
        whenever(fileOps.move(eq("/src/a.txt"), eq("/out/txt"))).thenReturn("/out/txt/a.txt")

        val r = useCase.applyAllEnabled()

        assertEquals(1, r.moved)
        assertEquals(0, r.failed)
        verify(fileOps).move("/src/a.txt", "/out/txt")
        val log = argumentCaptor<SortLogEntity>()
        verify(logDao).insert(log.capture())
        assertEquals("OK", log.firstValue.status)
        assertEquals("/out/txt/a.txt", log.firstValue.dstPath)
        assertEquals("a.txt", log.firstValue.fileName)
    }

    @Test
    fun `file below minSize is skipped`() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(minSizeBytes = 1000)))
        stubNoExcludes()
        whenever(fileOps.isReadableDir("/src")).thenReturn(true)
        whenever(fileOps.listFiles("/src")).thenReturn(listOf(fakeFile("/src/a.txt", size = 10)))

        val r = useCase.applyAllEnabled()

        assertEquals(0, r.moved)
        assertEquals(1, r.skipped)
        verify(fileOps, never()).move(any(), any())
    }

    @Test
    fun `per-rule excluded file is skipped`() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule()))
        whenever(excludeRepo.patternsForScan(any())).thenReturn(setOf("/src/skip.txt"))
        whenever(fileOps.isReadableDir("/src")).thenReturn(true)
        whenever(fileOps.listFiles("/src")).thenReturn(listOf(fakeFile("/src/skip.txt")))

        val r = useCase.applyAllEnabled()

        assertEquals(0, r.moved)
        assertEquals(1, r.skipped)
        verify(fileOps, never()).move(any(), any())
    }

    @Test
    fun `system path root is skipped`() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(sourceDirs = "/system/x")))
        stubNoExcludes()

        val r = useCase.applyAllEnabled()

        assertEquals(0, r.moved)
        assertEquals(1, r.skipped)
        verify(fileOps, never()).move(any(), any())
    }

    @Test
    fun `TRASH action moves file under trash root`() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(autoAction = "TRASH")))
        stubNoExcludes()
        whenever(fileOps.isReadableDir("/src")).thenReturn(true)
        whenever(fileOps.listFiles("/src")).thenReturn(listOf(fakeFile("/src/a.txt")))
        whenever(fileOps.move(eq("/src/a.txt"), eq("${FileOps.TRASH_ROOT}/txt")))
            .thenReturn("${FileOps.TRASH_ROOT}/txt/a.txt")

        val r = useCase.applyAllEnabled()

        assertEquals(1, r.trashed)
        assertEquals(0, r.moved)
        val dstDir = argumentCaptor<String>()
        verify(fileOps).move(eq("/src/a.txt"), dstDir.capture())
        assertTrue(dstDir.firstValue.contains(".sortit-trash"))
    }

    @Test
    fun `failed move increments failed and logs FAIL`() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule()))
        stubNoExcludes()
        whenever(fileOps.isReadableDir("/src")).thenReturn(true)
        whenever(fileOps.listFiles("/src")).thenReturn(listOf(fakeFile("/src/a.txt")))
        whenever(fileOps.move(any(), any())).thenReturn(null)

        val r = useCase.applyAllEnabled()

        assertEquals(0, r.moved)
        assertEquals(1, r.failed)
        val log = argumentCaptor<SortLogEntity>()
        verify(logDao).insert(log.capture())
        assertEquals("FAIL", log.firstValue.status)
        assertEquals("", log.firstValue.dstPath)
    }

    @Test
    fun `applyForMonitorPaths skips FOLDERS rule without path intersection`() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(sourceDirs = "/other")))

        val r = useCase.applyForMonitorPaths("/mon")

        assertEquals(AutoApplyUseCase.Result(), r)
        verifyNoInteractions(fileOps)
    }

    @Test
    fun `applyForMonitorPaths processes FOLDERS rule with intersection`() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(sourceDirs = "/mon/sub")))
        stubNoExcludes()
        whenever(fileOps.isReadableDir("/mon/sub")).thenReturn(true)
        whenever(fileOps.listFiles("/mon/sub")).thenReturn(listOf(fakeFile("/mon/sub/a.txt")))
        whenever(fileOps.move(eq("/mon/sub/a.txt"), eq("/out/txt"))).thenReturn("/out/txt/a.txt")

        val r = useCase.applyForMonitorPaths("/mon")

        assertEquals(1, r.moved)
        verify(fileOps).move("/mon/sub/a.txt", "/out/txt")
    }

    @Test
    fun `applyForMonitorPaths ALL rule only scans monitor path not storage root`() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(sourceMode = "ALL", sourceDirs = null)))
        stubNoExcludes()
        val mp = "/storage/emulated/0/Download"
        whenever(fileOps.isReadableDir(mp)).thenReturn(true)
        whenever(fileOps.listFiles(mp)).thenReturn(listOf(fakeFile("$mp/a.txt")))
        whenever(fileOps.move(eq("$mp/a.txt"), eq("/out/txt"))).thenReturn("/out/txt/a.txt")

        val r = useCase.applyForMonitorPaths(mp)

        assertEquals(1, r.moved)
        verify(fileOps).listFiles(mp)
        verify(fileOps, never()).listFiles("/storage/emulated/0")
    }
}
