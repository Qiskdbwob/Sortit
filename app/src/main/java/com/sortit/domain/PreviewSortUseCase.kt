package com.sortit.domain

import com.sortit.repo.FileOps
import com.sortit.util.SystemExcludes
import com.sortit.util.matchesExtension
import com.sortit.util.parseExtensions
import java.io.File

// Hasilkan list kandidat (tanpa flow) untuk preview gate.
class PreviewSortUseCase(private val fileOps: FileOps) {

    fun collect(template: com.sortit.data.TemplateEntity, excludePatterns: Set<String>): List<FileItem> {
        val exts = parseExtensions(template.extensions)
        val roots: List<String> = when (template.sourceMode) {
            "ALL" -> listOf("/storage/emulated/0")
            else -> (template.sourceDirs ?: "").split(',').map { it.trim() }.filter { it.isNotBlank() }
        }
        val out = mutableListOf<FileItem>()
        for (root in roots) {
            if (SystemExcludes.isSystemPath(root)) continue
            fileOps.walkDeep(root)
                .filter { it.isFile }
                .filter { !SystemExcludes.isSystemPath(it.absolutePath) }
                .filter { matchesExtension(it.name, exts) }
                .filter { !isExcluded(it.absolutePath, it.name, excludePatterns) }
                .forEach { f ->
                    out.add(
                        FileItem(
                            path = f.absolutePath,
                            name = f.name,
                            size = f.length(),
                            mimeType = fileOps.mimeOf(f),
                            lastModified = f.lastModified(),
                            isMedia = fileOps.mimeOf(f)?.startsWith("image/") == true
                                    || fileOps.mimeOf(f)?.startsWith("video/") == true
                        )
                    )
                }
        }
        return out
    }

    private fun isExcluded(path: String, name: String, patterns: Set<String>): Boolean =
        patterns.any { path.startsWith(it) || name == it }
}
