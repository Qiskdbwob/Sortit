package com.sortit.domain

import com.sortit.data.SortLogDao
import com.sortit.data.SortLogEntity
import com.sortit.repo.FileOps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FakeLogDao : SortLogDao {
    val rows = mutableListOf<SortLogEntity>()
    override suspend fun insert(l: SortLogEntity) { rows.add(l) }
    override suspend fun recent(templateId: Long, limit: Int): List<SortLogEntity> = rows
}

class SortFilesUseCaseTest {
    @Test
    fun `moves selected and logs OK`() = runBlocking {
        val ops = object : FileOps {
            val moved = mutableListOf<String>()
            override fun listFiles(dir: String): List<File> = emptyList()
            override fun walkDeep(dir: String): Sequence<File> = emptySequence()
            override fun exists(path: String): Boolean = true
            override fun size(path: String): Long = 10
            override fun lastModified(path: String): Long = 0
            override fun mimeOf(file: File): String? = null
            override fun move(src: String, dstDir: String): String? { moved.add(src); return "$dstDir/${File(src).name}" }
            override fun mkdirs(dir: String): Boolean = true
        }
        val log = FakeLogDao()
        val use = SortFilesUseCase(ops, log)
        val items = listOf(FileItem("/a/1.txt", "1.txt", 10, null, 0, false))
        var done = 0
        use.execute(1, "/out", items).collect { done = it.done }
        assertEquals(1, done)
        assertEquals(1, log.rows.count { it.status == "OK" })
    }

    @Test
    fun `failed move logs FAIL`() = runBlocking {
        val ops = object : FileOps {
            override fun listFiles(dir: String): List<File> = emptyList()
            override fun walkDeep(dir: String): Sequence<File> = emptySequence()
            override fun exists(path: String): Boolean = false
            override fun size(path: String): Long = 0
            override fun lastModified(path: String): Long = 0
            override fun mimeOf(file: File): String? = null
            override fun move(src: String, dstDir: String): String? = null // gagal
            override fun mkdirs(dir: String): Boolean = true
        }
        val log = FakeLogDao()
        val use = SortFilesUseCase(ops, log)
        val items = listOf(FileItem("/a/1.txt", "1.txt", 10, null, 0, false))
        var failed = 0
        use.execute(1, "/out", items).collect { failed = it.failed }
        assertEquals(1, failed)
        assertEquals(1, log.rows.count { it.status == "FAIL" })
    }
}
