package com.sortit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sortit.MainActivity
import com.sortit.R
import com.sortit.SortitApplication
import com.sortit.repo.startOfToday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Widget "Ringkasan hari ini": berapa file dipindah/di-trash sejak 00:00,
 * aktivitas terakhir, tombol segarkan, dan ketuk untuk membuka app.
 * Data dibaca sekali-jalan dari Room (bukan Flow) — cukup untuk widget.
 */
class SortitWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    // goAsync: BroadcastReceiver harus selesai cepat; query DB jalan di coroutine.
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        render(context.applicationContext, appWidgetManager, appWidgetIds)
      } finally {
        pending.finish()
      }
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == ACTION_REFRESH) refresh(context)
  }

  private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
    val app = context.applicationContext as? SortitApplication ?: return
    val dao = app.db.sortLogDao()
    val since = startOfToday()
    val moved = dao.movedCountSinceOnce(since)
    val trashed = dao.trashedCountSinceOnce(since)
    val latest = dao.latestOnce()
    val autoSummary = app.prefs.lastAutoSummary
    val lastLine = when {
      autoSummary.isNotBlank() -> autoSummary
      latest != null -> "Terakhir: ${latest.fileName}"
      else -> "Belum ada aktivitas"
    }

    for (id in ids) {
      val views = RemoteViews(context.packageName, R.layout.widget_sortit)
      views.setTextViewText(R.id.widget_counts, "Hari ini: $moved dipindah · $trashed ke sampah")
      views.setTextViewText(R.id.widget_last, lastLine)
      views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
      views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))
      manager.updateAppWidget(id, views)
    }
  }

  private fun openApp(context: Context): PendingIntent =
    PendingIntent.getActivity(
      context,
      1,
      Intent(context, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

  private fun refreshIntent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      2,
      Intent(context, SortitWidgetProvider::class.java).setAction(ACTION_REFRESH),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

  companion object {
    const val ACTION_REFRESH = "com.sortit.widget.REFRESH"

    /** Minta update semua instance widget (dipanggil setelah auto-apply dsb). */
    fun refresh(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, SortitWidgetProvider::class.java))
      if (ids.isEmpty()) return
      context.sendBroadcast(
        Intent(context, SortitWidgetProvider::class.java).apply {
          action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
          putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
      )
    }
  }
}
