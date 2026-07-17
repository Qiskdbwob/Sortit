package com.sortit.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// File hasil scan yang tersimpan di DB. Status:
// PENDING  = masuk daftar tindak (default)
// EXCLUDED = user memilih file ini untuk dikecualikan dari aksi
// MOVED    = sudah dipindah ke folder target rule
// TRASHED  = sudah dipindah ke .sortit-trash
// FAILED   = gagal ditindak
@Entity(
    tableName = "scan_items",
    indices = [Index("sessionId"), Index("templateId"), Index("status")]
)
data class ScanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val templateId: Long,
    val path: String,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val lastModified: Long,
    val isMedia: Boolean,
    val status: String = "PENDING",
    val dstPath: String? = null
)
