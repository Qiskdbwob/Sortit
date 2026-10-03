package com.sortit.util

import com.sortit.data.AppDatabase
import com.sortit.repo.FileOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TrashCleanupUseCase(
  private val fileOps: FileOps,
  private val prefs: Prefs
) {
  data class CleanupResult(val deletedCount: Int, val freedBytes: Long)

  suspend fun cleanup(): CleanupResult = withContext(Dispatchers.IO) {
    val retentionMs = prefs.trashRetentionDays * 86_400_000L
    val cutoff = System.currentTimeMillis() - retentionMs
    val trashDir = File(FileOps.TRASH_ROOT)
    if (!trashDir.exists()) return@withContext CleanupResult(0, 0)

    var deletedCount = 0
    var freedBytes = 0L

    // File trash tersimpan di subfolder ekstensi: .sortit-trash/<ext>/file
    trashDir.listFiles()?.forEach { child ->
      if (child.isFile) {
        // Ukuran HARUS dibaca sebelum delete — setelah dihapus length() = 0,
        // dulu membuat "freedBytes" selalu 0 (terlihat seperti tidak bekerja).
        if (child.lastModified() < cutoff) {
          val size = child.length()
          if (child.delete()) {
            deletedCount++
            freedBytes += size
          }
        }
      } else if (child.isDirectory) {
        child.listFiles()?.forEach { f ->
          if (f.isFile && f.lastModified() < cutoff) {
            val size = f.length()
            if (f.delete()) {
              deletedCount++
              freedBytes += size
            }
          }
        }
        // Bersihkan folder ekstensi yang sudah kosong supaya tidak menumpuk.
        if (child.listFiles()?.isEmpty() == true) child.delete()
      }
    }

    CleanupResult(deletedCount, freedBytes)
  }

  /**
   * File trash kedaluwarsa + retensi log trash + trim tabel log.
   * Dipakai worker harian, pemicu saat app start / retensi diubah, dan tombol
   * "Bersihkan sekarang" di Pengaturan.
   */
  suspend fun cleanupWithLogs(db: AppDatabase): CleanupResult {
    val result = cleanup()
    val cutoff = System.currentTimeMillis() - prefs.trashRetentionDays * 86_400_000L
    val dao = db.sortLogDao()
    dao.deleteTrashedOlderThan(cutoff)
    dao.trimTo(MAX_LOG_ROWS)
    return result
  }

  companion object {
    /** Cap sort_logs — cukup untuk ratusan sesi, hindari DB membengkak. */
    const val MAX_LOG_ROWS = 5000
  }
}
