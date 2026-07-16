package com.sortit.repo

import com.sortit.data.AppDatabase
import com.sortit.data.SortLogEntity

class SortLogRepository(private val db: AppDatabase) {
    suspend fun recent(templateId: Long, limit: Int = 200): List<SortLogEntity> =
        db.sortLogDao().recent(templateId, limit)
}
