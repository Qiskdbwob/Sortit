package com.sortit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SortLogDao {
    @Insert
    suspend fun insert(l: SortLogEntity)

    @Query("SELECT * FROM sort_logs WHERE templateId = :templateId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(templateId: Long, limit: Int = 200): List<SortLogEntity>

    @Query("SELECT * FROM sort_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SortLogEntity>>

    @Query("SELECT COUNT(*) FROM sort_logs WHERE status = 'OK' AND dstPath NOT LIKE '%/.sortit-trash/%'")
    fun observeMovedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sort_logs WHERE status = 'OK' AND dstPath LIKE '%/.sortit-trash/%'")
    fun observeTrashedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sort_logs WHERE status = 'FAIL'")
    fun observeFailedCount(): Flow<Int>
    @Query("SELECT * FROM sort_logs WHERE dstPath LIKE '%' || :trashRoot || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun trashedFiles(trashRoot: String, limit: Int = 100): List<SortLogEntity>

    @Query("UPDATE sort_logs SET status = 'RESTORED' WHERE id = :logId")
    suspend fun markRestored(logId: Long)
}