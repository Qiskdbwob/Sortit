package com.sortit.repo

import com.sortit.data.AppDatabase
import com.sortit.data.MonitorEntity
import com.sortit.data.TemplateEntity

/**
 * Abstraksi ringan untuk SeedUseCase — memisahkan dari Android framework
 * (RoomDatabase, SharedPreferences) supaya migrasi bisa di-unit-test di JVM.
 */
interface SeedDb {
    suspend fun countDefaultMonitors(): Int
    suspend fun insertMonitor(m: MonitorEntity): Long
    suspend fun getAllMonitors(): List<MonitorEntity>

    suspend fun countDefaultTemplates(): Int
    suspend fun insertTemplate(t: TemplateEntity): Long
    suspend fun getAllTemplates(): List<TemplateEntity>
    suspend fun updateTemplate(t: TemplateEntity)
}

/** State seed yang dibutuhkan migrasi — tanpa SharedPreferences. */
interface SeedPrefs {
    var seeded: Boolean
    var seedVersion: Int
}

/** Adaptor AppDatabase (Room) -> SeedDb. */
class SeedDbAdapter(private val db: AppDatabase) : SeedDb {
    override suspend fun countDefaultMonitors(): Int = db.monitorDao().countDefaults()
    override suspend fun insertMonitor(m: MonitorEntity): Long = db.monitorDao().insert(m)
    override suspend fun getAllMonitors(): List<MonitorEntity> = db.monitorDao().getAllOnce()

    override suspend fun countDefaultTemplates(): Int = db.templateDao().countDefaults()
    override suspend fun insertTemplate(t: TemplateEntity): Long = db.templateDao().insert(t)
    override suspend fun getAllTemplates(): List<TemplateEntity> = db.templateDao().getAllOnce()
    override suspend fun updateTemplate(t: TemplateEntity) = db.templateDao().update(t)
}
