package com.sortit.domain

import com.sortit.data.TemplateEntity
import com.sortit.repo.FileOps
import com.sortit.util.SystemExcludes
import com.sortit.util.matchesExtension
import com.sortit.util.parseExtensions

// Shared scan engine: resolve root paths, filter system paths,
// filter by extension, filter by exclude patterns.
// Dipakai oleh ScanSortUseCase (Flow) dan PreviewSortUseCase (List).
object FileScanner {

    data class Candidate(
        val templateId: Long,
        val item: FileItem
    )

    /** Resolve root paths dari template. */
    fun resolveRoots(template: TemplateEntity): List<String> =
        when (template.sourceMode) {
            "ALL" -> listOf("/storage/emulated/0")
            else -> (template.sourceDirs ?: "")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

    /** Scan satu template, return semua file kandidat. */
    fun scanTemplate(
        template: TemplateEntity,
        fileOps: FileOps,
        excludePatterns: Set<String>,
        seen: MutableSet<String>
    ): List<Candidate> {
        val exts = parseExtensions(template.extensions)
        if (exts.isEmpty()) return emptyList()

        val out = mutableListOf<Candidate>()
        for (root in resolveRoots(template)) {
            if (SystemExcludes.isSystemPath(root) || !fileOps.isReadableDir(root)) continue
            val seq = try { fileOps.walkDeep(root) } catch (_: Exception) { emptySequence() }
            try {
                seq.filter { !SystemExcludes.isSystemPath(it.absolutePath) }
                    .filter { matchesExtension(it.name, exts) }
                    .filter { !isExcluded(it.absolutePath, it.name, excludePatterns) }
                    .forEach { f ->
                        if (seen.add(f.absolutePath)) {
                            val mime = fileOps.mimeOf(f)
                            out.add(
                                Candidate(
                                    templateId = template.id,
                                    item = FileItem(
                                        path = f.absolutePath,
                                        name = f.name,
                                        size = f.length(),
                                        mimeType = mime,
                                        lastModified = f.lastModified(),
                                        isMedia = mime?.startsWith("image/") == true ||
                                                mime?.startsWith("video/") == true
                                    )
                                )
                            )
                        }
                    }
            } catch (_: Exception) {
                // Folder hilang/permission berubah: skip root ini
            }
        }
        return out
    }

    private fun isExcluded(path: String, name: String, patterns: Set<String>): Boolean =
        patterns.any { path.startsWith(it) || name == it }
}
