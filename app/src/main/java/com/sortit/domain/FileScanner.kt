package com.sortit.domain

import com.sortit.data.TemplateEntity
import com.sortit.repo.FileOps
import com.sortit.util.SystemExcludes
import com.sortit.util.matchesExtension
import com.sortit.util.parseExtensions

// Shared scan engine: resolve root paths, filter system paths,
// filter by extension, filter by exclude patterns (global + per-rule).
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

    /**
     * Pattern match:
     * - path prefix (folder + anak): /a/b cocok /a/b dan /a/b/c
     * - exact file name: "skip.txt"
     */
    fun isExcluded(path: String, name: String, patterns: Set<String>): Boolean {
        if (patterns.isEmpty()) return false
        val p = path.trimEnd('/')
        return patterns.any { raw ->
            val pat = raw.trim().trimEnd('/')
            if (pat.isBlank()) return@any false
            p == pat || p.startsWith("$pat/") || name == raw || name == pat
        }
    }

    /** Root tidak perlu di-walk jika dirinya (atau ancestor) di-exclude. */
    fun isRootExcluded(root: String, patterns: Set<String>): Boolean {
        if (patterns.isEmpty()) return false
        val r = root.trimEnd('/')
        return patterns.any { raw ->
            val pat = raw.trim().trimEnd('/')
            if (pat.isBlank()) return@any false
            // root sama / di bawah exclude, atau exclude di dalam root (tetap walk, filter file)
            // skip walk hanya jika root sendiri excluded
            r == pat || r.startsWith("$pat/")
        }
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
            if (SystemExcludes.isSystemPath(root) || isRootExcluded(root, excludePatterns)) continue
            if (!fileOps.isReadableDir(root)) continue
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
}
