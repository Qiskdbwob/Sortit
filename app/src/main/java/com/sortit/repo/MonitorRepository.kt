package com.sortit.repo

import com.sortit.data.AppDatabase
import com.sortit.data.MonitorEntity
import kotlinx.coroutines.flow.Flow

class MonitorRepository(private val db: AppDatabase) {
    fun observe(): Flow<List<MonitorEntity>> = db.monitorDao().observeAll()
    suspend fun insert(m: MonitorEntity): Long = db.monitorDao().insert(m)
    suspend fun update(m: MonitorEntity) = db.monitorDao().update(m)
    suspend fun delete(m: MonitorEntity) = db.monitorDao().delete(m)
    suspend fun countDefaults(): Int = db.monitorDao().countDefaults()
}
