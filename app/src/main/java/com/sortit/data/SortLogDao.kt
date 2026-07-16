package com.sortit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SortLogDao {
    @Insert
    suspend fun insert(l: SortLogEntity)

    @Query("SELECT * FROM sort_logs WHERE templateId = :templateId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(templateId: Long, limit: Int = 200): List<SortLogEntity>
}
