package com.sortit.domain

import com.sortit.repo.FileOps
import com.sortit.util.SystemExcludes
import com.sortit.util.splitMonitorPaths

/**
 * Monitor: list file di 1+ path (multi-folder digabung newline/|).
 * Performa: cap [maxFiles], sort terbaru dulu, skip subdir berat.
 */
class ScanPathUseCase(private val fileOps: FileOps) {

    data class PathInspection(
        val path: String,
        val exists: Boolean,
        val isDirectory: Boolean,
        val readable: Boolean,
        val fileCount: Int,
        val message: String,
        val files: List<FileItem>,
        /** true kalau list dipotong karena cap */
        val truncated: Boolean = false,
        val pathsScanned: Int = 1,
        val pathsReadable: Int = 0
    )

    fun list(path: String, maxFiles: Int = DEFAULT_MAX): List<FileItem> =
        inspect(path, maxFiles).files

    fun inspect(path: String, maxFiles: Int = DEFAULT_MAX): PathInspection {
        val paths = splitMonitorPaths(path)
        if (paths.isEmpty()) {
            return PathInspection(
                path = path,
                exists = false,
                isDirectory = false,
                readable = false,
                fileCount = 0,
                message = "Path kosong",
                files = emptyList(),
                pathsScanned = 0,
                pathsReadable = 0
            )
        }

        // Single path: keep old semantics for system exclude
        if (paths.size == 1) {
            return inspectOne(paths[0], maxFiles)
        }

        val collected = ArrayList<FileItem>(minOf(maxFiles, 256))
        var readableCount = 0
        var totalSeen = 0
        var anyExists = false
        var hitCap = false

        for (p in paths) {
            if (SystemExcludes.isSystemPath(p)) continue
            if (!fileOps.isReadableDir(p)) {
                if (fileOps.exists(p)) anyExists = true
                continue
            }
            anyExists = true
            readableCount++
            val listed = fileOps.listFiles(p)
            // Sort by lastModified desc before mapping to prefer newest under cap
            val filesOnly = listed.asSequence()
                .filter { it.isFile }
                .sortedByDescending { it.lastModified() }
            for (f in filesOnly) {
                totalSeen++
                if (collected.size >= maxFiles) {
                    hitCap = true
                    break
                }
                val mime = fileOps.mimeOf(f)
                collected.add(
                    FileItem(
                        path = f.absolutePath,
                        name = f.name,
                        size = f.length(),
                        mimeType = mime,
                        lastModified = f.lastModified(),
                        isMedia = mime?.startsWith("image/") == true || mime?.startsWith("video/") == true
                    )
                )
            }
            if (hitCap) break
        }

        // Final sort newest first across folders
        collected.sortByDescending { it.lastModified }

        val readable = readableCount > 0
        val msg = when {
            !readable && !anyExists -> "Path tidak ditemukan"
            !readable -> "Path tidak readable"
            hitCap -> "OK (tampil $maxFiles terbaru dari $totalSeen+)"
            else -> "OK"
        }
        return PathInspection(
            path = path,
            exists = anyExists || readable,
            isDirectory = true,
            readable = readable,
            fileCount = if (hitCap) totalSeen else collected.size,
            message = msg,
            files = collected,
            truncated = hitCap,
            pathsScanned = paths.size,
            pathsReadable = readableCount
        )
    }

    private fun inspectOne(path: String, maxFiles: Int): PathInspection {
        if (SystemExcludes.isSystemPath(path)) {
            return PathInspection(
                path, exists = true, isDirectory = true, readable = false,
                fileCount = 0, message = "Path sistem dikecualikan", files = emptyList(),
                pathsScanned = 1, pathsReadable = 0
            )
        }
        val readable = fileOps.isReadableDir(path)
        if (!readable) {
            return PathInspection(
                path,
                exists = fileOps.exists(path),
                isDirectory = false,
                readable = false,
                fileCount = 0,
                message = "Path tidak ditemukan atau tidak readable",
                files = emptyList(),
                pathsScanned = 1,
                pathsReadable = 0
            )
        }

        val listed = fileOps.listFiles(path)
        // Prefer newest; cap early for huge folders (WA Images can be thousands)
        val sorted = listed.asSequence()
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }

        val files = ArrayList<FileItem>(minOf(maxFiles, 128))
        var total = 0
        var hitCap = false
        for (f in sorted) {
            total++
            if (files.size >= maxFiles) {
                hitCap = true
                // still count remaining cheaply? listFiles already loaded; count rest
                // total already counts current; add remaining estimate from sequence not possible after break
                // recount: we already sorted full list — use listed count of files
                break
            }
            val mime = fileOps.mimeOf(f)
            files.add(
                FileItem(
                    path = f.absolutePath,
                    name = f.name,
                    size = f.length(),
                    mimeType = mime,
                    lastModified = f.lastModified(),
                    isMedia = mime?.startsWith("image/") == true || mime?.startsWith("video/") == true
                )
            )
        }
        if (hitCap) {
            // accurate total file count without re-mime
            total = listed.count { it.isFile }
        } else {
            total = files.size
        }

        return PathInspection(
            path = path,
            exists = true,
            isDirectory = true,
            readable = true,
            fileCount = total,
            message = if (hitCap) "OK (tampil $maxFiles terbaru dari $total)" else "OK",
            files = files,
            truncated = hitCap,
            pathsScanned = 1,
            pathsReadable = 1
        )
    }

    companion object {
        /** Cap default: cukup buat preview monitor, hindari OOM di folder WA besar. */
        const val DEFAULT_MAX = 200
        const val PREVIEW_UI = 40
    }
}
