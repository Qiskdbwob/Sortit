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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: ExcludeEntity)

    @Delete
    suspend fun delete(e: ExcludeEntity)
}
