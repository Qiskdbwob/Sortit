package com.sortit.domain

import com.sortit.data.SortLogDao
import com.sortit.repo.FileOps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Eksekusi pemindahan file terpilih (sudah lewat preview gate).
// Pindah ke target, lalu ke .sortit-trash sebagai safety (log saja, tidak hapus).
class SortFilesUseCase(
    private val fileOps: FileOps,
    private val logDao: SortLogDao
) {
    data class Progress(
        val total: Int,
        val done: Int,
        val failed: Int,
        val currentPath: String
    )

    fun execute(templateId: Long, targetDir: String, items: List<FileItem>): Flow<Progress> = flow {
        var done = 0
        var failed = 0
        fileOps.mkdirs(targetDir)
        for (item in items) {
            val dst = fileOps.move(item.path, targetDir)
            if (dst != null) {
                done++
                logDao.insert(
                    com.sortit.data.SortLogEntity(
                        templateId = templateId,
                        fileName = item.name,
                        srcPath = item.path,
                        dstPath = dst,
                        status = "OK",
                        size = item.size
                    )
                )
            } else {
                failed++
                logDao.insert(
                    com.sortit.data.SortLogEntity(
                        templateId = templateId,
                        fileName = item.name,
                        srcPath = item.path,
                        dstPath = "",
                        status = "FAIL",
                        size = item.size
                    )
                )
            }
            emit(Progress(items.size, done, failed, item.path))
        }
    }
}
