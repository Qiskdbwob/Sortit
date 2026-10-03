package com.sortit.widget

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sortit.MainActivity
import com.sortit.R
import com.sortit.SortitApplication
import com.sortit.util.AutoApplyWorker
import com.sortit.util.Prefs
import com.sortit.util.StorageAccess

/**
 * Quick Settings tile "Rapikan sekarang":
 * - izin storage OK → jalankan auto-apply (WorkManager one-time)
 * - belum ada izin → buka app supaya user mengaktifkan All-files access
 * Subtitle menampilkan hasil auto-apply terakhir.
 */
class SortitTileService : TileService() {

  override fun onStartListening() {
    super.onStartListening()
    updateTile()
  }

  override fun onClick() {
    super.onClick()
    val app = applicationContext as? SortitApplication
    if (app == null || !StorageAccess.has(this)) {
      openApp()
      return
    }
    WorkManager.getInstance(this).enqueueUniqueWork(
      AutoApplyWorker.ONE_TIME_WORK,
      ExistingWorkPolicy.REPLACE,
      OneTimeWorkRequestBuilder<AutoApplyWorker>().build()
    )
    app.prefs.lastAutoSummary = "Merapikan file…"
    updateTile()
  }

  private fun updateTile() {
    val tile = qsTile ?: return
    tile.state = Tile.STATE_INACTIVE
    tile.label = "Sortit"
    tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_sortit)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val summary = Prefs(this).lastAutoSummary
      tile.subtitle = summary.ifBlank { "Rapikan file sekarang" }
    }
    tile.updateTile()
  }

  private fun openApp() {
    val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startActivityAndCollapse(
        PendingIntent.getActivity(
          this, 3, intent,
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
      )
    } else {
      // API < 34: overload PendingIntent belum ada di framework, jadi satu-satunya
      // cara yang benar memang overload Intent (di API 34+ ia melempar exception —
      // karena itu cabang ini hanya jalan di bawah 34).
      @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
      startActivityAndCollapse(intent)
    }
  }
}
