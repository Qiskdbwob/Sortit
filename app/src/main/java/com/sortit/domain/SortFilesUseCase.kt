package com.sortit.domain

import com.sortit.data.SortLogDao
import com.sortit.repo.FileOps
import com.sortit.util.extensionFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

// Eksekusi pemindahan file terpilih (sudah lewat preview gate).
// MOVE = pindah LANGSUNG ke targetDir (/Download/1.txt, tanpa subfolder ekstensi).
// TRASH = pindah ke .sortit-trash/<ekstensi>/ (dikelompokkan supaya retensi mudah).
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
            // MOVE: user pilih /Download → hasil /Download/1.txt (flat, tanpa folder ekstensi).
            // TRASH: tetap .sortit-trash/txt/... agar pembersihan & retensi per folder.
            val dstDir = if (action == Action.TRASH) "$baseDir/${extensionFolder(item.name)}" else baseDir
            fileOps.mkdirs(dstDir)

            val dst = fileOps.move(item.path, dstDir)
            if (dst != null) {
                done++
                // Dedup: hapus log lama yang menunjuk ke lokasi sumber ini
                // (file sudah dipindah lagi), supaya riwayat tidak punya entri hantu.
                logDao.deleteByDstPath(item.path)
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
