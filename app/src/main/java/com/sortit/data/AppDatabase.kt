package com.sortit.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TemplateEntity::class,
        MonitorEntity::class,
        ExcludeEntity::class,
        SortLogEntity::class,
        ScanSessionEntity::class,
        ScanItemEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun monitorDao(): MonitorDao
    abstract fun excludeDao(): ExcludeDao
    abstract fun sortLogDao(): SortLogDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun scanItemDao(): ScanItemDao
}
