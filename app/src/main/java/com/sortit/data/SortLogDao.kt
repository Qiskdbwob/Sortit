package com.sortit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SortLogDao {
  @Insert
  suspend fun insert(l: SortLogEntity): Long

  @Update
  suspend fun update(l: SortLogEntity)

  @Delete
  suspend fun delete(l: SortLogEntity)

  @Query("DELETE FROM sort_logs WHERE id IN (:ids)")
  suspend fun deleteIds(ids: List<Long>)

  @Query("SELECT * FROM sort_logs WHERE templateId = :templateId ORDER BY timestamp DESC LIMIT :limit")
  suspend fun recent(templateId: Long, limit: Int = 200): List<SortLogEntity>

  @Query("SELECT * FROM sort_logs ORDER BY timestamp DESC LIMIT :limit")
  fun observeRecent(limit: Int): Flow<List<SortLogEntity>>

  @Query("SELECT * FROM sort_logs WHERE dstPath LIKE '%/.sortit-trash/%' ORDER BY timestamp DESC LIMIT :limit")
  suspend fun listTrashed(limit: Int = 2000): List<SortLogEntity>

  @Query("SELECT * FROM sort_logs WHERE dstPath NOT LIKE '%/.sortit-trash/%' ORDER BY timestamp DESC LIMIT :limit")
  suspend fun listMoved(limit: Int = 2000): List<SortLogEntity>

  @Query("SELECT * FROM sort_logs WHERE status = 'OK' ORDER BY timestamp DESC LIMIT :limit")
  suspend fun listOk(limit: Int = 3000): List<SortLogEntity>

  /** Log sukses + gagal (FAIL/SKIP) — dipakai History supaya file yang gagal/lewat terlihat. */
  @Query("SELECT * FROM sort_logs ORDER BY timestamp DESC LIMIT :limit")
  suspend fun listAll(limit: Int = 3000): List<SortLogEntity>

  @Query("SELECT COUNT(*) FROM sort_logs WHERE status = 'OK' AND dstPath NOT LIKE '%/.sortit-trash/%'")
  fun observeMovedCount(): Flow<Int>

  /** Hitung hanya log hari ini (sejak 00:00) — dashboard "Manifest hari ini". */
  @Query("SELECT COUNT(*) FROM sort_logs WHERE status = 'OK' AND dstPath NOT LIKE '%/.sortit-trash/%' AND timestamp >= :since")
  fun observeMovedCountSince(since: Long): Flow<Int>

  @Query("SELECT COUNT(*) FROM sort_logs WHERE status = 'OK' AND dstPath LIKE '%/.sortit-trash/%' AND timestamp >= :since")
  fun observeTrashedCountSince(since: Long): Flow<Int>

  @Query("SELECT COUNT(*) FROM sort_logs WHERE status = 'FAIL' AND timestamp >= :since")
  fun observeFailedCountSince(since: Long): Flow<Int>

  @Query("SELECT COUNT(*) FROM sort_logs WHERE status = 'OK' AND dstPath LIKE '%/.sortit-trash/%'")
  fun observeTrashedCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM sort_logs WHERE status = 'FAIL'")
  fun observeFailedCount(): Flow<Int>

  @Query("DELETE FROM sort_logs WHERE timestamp < :cutoff")
  suspend fun deleteOlderThan(cutoff: Long)
}
