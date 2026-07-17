package com.sortit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ScanItemEntity>)

    @Query("SELECT * FROM scan_items WHERE sessionId = :sessionId ORDER BY isMedia DESC, name COLLATE NOCASE ASC")
    suspend fun forSession(sessionId: Long): List<ScanItemEntity>

    @Query("SELECT * FROM scan_items WHERE sessionId = :sessionId ORDER BY isMedia DESC, name COLLATE NOCASE ASC")
    fun observeForSession(sessionId: Long): Flow<List<ScanItemEntity>>

    @Query("UPDATE scan_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE scan_items SET status = :status, dstPath = :dstPath WHERE id = :id")
    suspend fun updateResult(id: Long, status: String, dstPath: String?)

    @Query("SELECT COUNT(*) FROM scan_items WHERE sessionId = :sessionId AND status = 'PENDING'")
    fun observePendingCount(sessionId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM scan_items WHERE sessionId = :sessionId AND status = 'EXCLUDED'")
    fun observeExcludedCount(sessionId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM scan_items WHERE sessionId IN (SELECT id FROM scan_sessions WHERE status = 'PREVIEW') AND status = 'PENDING'")
    fun observeGlobalPendingCount(): Flow<Int>

    @Query("DELETE FROM scan_items WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}
