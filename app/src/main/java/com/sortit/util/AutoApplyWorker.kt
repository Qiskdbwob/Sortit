package com.sortit.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sortit.SortitApplication
import com.sortit.domain.AutoApplyUseCase
import com.sortit.repo.ExcludeRepository
import com.sortit.repo.TemplateRepository

// Worker periodik (15 menit) untuk auto-apply rule yang autoEnabled - MOVE/TRASH
// tanpa preview, hormati exclude + filter ukuran/umur. Retry kalau ada exception.
class AutoApplyWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    return try {
      val app = applicationContext as? SortitApplication ?: return Result.failure()
      val templateRepo = TemplateRepository(app.db)
      val excludeRepo = ExcludeRepository(app.db)
      val r = AutoApplyUseCase(app.fileOps, templateRepo, excludeRepo, app.db.sortLogDao())
        .applyAllEnabled()
      NotifHelper.showAutoResult(applicationContext, r.moved, r.trashed, r.failed)
      Result.success()
    } catch (e: Exception) {
      Result.retry()
    }
  }
}
