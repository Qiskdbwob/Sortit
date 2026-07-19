package com.sortit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: Long): TemplateEntity?

    @Query("SELECT * FROM templates WHERE id IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<TemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: TemplateEntity): Long

    @Update
    suspend fun update(t: TemplateEntity)

    @Delete
    suspend fun delete(t: TemplateEntity)

    @Query("SELECT COUNT(*) FROM templates WHERE isDefault = 1")
    suspend fun countDefaults(): Int
}
