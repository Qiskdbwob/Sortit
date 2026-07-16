package com.sortit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitors")
data class MonitorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
