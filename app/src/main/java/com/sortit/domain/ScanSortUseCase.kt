package com.sortit.domain

import com.sortit.data.TemplateEntity
import com.sortit.repo.FileOps
import com.sortit.util.SystemExcludes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

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
        val now = System.currentTimeMillis()

        for (template in templates.filter { it.enabled }) {
            val exts = com.sortit.util.parseExtensions(template.extensions)
            if (exts.isEmpty()) continue

            for (root in FileScanner.resolveRoots(template)) {
                if (SystemExcludes.isSystemPath(root) ||
                    FileScanner.isRootExcluded(root, excludePatterns) ||
                    !fileOps.isReadableDir(root)
                ) continue
                val seq = try { fileOps.walkDeep(root) } catch (_: Exception) { emptySequence() }
                try {
                    seq.forEach { f ->
                        scanned++
                        val path = f.absolutePath
                        val size = f.length()
                        val lm = f.lastModified()
                        val matches = !SystemExcludes.isSystemPath(path) &&
                                com.sortit.util.matchesExtension(f.name, exts) &&
                                !FileScanner.isExcluded(path, f.name, excludePatterns) &&
                                FileScanner.matchesMeta(template, size, lm, now) &&
                                seen.add(path)
                        if (matches) {
                            found++
                            val mime = fileOps.mimeOf(f)
                            onCandidate(
                                template.id,
                                FileItem(
                                    path = path,
                                    name = f.name,
                                    size = size,
                                    mimeType = mime,
                                    lastModified = lm,
                                    isMedia = mime?.startsWith("image/") == true || mime?.startsWith("video/") == true
                                )
                            )
                            emit(Progress(scanned, found, path))
                        } else {
                            emit(Progress(scanned, found, path))
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        emit(Progress(scanned, found, ""))
    }.flowOn(Dispatchers.IO)
}
