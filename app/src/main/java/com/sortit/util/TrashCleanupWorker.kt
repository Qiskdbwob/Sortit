package com.sortit.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrashCleanupWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val app = applicationContext as? android.app.Application ?: return Result.failure()
    val sortitApp = app as? com.sortit.SortitApplication ?: return Result.failure()
    TrashCleanupUseCase(sortitApp.fileOps, sortitApp.prefs).cleanup()
    withContext(Dispatchers.IO) {
      // Log retensi ikut preferensi trash (bukan hardcoded 90 hari) supaya
      // riwayat sampah & log sinkron dengan umur file di trash.
      val retentionMs = sortitApp.prefs.trashRetentionDays * 86_400_000L
      val cutoff = System.currentTimeMillis() - retentionMs
      sortitApp.db.sortLogDao().deleteOlderThan(cutoff)
    }
    return Result.success()
  }
}
