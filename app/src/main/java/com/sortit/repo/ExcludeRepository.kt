package com.sortit.repo

import com.sortit.data.AppDatabase
import com.sortit.data.ExcludeEntity

class ExcludeRepository(private val db: AppDatabase) {
    suspend fun patternsFor(templateId: Long): List<String> =
        db.excludeDao().forTemplate(templateId).map { it.pattern }

    suspend fun add(templateId: Long, pattern: String) =
        db.excludeDao().insert(ExcludeEntity(templateId = templateId, pattern = pattern))

    suspend fun remove(templateId: Long, pattern: String) {
        db.excludeDao().forTemplate(templateId)
            .firstOrNull { it.pattern == pattern }
            ?.let { db.excludeDao().delete(it) }
    }
}
