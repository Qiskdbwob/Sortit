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

  @Query("DELETE FROM sort_logs WHERE dstPath = :path")
  suspend fun deleteByDstPath(path: String)

  @Query("SELECT * FROM sort_logs WHERE templateId = :templateId ORDER BY timestamp DESC LIMIT :limit")
  suspend fun recent(templateId: Long, limit: Int = 200): List<SortLogEntity>

  @Query("SELECT * FROM sort_logs ORDER BY timestamp DESC LIMIT :limit")
  fun observeRecent(limit: Int): Flow<List<SortLogEntity>>

  @Query("SELECT * FROM sort_logs WHERE dstPath LIKE '%/.sortit-trash/%' ORDER BY timestamp DESC LIMIT :limit")
  suspend fun listTrashed(limit: Int = 2000): List<SortLogEntity>

  @Query("SELECT * FROM sort_logs WHERE dstPath NOT LIKE '%/.sortit-trash/%' ORDER BY timestamp DESC LIMIT :limit")
  suspend fun listMoved(limit: Int = 2000): List<SortLogEntity>

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

  /** Query sekali-jalan untuk widget home screen (bukan Flow). */
  @Query("SELECT COUNT(*) FROM sort_logs WHERE status = 'OK' AND dstPath NOT LIKE '%/.sortit-trash/%' AND timestamp >= :since")
  suspend fun movedCountSinceOnce(since: Long): Int

  @Query("SELECT COUNT(*) FROM sort_logs WHERE status = 'OK' AND dstPath LIKE '%/.sortit-trash/%' AND timestamp >= :since")
  suspend fun trashedCountSinceOnce(since: Long): Int

  @Query("SELECT * FROM sort_logs ORDER BY timestamp DESC LIMIT 1")
  suspend fun latestOnce(): SortLogEntity?

  /**
   * Retensi HANYA log trash (file di .sortit-trash sudah ikut dibersihkan).
   * Log pindah/undo tidak boleh ikut terhapus — itu satu-satunya jejak untuk
   * fitur Undo & tab "Dipindahkan".
   */
  @Query("DELETE FROM sort_logs WHERE dstPath LIKE '%/.sortit-trash/%' AND timestamp < :cutoff")
  suspend fun deleteTrashedOlderThan(cutoff: Long)

  /**
   * Batasi pertumbuhan tabel: sisakan [max] baris terbaru. Tanpa ini
   * sort_logs tumbuh tanpa batas untuk user aktif.
   */
  @Query("DELETE FROM sort_logs WHERE id NOT IN (SELECT id FROM sort_logs ORDER BY timestamp DESC, id DESC LIMIT :max)")
  suspend fun trimTo(max: Int)
}
