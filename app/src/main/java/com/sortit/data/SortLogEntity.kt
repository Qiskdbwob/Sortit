package com.sortit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sort_logs", indices = [androidx.room.Index("templateId")])
data class SortLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val fileName: String,
    val srcPath: String,
    val dstPath: String,
    val status: String,              // OK | FAIL | SKIP
    val size: Long,
    val timestamp: Long = System.currentTimeMillis()
)
