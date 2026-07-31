package com.sortit.util

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
        if (child.lastModified() < cutoff && child.delete()) {
          deletedCount++
          freedBytes += child.length()
        }
      } else if (child.isDirectory) {
        child.listFiles()?.forEach { f ->
          if (f.isFile && f.lastModified() < cutoff && f.delete()) {
            deletedCount++
            freedBytes += f.length()
          }
        }
        // Bersihkan folder ekstensi yang sudah kosong supaya tidak menumpuk.
        if (child.listFiles()?.isEmpty() == true) child.delete()
      }
    }

    CleanupResult(deletedCount, freedBytes)
  }
}
