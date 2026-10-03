package com.sortit.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sortit.SortitApplication
import com.sortit.domain.AutoApplyUseCase
import com.sortit.repo.ExcludeRepository
import com.sortit.repo.TemplateRepository
import com.sortit.widget.SortitWidgetProvider

// Worker periodik (15 menit) + pemicu manual (Quick Settings tile / one-time work)
// untuk auto-apply rule yang autoEnabled - MOVE/TRASH tanpa preview, hormati
// exclude + filter ukuran/umur. Retry kalau ada exception.
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
      app.prefs.lastAutoSummary =
        if (r.moved + r.trashed == 0) "Tidak ada file baru · ${r.skipped} dilewat"
        else "${r.moved} dipindah · ${r.trashed} ke sampah"
      NotifHelper.showAutoResult(applicationContext, r.moved, r.trashed, r.failed)
      SortitWidgetProvider.refresh(applicationContext)
      Result.success()
    } catch (e: Exception) {
      Result.retry()
    }
  }

  companion object {
    /** Nama unique work untuk pemicu manual (tile Quick Settings). */
    const val ONE_TIME_WORK = "auto_apply_now"
  }
}
