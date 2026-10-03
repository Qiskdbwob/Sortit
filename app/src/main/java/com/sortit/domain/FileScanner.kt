package com.sortit.domain

import com.sortit.data.TemplateEntity

// Shared scan engine: roots, system/global/per-rule exclude, extension, size, age.
object FileScanner {

    fun resolveRoots(template: TemplateEntity): List<String> =
        when (template.sourceMode) {
            "ALL" -> listOf("/storage/emulated/0")
            else -> splitSourceDirs(template.sourceDirs ?: "")
        }

    /** Pisahkan sourceDirs — support newline (baru) dan koma (lama/migrasi). */
    fun splitSourceDirs(raw: String): List<String> =
        raw.split('\n', '|', ',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    fun isExcluded(path: String, name: String, patterns: Set<String>): Boolean {
        if (patterns.isEmpty()) return false
        val p = path.trimEnd('/')
        return patterns.any { raw ->
            val pat = raw.trim().trimEnd('/')
            if (pat.isBlank()) return@any false
            p == pat || p.startsWith("${pat}/") || name == raw || name == pat
        }
    }

    fun isRootExcluded(root: String, patterns: Set<String>): Boolean {
        if (patterns.isEmpty()) return false
        val r = root.trimEnd('/')
        return patterns.any { raw ->
            val pat = raw.trim().trimEnd('/')
            if (pat.isBlank()) return@any false
            r == pat || r.startsWith("${pat}/")
        }
    }

    /** Size + age filters from rule. maxAgeDays = only files older than N days. */
    fun matchesMeta(template: TemplateEntity, size: Long, lastModified: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (template.minSizeBytes > 0 && size < template.minSizeBytes) return false
        if (template.maxSizeBytes > 0 && size > template.maxSizeBytes) return false
        if (template.maxAgeDays > 0) {
            val ageMs = now - lastModified
            val needMs = template.maxAgeDays.toLong() * 86_400_000L
            if (ageMs < needMs) return false
        }
        return true
    }
}
