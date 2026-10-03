package com.sortit.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sortit.SortitApplication
import java.util.concurrent.TimeUnit

class TrashCleanupWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val app = applicationContext as? SortitApplication ?: return Result.failure()
    TrashCleanupUseCase(app.fileOps, app.prefs).cleanupWithLogs(app.db)
    return Result.success()
  }

  companion object {
    private const val PERIODIC_WORK = "trash_cleanup"
    private const val NOW_WORK = "trash_cleanup_now"

    /** Jadwal harian — retensi tetap jalan walau app jarang dibuka. */
    fun schedulePeriodic(context: Context) {
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        PERIODIC_WORK,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS).build()
      )
    }

    /**
     * Paksa cleanup secepatnya (tanpa menunggu jadwal harian): dipakai saat app
     * start, setelah retensi diubah di Pengaturan, dsb. Hasilnya terlihat dalam
     * beberapa detik.
     */
    fun enqueueNow(context: Context) {
      WorkManager.getInstance(context).enqueueUniqueWork(
        NOW_WORK,
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<TrashCleanupWorker>().build()
      )
    }
  }
}
