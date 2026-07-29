package com.sortit.domain

import com.sortit.data.SortLogDao
import com.sortit.repo.FileOps
import com.sortit.util.extensionFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

// Eksekusi pemindahan file terpilih (sudah lewat preview gate).
// MOVE = pindah ke <targetDir>/<ekstensi>/; TRASH = pindah ke .sortit-trash/<ekstensi>/
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
        val baseDir = if (action == Action.MOVE) targetDir else FileOps.TRASH_ROOT
        fileOps.mkdirs(baseDir)

        for (item in items) {
            val subDir = "$baseDir/${extensionFolder(item.name)}"
            fileOps.mkdirs(subDir)

            val dst = when (action) {
                Action.MOVE -> fileOps.move(item.path, subDir)
                Action.TRASH -> fileOps.move(item.path, subDir)
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
    }.flowOn(Dispatchers.IO)
}
