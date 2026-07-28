package com.sortit.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "templates",
    indices = [Index(value = ["name"], unique = true)]
)
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val extensions: String,          // CSV lowercase tanpa titik
    val targetTreeUri: String,       // folder tujuan absolut
    val sourceMode: String,          // "ALL" | "FOLDERS"
    val sourceDirs: String?,         // CSV path, null jika ALL
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** 0 = no min filter. Bytes. */
    val minSizeBytes: Long = 0L,
    /** 0 = no max filter. Bytes. */
    val maxSizeBytes: Long = 0L,
    /** 0 = no age filter. Only files older than N days. */
    val maxAgeDays: Int = 0,
    /** Auto-apply without manual scan (source FOLDERS recommended). */
    val autoEnabled: Boolean = false,
    /** MOVE | TRASH when autoEnabled. */
    val autoAction: String = "MOVE"
)
