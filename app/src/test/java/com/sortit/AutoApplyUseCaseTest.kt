package com.sortit

import com.sortit.data.TemplateEntity
import com.sortit.domain.AutoApplyUseCase
import com.sortit.domain.FakeLogDao
import com.sortit.repo.ExcludeRepository
import com.sortit.repo.FileOps
import com.sortit.repo.TemplateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/** File palsu: override method final-ish yang dipakai UseCase tanpa mockito. */
private fun testFile(
    path: String,
    size: Long = 100L,
    lastMod: Long = System.currentTimeMillis()
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

private class MapFileOps : FileOps {
    private val store = linkedMapOf<String, File>()
    val moved = mutableListOf<Pair<String, String>>()
    private val readableDirs = mutableSetOf<String>()
    private val existingDirs = mutableSetOf<String>()
    var failMove = false

    fun put(path: String, size: Long = 100L, lastMod: Long = System.currentTimeMillis()) {
        store[path] = testFile(path, size, lastMod)
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotBlank()) {
            readableDirs += parent
            existingDirs += parent
        }
    }

    fun markDir(path: String, readable: Boolean = true, exists: Boolean = true) {
        val p = path.trimEnd('/')
        if (exists) existingDirs += p
        if (readable) readableDirs += p else readableDirs -= p
    }

    override fun listFiles(dir: String): List<File> {
        val prefix = dir.trimEnd('/') + "/"
        return store.values.filter {
            val p = it.absolutePath
            p.startsWith(prefix) && !p.removePrefix(prefix).contains('/')
        }
    }

    override fun walkDeep(dir: String): Sequence<File> {
        val p = dir.trimEnd('/')
        return store.values.asSequence().filter {
            it.absolutePath == p || it.absolutePath.startsWith("$p/")
        }
    }

    override fun exists(path: String): Boolean =
        store.containsKey(path) || existingDirs.contains(path.trimEnd('/'))

    override fun size(path: String): Long = store[path]?.length() ?: 0
    override fun lastModified(path: String): Long = store[path]?.lastModified() ?: 0
    override fun mimeOf(file: File): String? = null

    override fun move(src: String, dstDir: String): String? {
        if (failMove) return null
        val f = store.remove(src) ?: return null
        moved += src to dstDir
        val dst = "$dstDir/${f.name}"
        store[dst] = testFile(dst, f.length(), f.lastModified())
        return dst
    }

    override fun mkdirs(dir: String): Boolean {
        existingDirs += dir.trimEnd('/')
        readableDirs += dir.trimEnd('/')
        return true
    }

    override fun isReadableDir(path: String): Boolean = readableDirs.contains(path.trimEnd('/'))
    override fun childCount(path: String): Int = listFiles(path).size
    override fun restore(src: String, dstDir: String): String? = move(src, dstDir)
    override fun listTrashFiles(): List<File> =
        store.values.filter { it.absolutePath.startsWith(FileOps.TRASH_ROOT) }
    override fun deleteFile(path: String): Boolean = store.remove(path) != null
}

class AutoApplyUseCaseTest {

