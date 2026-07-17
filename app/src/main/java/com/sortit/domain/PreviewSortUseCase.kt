package com.sortit.domain

import com.sortit.data.TemplateEntity
import com.sortit.repo.FileOps
import com.sortit.util.SystemExcludes
import com.sortit.util.matchesExtension
import com.sortit.util.parseExtensions

// Hasilkan list kandidat untuk preview gate. collectMany dipakai saat user
// memilih beberapa rule untuk discan bersamaan.
class PreviewSortUseCase(private val fileOps: FileOps) {

    data class ScannedFile(
        val templateId: Long,
        val item: FileItem
    )

    fun collect(template: TemplateEntity, excludePatterns: Set<String>): List<FileItem> =
        collectMany(listOf(template), excludePatterns).map { it.item }

    fun collectMany(
        templates: List<TemplateEntity>,
        excludePatterns: Set<String>
    ): List<ScannedFile> {
        val out = mutableListOf<ScannedFile>()
        val seen = mutableSetOf<String>()
        for (template in templates.filter { it.enabled }) {
            val exts = parseExtensions(template.extensions)
            if (exts.isEmpty()) continue
            val roots: List<String> = when (template.sourceMode) {
                "ALL" -> listOf("/storage/emulated/0")
                else -> (template.sourceDirs ?: "")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
            for (root in roots) {
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
                                    ScannedFile(
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
                    // Lanjut template/root berikutnya bila satu root bermasalah.
                }
            }
        }
        return out.sortedWith(compareByDescending<ScannedFile> { it.item.isMedia }.thenBy { it.item.name.lowercase() })
    }

    private fun isExcluded(path: String, name: String, patterns: Set<String>): Boolean =
        patterns.any { path.startsWith(it) || name == it }
}
