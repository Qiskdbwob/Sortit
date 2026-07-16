package com.sortit.domain

import com.sortit.repo.FileOps
import java.io.File

// Monitor: list file di 1 path (realtime di-handle UI via FileObserver).
class ScanPathUseCase(private val fileOps: FileOps) {
    fun list(path: String): List<FileItem> {
        if (SystemExcludes.isSystemPath(path)) return emptyList()
        return fileOps.listFiles(path)
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
    }
}