    private val templateRepo = mock<TemplateRepository>()
    private val excludeRepo = mock<ExcludeRepository>()

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
        id = id,
        name = "r$id",
        extensions = extensions,
        targetTreeUri = targetTreeUri,
        sourceMode = sourceMode,
        sourceDirs = sourceDirs,
        enabled = enabled,
        minSizeBytes = minSizeBytes,
        maxSizeBytes = maxSizeBytes,
        maxAgeDays = maxAgeDays,
        autoEnabled = autoEnabled,
        autoAction = autoAction
    )

    private suspend fun stubNoExcludes() {
        whenever(excludeRepo.patternsForScan(any())).thenReturn(emptySet())
    }

    @Test
    fun applyAllEnabled_emptyWhenNoAutoRules() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(emptyList())
        val ops = MapFileOps()
        val log = FakeLogDao()
        val useCase = AutoApplyUseCase(ops, templateRepo, excludeRepo, log)

        val r = useCase.applyAllEnabled()

        assertEquals(AutoApplyUseCase.Result(), r)
        assertTrue(ops.moved.isEmpty())
        assertTrue(log.rows.isEmpty())
        Unit
    }

    @Test
    fun disabledRule_isSkipped() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(enabled = false)))
        val ops = MapFileOps()
        val useCase = AutoApplyUseCase(ops, templateRepo, excludeRepo, FakeLogDao())

        val r = useCase.applyAllEnabled()

        assertEquals(AutoApplyUseCase.Result(), r)
        assertTrue(ops.moved.isEmpty())
        Unit
    }

    @Test
    fun matchingFile_isMovedAndLoggedOk() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule()))
        stubNoExcludes()
        val ops = MapFileOps().also { it.put("/src/a.txt") }
        val log = FakeLogDao()
        val useCase = AutoApplyUseCase(ops, templateRepo, excludeRepo, log)

        val r = useCase.applyAllEnabled()

        assertEquals(1, r.moved)
        assertEquals(0, r.failed)
        assertEquals(listOf("/src/a.txt" to "/out/txt"), ops.moved)
        assertEquals(1, log.rows.size)
        assertEquals("OK", log.rows[0].status)
        assertEquals("/out/txt/a.txt", log.rows[0].dstPath)
        assertEquals("a.txt", log.rows[0].fileName)
        Unit
    }

    @Test
    fun fileBelowMinSize_isSkipped() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(minSizeBytes = 1000)))
        stubNoExcludes()
        val ops = MapFileOps().also { it.put("/src/a.txt", size = 10) }
        val useCase = AutoApplyUseCase(ops, templateRepo, excludeRepo, FakeLogDao())

        val r = useCase.applyAllEnabled()

        assertEquals(0, r.moved)
        assertEquals(1, r.skipped)
        assertTrue(ops.moved.isEmpty())
        Unit
    }

    @Test
    fun perRuleExcludedFile_isSkipped() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule()))
        whenever(excludeRepo.patternsForScan(any())).thenReturn(setOf("/src/skip.txt"))
        val ops = MapFileOps().also { it.put("/src/skip.txt") }
        val useCase = AutoApplyUseCase(ops, templateRepo, excludeRepo, FakeLogDao())

        val r = useCase.applyAllEnabled()

        assertEquals(0, r.moved)
        assertEquals(1, r.skipped)
        assertTrue(ops.moved.isEmpty())
        Unit
    }

    @Test
    fun systemPathRoot_isSkipped() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(sourceDirs = "/system/x")))
        stubNoExcludes()
        val ops = MapFileOps()
        val useCase = AutoApplyUseCase(ops, templateRepo, excludeRepo, FakeLogDao())

        val r = useCase.applyAllEnabled()

        assertEquals(0, r.moved)
        assertEquals(1, r.skipped)
        assertTrue(ops.moved.isEmpty())
        Unit
    }

    @Test
    fun trashAction_movesUnderTrashRoot() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(autoAction = "TRASH")))
        stubNoExcludes()
        val ops = MapFileOps().also { it.put("/src/a.txt") }
        val useCase = AutoApplyUseCase(ops, templateRepo, excludeRepo, FakeLogDao())

        val r = useCase.applyAllEnabled()

        assertEquals(1, r.trashed)
        assertEquals(0, r.moved)
        assertEquals(1, ops.moved.size)
        assertTrue(ops.moved[0].second.contains(".sortit-trash"))
        Unit
    }

    @Test
    fun failedMove_incrementsFailedAndLogsFail() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule()))
        stubNoExcludes()
        val ops = MapFileOps().also {
            it.put("/src/a.txt")
            it.failMove = true
        }
        val log = FakeLogDao()
        val useCase = AutoApplyUseCase(ops, templateRepo, excludeRepo, log)

        val r = useCase.applyAllEnabled()

        assertEquals(0, r.moved)
        assertEquals(1, r.failed)
        assertEquals(1, log.rows.size)
        assertEquals("FAIL", log.rows[0].status)
        assertEquals("", log.rows[0].dstPath)
        Unit
    }

    @Test
    fun applyForMonitorPaths_skipsFoldersWithoutIntersection() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(sourceDirs = "/other")))
        val ops = MapFileOps()
        val useCase = AutoApplyUseCase(ops, templateRepo, excludeRepo, FakeLogDao())

        val r = useCase.applyForMonitorPaths("/mon")

        assertEquals(AutoApplyUseCase.Result(), r)
        assertTrue(ops.moved.isEmpty())
        Unit
    }

    @Test
    fun applyForMonitorPaths_processesFoldersWithIntersection() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(listOf(rule(sourceDirs = "/mon/sub")))
        stubNoExcludes()
        val ops = MapFileOps().also { it.put("/mon/sub/a.txt") }
        val useCase = AutoApplyUseCase(ops, templateRepo, excludeRepo, FakeLogDao())

        val r = useCase.applyForMonitorPaths("/mon")

        assertEquals(1, r.moved)
        assertEquals(listOf("/mon/sub/a.txt" to "/out/txt"), ops.moved)
        Unit
    }

    @Test
    fun applyForMonitorPaths_allRuleOnlyScansMonitorPath() = runBlocking {
        whenever(templateRepo.getAllOnce()).thenReturn(
            listOf(rule(sourceMode = "ALL", sourceDirs = null))
        )
        stubNoExcludes()
        val mp = "/storage/emulated/0/Download"
        val ops = MapFileOps().also {
            it.put("$mp/a.txt")
            it.put("/storage/emulated/0/Other/b.txt")
            // ALL resolveRoots = /storage/emulated/0, but applyForMonitorPaths ALL
            // only uses monitor paths as roots
            it.markDir(mp, readable = true)
        }
        val useCase = AutoApplyUseCase(ops, templateRepo, excludeRepo, FakeLogDao())

        val r = useCase.applyForMonitorPaths(mp)

        assertEquals(1, r.moved)
        assertEquals(listOf("$mp/a.txt" to "/out/txt"), ops.moved)
        Unit
    }
}
