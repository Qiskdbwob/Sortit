package com.sortit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {
    @Insert
    suspend fun insert(s: ScanSessionEntity): Long

    @Update
    suspend fun update(s: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions WHERE status = 'PREVIEW' ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestActive(): ScanSessionEntity?

    @Query("SELECT * FROM scan_sessions WHERE status = 'PREVIEW' ORDER BY createdAt DESC LIMIT 1")
    fun observeActive(): Flow<ScanSessionEntity?>

    @Query("SELECT * FROM scan_sessions WHERE status = 'PREVIEW' ORDER BY createdAt DESC")
    suspend fun activeSessions(): List<ScanSessionEntity>

    @Query("UPDATE scan_sessions SET status = 'DONE', updatedAt = :now WHERE id = :id")
    suspend fun done(id: Long, now: Long)

    @Query("UPDATE scan_sessions SET status = 'DISMISSED', updatedAt = :now WHERE id = :id")
    suspend fun dismiss(id: Long, now: Long)

    @Query("UPDATE scan_sessions SET totalFound = :total, updatedAt = :now WHERE id = :id")
    suspend fun updateTotal(id: Long, total: Int, now: Long)
}
