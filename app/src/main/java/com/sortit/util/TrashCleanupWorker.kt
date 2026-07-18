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
      val cutoff = System.currentTimeMillis() - (90L * 86_400_000L)
      sortitApp.db.sortLogDao().deleteOlderThan(cutoff)
    }
    return Result.success()
  }
}
