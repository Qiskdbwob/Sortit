package com.sortit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Satu sesi scan/review. Status:
// PREVIEW  = hasil scan masih menunggu tindakan user (boleh dipakai ulang agar tidak scan ulang)
// DONE     = user sudah menindak hasil scan (pindah/trash)
// DISMISSED= sesi dibuang karena user pilih scan ulang / rule berubah
@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = "PREVIEW",
    val ruleIds: String,       // CSV id template/rule yang discan bersamaan
    val signature: String,     // sidik konfigurasi scan; dipakai untuk deteksi rule berubah
    val totalFound: Int = 0
)
