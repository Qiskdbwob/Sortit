package com.sortit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitorDao {
    @Query("SELECT * FROM monitors ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<MonitorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: MonitorEntity): Long

    @Update
    suspend fun update(m: MonitorEntity)

    @Delete
    suspend fun delete(m: MonitorEntity)

    @Query("SELECT COUNT(*) FROM monitors WHERE isDefault = 1")
    suspend fun countDefaults(): Int
}
