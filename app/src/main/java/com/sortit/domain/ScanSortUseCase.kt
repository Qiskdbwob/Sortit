package com.sortit.domain

import com.sortit.repo.FileOps
import com.sortit.util.SystemExcludes
import com.sortit.util.matchesExtension
import com.sortit.util.parseExtensions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

// Deep recursive scan by ekstensi. Mode ALL = scan root storage dengan
// exclude path sistem. Mode FOLDERS = scan folder pilihan saja.
// Emit progress bertahap (chunked) supaya UI tidak freeze.
class ScanSortUseCase(private val fileOps: FileOps) {

    data class Progress(
        val scanned: Int,
        val found: Int,
        val currentPath: String
    )

    fun execute(
        template: com.sortit.data.TemplateEntity,
        excludePatterns: Set<String>
    ): Flow<Progress> = flow {
        val exts = parseExtensions(template.extensions)
        val roots: List<String> = when (template.sourceMode) {
            "ALL" -> listOf("/storage/emulated/0")
            else -> (template.sourceDirs ?: "").split(',').map { it.trim() }.filter { it.isNotBlank() }
        }

        var scanned = 0
        var found = 0
        for (root in roots) {
            if (SystemExcludes.isSystemPath(root)) continue
            fileOps.walkDeep(root)
                .filter { it.isFile }
                .filter { !SystemExcludes.isSystemPath(it.absolutePath) }
                .filter { matchesExtension(it.name, exts) }
                .filter { !isExcluded(it.absolutePath, it.name, excludePatterns) }
                .forEach { f ->
                    scanned++
                    found++
                    emit(Progress(scanned, found, f.absolutePath))
                }
        }
    }

    private fun isExcluded(path: String, name: String, patterns: Set<String>): Boolean =
        patterns.any { path.startsWith(it) || name == it }
}
