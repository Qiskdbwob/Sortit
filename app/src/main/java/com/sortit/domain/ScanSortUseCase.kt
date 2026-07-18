package com.sortit.domain

import com.sortit.data.TemplateEntity
import com.sortit.repo.FileOps
import com.sortit.util.SystemExcludes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Deep recursive scan by ekstensi. Emit progress bertahap supaya UI tidak freeze.
// Menggunakan FileScanner shared engine untuk filter logic.
class ScanSortUseCase(private val fileOps: FileOps) {

    data class Progress(
        val scanned: Int,
        val found: Int,
        val currentPath: String
    )

    fun execute(
        template: TemplateEntity,
        excludePatterns: Set<String>,
        onCandidate: suspend (templateId: Long, item: FileItem) -> Unit = { _, _ -> }
    ): Flow<Progress> = executeMany(listOf(template), excludePatterns, onCandidate)

    fun executeMany(
        templates: List<TemplateEntity>,
        excludePatterns: Set<String>,
        onCandidate: suspend (templateId: Long, item: FileItem) -> Unit = { _, _ -> }
    ): Flow<Progress> = flow {
        var scanned = 0
        var found = 0
        val seen = mutableSetOf<String>()

        for (template in templates.filter { it.enabled }) {
            val exts = com.sortit.util.parseExtensions(template.extensions)
            if (exts.isEmpty()) continue

            for (root in FileScanner.resolveRoots(template)) {
                if (SystemExcludes.isSystemPath(root) || !fileOps.isReadableDir(root)) continue
                val seq = try { fileOps.walkDeep(root) } catch (_: Exception) { emptySequence() }
                try {
                    seq.forEach { f ->
                        scanned++
                        val path = f.absolutePath
                        val matches = !SystemExcludes.isSystemPath(path) &&
                                com.sortit.util.matchesExtension(f.name, exts) &&
                                !isExcluded(path, f.name, excludePatterns) &&
                                seen.add(path)
                        if (matches) {
                            found++
                            val mime = fileOps.mimeOf(f)
                            onCandidate(
                                template.id,
                                FileItem(
                                    path = path,
                                    name = f.name,
                                    size = f.length(),
                                    mimeType = mime,
                                    lastModified = f.lastModified(),
                                    isMedia = mime?.startsWith("image/") == true || mime?.startsWith("video/") == true
                                )
                            )
                            emit(Progress(scanned, found, path))
                        } else if (scanned % 50 == 0) {
                            emit(Progress(scanned, found, path))
                        }
                    }
                } catch (_: Exception) {
                    // Folder hilang/permission berubah: skip root ini
                }
            }
        }
        emit(Progress(scanned, found, ""))
    }

    private fun isExcluded(path: String, name: String, patterns: Set<String>): Boolean =
        patterns.any { path.startsWith(it) || name == it }
}
