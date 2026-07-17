package com.sortit.domain

import com.sortit.data.SortLogDao
import com.sortit.repo.FileOps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Eksekusi pemindahan file terpilih (sudah lewat preview gate).
// Aksi aman v1: MOVE = pindah ke folder target rule; TRASH = pindah ke .sortit-trash.
// Tidak ada hapus permanen di v1.
class SortFilesUseCase(
    private val fileOps: FileOps,
    private val logDao: SortLogDao
) {
    enum class Action { MOVE, TRASH }

    data class Progress(
        val total: Int,
        val done: Int,
        val failed: Int,
        val currentPath: String
    )

    fun execute(
        templateId: Long,
        targetDir: String,
        items: List<FileItem>,
        action: Action = Action.MOVE,
        onItemResult: suspend (srcPath: String, dstPath: String?, status: String) -> Unit = { _, _, _ -> }
    ): Flow<Progress> = flow {
        var done = 0
        var failed = 0
        if (action == Action.MOVE) fileOps.mkdirs(targetDir) else fileOps.mkdirs(FileOps.TRASH_ROOT)

        for (item in items) {
            val dst = when (action) {
                Action.MOVE -> fileOps.move(item.path, targetDir)
                Action.TRASH -> fileOps.moveToTrash(item.path)
            }
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
                onItemResult(item.path, dst, "OK")
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
                onItemResult(item.path, null, "FAIL")
            }
            emit(Progress(items.size, done, failed, item.path))
        }
    }
}
