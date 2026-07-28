package com.sortit.domain

import com.sortit.data.SortLogDao
import com.sortit.data.SortLogEntity
import com.sortit.repo.FileOps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FakeLogDao : SortLogDao {
    val rows = mutableListOf<SortLogEntity>()
    override suspend fun insert(l: SortLogEntity): Long {
        rows.add(l)
        return rows.size.toLong()
    }
    override suspend fun delete(l: SortLogEntity) { rows.remove(l) }
    override suspend fun deleteIds(ids: List<Long>) { rows.removeAll { it.id in ids } }
    override suspend fun recent(templateId: Long, limit: Int): List<SortLogEntity> = rows
    override fun observeRecent(limit: Int): Flow<List<SortLogEntity>> = flowOf(rows.toList())
    override suspend fun listTrashed(limit: Int): List<SortLogEntity> =
        rows.filter { it.status == "OK" && it.dstPath.contains("/.sortit-trash/") }
    override suspend fun listMoved(limit: Int): List<SortLogEntity> =
        rows.filter { it.status == "OK" && !it.dstPath.contains("/.sortit-trash/") }
    override suspend fun listOk(limit: Int): List<SortLogEntity> =
        rows.filter { it.status == "OK" }
    override fun observeMovedCount(): Flow<Int> = flowOf(rows.count { it.status == "OK" && !it.dstPath.contains("/.sortit-trash/") })
    override fun observeTrashedCount(): Flow<Int> = flowOf(rows.count { it.status == "OK" && it.dstPath.contains("/.sortit-trash/") })
    override fun observeFailedCount(): Flow<Int> = flowOf(rows.count { it.status == "FAIL" })
    override suspend fun deleteOlderThan(cutoff: Long) { }
}

private fun ops(moveResult: (String, String) -> String?): FileOps = object : FileOps {
    override fun listFiles(dir: String): List<File> = emptyList()
    override fun walkDeep(dir: String): Sequence<File> = emptySequence()
    override fun exists(path: String): Boolean = true
    override fun size(path: String): Long = 10
    override fun lastModified(path: String): Long = 0
    override fun mimeOf(file: File): String? = null
    override fun move(src: String, dstDir: String): String? = moveResult(src, dstDir)
    override fun moveToTrash(src: String): String? = moveResult(src, FileOps.TRASH_ROOT)
    override fun mkdirs(dir: String): Boolean = true
    override fun isReadableDir(path: String): Boolean = true
    override fun childCount(path: String): Int = 0
    override fun restore(src: String, dstDir: String): String? = move(src, dstDir)
    override fun listTrashFiles(): List<File> = emptyList()
    override fun deleteFile(path: String): Boolean = true
}

class SortFilesUseCaseTest {
    @Test
    fun `moves selected and logs OK`() = runBlocking {
        val log = FakeLogDao()
        val use = SortFilesUseCase(ops { src, dst -> "$dst/${File(src).name}" }, log)
        val items = listOf(FileItem("/a/1.txt", "1.txt", 10, null, 0, false))
        var done = 0
        use.execute(1, "/out", items).collect { done = it.done }
        assertEquals(1, done)
        assertEquals(1, log.rows.count { it.status == "OK" })
    }

    @Test
    fun `failed move logs FAIL`() = runBlocking {
        val log = FakeLogDao()
        val use = SortFilesUseCase(ops { _, _ -> null }, log)
        val items = listOf(FileItem("/a/1.txt", "1.txt", 10, null, 0, false))
        var failed = 0
        use.execute(1, "/out", items).collect { failed = it.failed }
        assertEquals(1, failed)
        assertEquals(1, log.rows.count { it.status == "FAIL" })
    }

    @Test
    fun `trash action uses trash root`() = runBlocking {
        val log = FakeLogDao()
        val use = SortFilesUseCase(ops { src, dst -> "$dst/${File(src).name}" }, log)
        val items = listOf(FileItem("/a/1.txt", "1.txt", 10, null, 0, false))
        var done = 0
        use.execute(1, "/out", items, SortFilesUseCase.Action.TRASH).collect { done = it.done }
        assertEquals(1, done)
        assertEquals(1, log.rows.count { it.dstPath.startsWith(FileOps.TRASH_ROOT) })
    }
}
