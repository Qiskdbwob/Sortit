package com.sortit.repo

import com.sortit.data.AppDatabase
import com.sortit.data.SortLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class SortCounts(
    val moved: Int = 0,
    val trashed: Int = 0,
    val failed: Int = 0
)

class SortLogRepository(private val db: AppDatabase) {
    suspend fun recent(templateId: Long, limit: Int = 200): List<SortLogEntity> =
        db.sortLogDao().recent(templateId, limit)

    fun observeRecent(limit: Int = 12): Flow<List<SortLogEntity>> = db.sortLogDao().observeRecent(limit)

    fun observeCounts(): Flow<SortCounts> = combine(
        db.sortLogDao().observeMovedCount(),
        db.sortLogDao().observeTrashedCount(),
        db.sortLogDao().observeFailedCount()
    ) { moved, trashed, failed ->
        SortCounts(moved = moved, trashed = trashed, failed = failed)
    }

    suspend fun getTrashedFiles(limit: Int = 100): List<SortLogEntity> =
        db.sortLogDao().trashedFiles(com.sortit.repo.FileOps.TRASH_ROOT, limit)

    suspend fun markRestored(logId: Long) = db.sortLogDao().markRestored(logId)
}
