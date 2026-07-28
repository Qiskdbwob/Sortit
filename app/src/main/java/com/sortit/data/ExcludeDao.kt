package com.sortit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExcludeDao {
    @Query("SELECT * FROM excludes WHERE templateId = :templateId")
    suspend fun forTemplate(templateId: Long): List<ExcludeEntity>

    /** Global user excludes: templateId = 0 (bukan rule). */
    @Query("SELECT * FROM excludes WHERE templateId = 0 ORDER BY pattern ASC")
    suspend fun globalAll(): List<ExcludeEntity>

    @Query("SELECT pattern FROM excludes WHERE templateId = 0")
    suspend fun globalPatterns(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: ExcludeEntity): Long

    @Delete
    suspend fun delete(e: ExcludeEntity)

    @Query("DELETE FROM excludes WHERE templateId = 0 AND pattern = :pattern")
    suspend fun deleteGlobalPattern(pattern: String)
}
