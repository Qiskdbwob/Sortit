package com.sortit.repo

import com.sortit.data.AppDatabase
import com.sortit.data.TemplateEntity
import kotlinx.coroutines.flow.Flow

class TemplateRepository(private val db: AppDatabase) {
    fun observe(): Flow<List<TemplateEntity>> = db.templateDao().observeAll()
    suspend fun get(id: Long): TemplateEntity? = db.templateDao().get(id)
    suspend fun insert(t: TemplateEntity): Long = db.templateDao().insert(t)
    suspend fun update(t: TemplateEntity) = db.templateDao().update(t)
    suspend fun delete(t: TemplateEntity) = db.templateDao().delete(t)
    suspend fun countDefaults(): Int = db.templateDao().countDefaults()
}
