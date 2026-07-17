package com.sortit.domain

import com.sortit.data.TemplateEntity
import com.sortit.repo.FileOps
import com.sortit.util.SystemExcludes
import com.sortit.util.matchesExtension
import com.sortit.util.parseExtensions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Deep recursive scan by ekstensi. Mode ALL = scan root storage dengan
// exclude path sistem. Mode FOLDERS = scan folder pilihan saja.
// Emit progress bertahap (chunked) supaya UI tidak freeze.
class ScanSortUseCase(private val fileOps: FileOps) {

    data class ScannedFile(
        val templateId: Long,
        val item: FileItem
    )

    data class Progress(
        val scanned: Int,
        val found: Int,
        val currentPath: String,
        val errors: List<String> = emptyList()
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
        val errors = mutableListOf<String>()
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
                    seq.forEach { f ->
                        scanned++
                        val path = f.absolutePath
                        val matches = fileOps.exists(path) &&
                                !SystemExcludes.isSystemPath(path) &&
                                matchesExtension(f.name, exts) &&
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
                } catch (e: Exception) {
                    errors.add("Gagal scan $root: ${e.message ?: "unknown error"}")
                }
            }
        }
        emit(Progress(scanned, found, "", errors.toList()))
    }

    private fun isExcluded(path: String, name: String, patterns: Set<String>): Boolean =
        patterns.any { path.startsWith(it) || name == it }
}
