package com.sortit.domain

import com.sortit.repo.FileOps
import com.sortit.util.SystemExcludes

// Monitor: list file di 1 path + status readable. Realtime di-handle UI via FileObserver.
class ScanPathUseCase(private val fileOps: FileOps) {

    data class PathInspection(
        val path: String,
        val exists: Boolean,
        val isDirectory: Boolean,
        val readable: Boolean,
        val fileCount: Int,
        val message: String,
        val files: List<FileItem>
    )

    fun list(path: String): List<FileItem> = inspect(path).files

    fun inspect(path: String): PathInspection {
        if (SystemExcludes.isSystemPath(path)) {
            return PathInspection(path, exists = true, isDirectory = true, readable = false, fileCount = 0, message = "Path sistem dikecualikan", files = emptyList())
        }
        val readable = fileOps.isReadableDir(path)
        if (!readable) {
            return PathInspection(path, exists = fileOps.exists(path), isDirectory = readable, readable = false, fileCount = 0, message = "Path tidak ditemukan atau tidak readable", files = emptyList())
        }
        val files = fileOps.listFiles(path)
            .filter { it.isFile }
            .map { f ->
                val mime = fileOps.mimeOf(f)
                FileItem(
                    path = f.absolutePath,
                    name = f.name,
                    size = f.length(),
                    mimeType = mime,
                    lastModified = f.lastModified(),
                    isMedia = mime?.startsWith("image/") == true || mime?.startsWith("video/") == true
                )
            }
            .sortedBy { it.name.lowercase() }
        return PathInspection(path, exists = true, isDirectory = true, readable = true, fileCount = files.size, message = "OK", files = files)
    }
}
