package com.sortit.repo

import com.sortit.data.AppDatabase
import com.sortit.data.ExcludeEntity

class ExcludeRepository(private val db: AppDatabase) {
    suspend fun patternsFor(templateId: Long): List<String> =
        db.excludeDao().forTemplate(templateId).map { it.pattern }

    suspend fun globalPatterns(): List<String> =
        db.excludeDao().globalPatterns()

    suspend fun globalAll(): List<ExcludeEntity> =
        db.excludeDao().globalAll()

    suspend fun add(templateId: Long, pattern: String) {
        val clean = pattern.trim().trimEnd('/')
        if (clean.isBlank()) return
        db.excludeDao().insert(
            ExcludeEntity(templateId = templateId, pattern = clean)
        )
    }

    suspend fun addGlobal(pattern: String) {
        val clean = pattern.trim().trimEnd('/')
        if (clean.isBlank()) return
        val existing = db.excludeDao().globalPatterns()
        if (existing.any { it.trimEnd('/') == clean }) return
        db.excludeDao().insert(
            ExcludeEntity(
                templateId = ExcludeEntity.GLOBAL_TEMPLATE_ID,
                pattern = clean
            )
        )
    }

    suspend fun remove(templateId: Long, pattern: String) {
        val clean = pattern.trim().trimEnd('/')
        db.excludeDao().forTemplate(templateId)
            .firstOrNull { it.pattern.trimEnd('/') == clean || it.pattern == pattern }
            ?.let { db.excludeDao().delete(it) }
    }

    suspend fun removeGlobal(pattern: String) {
        val clean = pattern.trim().trimEnd('/')
        db.excludeDao().deleteGlobalPattern(clean)
        // fallback jika tersimpan dengan trailing slash lama
        db.excludeDao().deleteGlobalPattern("$clean/")
        db.excludeDao().globalAll()
            .filter { it.pattern.trimEnd('/') == clean }
            .forEach { db.excludeDao().delete(it) }
    }

    /** Gabungan global + per-rule untuk scan. */
    suspend fun patternsForScan(templateIds: List<Long>): Set<String> {
        val global = globalPatterns()
        val perRule = templateIds.flatMap { patternsFor(it) }
        return (global + perRule).map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.toSet()
    }
}
