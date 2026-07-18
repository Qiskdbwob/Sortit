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
    trashDir.listFiles()?.forEach { file ->
      if (file.lastModified() < cutoff) {
        val size = file.length()
        if (file.delete()) {
          deletedCount++
          freedBytes += size
        }
      }
    }
    CleanupResult(deletedCount, freedBytes)
  }
}
