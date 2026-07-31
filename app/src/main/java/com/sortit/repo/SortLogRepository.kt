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

/** Mulai hari ini 00:00 (local) — dipakai dashboard "Manifest hari ini". */
fun startOfToday(): Long {
  val cal = java.util.Calendar.getInstance()
  cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
  cal.set(java.util.Calendar.MINUTE, 0)
  cal.set(java.util.Calendar.SECOND, 0)
  cal.set(java.util.Calendar.MILLISECOND, 0)
  return cal.timeInMillis
}

class SortLogRepository(private val db: AppDatabase) {
    suspend fun recent(templateId: Long, limit: Int = 200): List<SortLogEntity> =
        db.sortLogDao().recent(templateId, limit)

    fun observeRecent(limit: Int = 12): Flow<List<SortLogEntity>> = db.sortLogDao().observeRecent(limit)

    /** Total akumulasi (seumur hidup) — dipakai History & pembersihan. */
    fun observeCounts(): Flow<SortCounts> = combine(
        db.sortLogDao().observeMovedCount(),
        db.sortLogDao().observeTrashedCount(),
        db.sortLogDao().observeFailedCount()
    ) { moved, trashed, failed ->
        SortCounts(moved = moved, trashed = trashed, failed = failed)
    }

    /** Hanya aktivitas sejak tengah malam — dashboard "Manifest hari ini". */
    fun observeTodayCounts(): Flow<SortCounts> = combine(
        db.sortLogDao().observeMovedCountSince(startOfToday()),
        db.sortLogDao().observeTrashedCountSince(startOfToday()),
        db.sortLogDao().observeFailedCountSince(startOfToday())
    ) { moved, trashed, failed ->
        SortCounts(moved = moved, trashed = trashed, failed = failed)
    }

    suspend fun listTrashed(limit: Int = 2000): List<SortLogEntity> = db.sortLogDao().listTrashed(limit)
    suspend fun listMoved(limit: Int = 2000): List<SortLogEntity> = db.sortLogDao().listMoved(limit)
    suspend fun listOk(limit: Int = 3000): List<SortLogEntity> = db.sortLogDao().listOk(limit)
    suspend fun listAll(limit: Int = 3000): List<SortLogEntity> = db.sortLogDao().listAll(limit)
    suspend fun delete(log: SortLogEntity) = db.sortLogDao().delete(log)
    suspend fun update(log: SortLogEntity) = db.sortLogDao().update(log)
    suspend fun deleteIds(ids: List<Long>) {
        if (ids.isNotEmpty()) db.sortLogDao().deleteIds(ids)
    }
}
