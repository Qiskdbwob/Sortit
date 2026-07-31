package com.sortit

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.sortit.data.AppDatabase
import com.sortit.repo.RealFileOps
import com.sortit.repo.SeedUseCase
import com.sortit.util.AutoApplyWorker
import com.sortit.util.Prefs
import com.sortit.util.TrashCleanupWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Migration eksplisit - menggantikan fallbackToDestructiveMigration agar data user
// (templates, monitors, excludes, logs) tidak hilang saat upgrade skema.
//
// v1 = templates(id,name,extensions,targetTreeUri,sourceMode,sourceDirs,enabled,isDefault,createdAt)
//      + monitors + excludes + sort_logs
// v2 = v1 + scan_sessions + scan_items
// v3 = v2 + templates: minSizeBytes, maxSizeBytes, maxAgeDays, autoEnabled, autoAction
//
// Semua migration defensif: CREATE TABLE IF NOT EXISTS + cek PRAGMA table_info
// sebelum ALTER, sehingga aman dijalankan walau skema lama sedikit berbeda.
private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
  query("PRAGMA table_info($table)").use { c ->
    val idx = c.getColumnIndex("name")
    while (c.moveToNext()) {
      if (idx >= 0 && c.getString(idx) == column) return true
    }
  }
  return false
}

private fun SupportSQLiteDatabase.createScanTablesIfMissing() {
  execSQL(
    """
    CREATE TABLE IF NOT EXISTS `scan_sessions` (
      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
      `createdAt` INTEGER NOT NULL,
      `updatedAt` INTEGER NOT NULL,
      `status` TEXT NOT NULL,
      `ruleIds` TEXT NOT NULL,
      `signature` TEXT NOT NULL,
      `totalFound` INTEGER NOT NULL
    )
    """.trimIndent()
  )
  execSQL(
    """
    CREATE TABLE IF NOT EXISTS `scan_items` (
      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
      `sessionId` INTEGER NOT NULL,
      `templateId` INTEGER NOT NULL,
      `path` TEXT NOT NULL,
      `name` TEXT NOT NULL,
      `size` INTEGER NOT NULL,
      `mimeType` TEXT,
      `lastModified` INTEGER NOT NULL,
      `isMedia` INTEGER NOT NULL,
      `status` TEXT NOT NULL,
      `dstPath` TEXT
    )
    """.trimIndent()
  )
  execSQL("CREATE INDEX IF NOT EXISTS `index_scan_items_sessionId` ON `scan_items` (`sessionId`)")
  execSQL("CREATE INDEX IF NOT EXISTS `index_scan_items_templateId` ON `scan_items` (`templateId`)")
  execSQL("CREATE INDEX IF NOT EXISTS `index_scan_items_status` ON `scan_items` (`status`)")
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.createScanTablesIfMissing()
  }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
  override fun migrate(db: SupportSQLiteDatabase) {
    if (!db.hasColumn("templates", "minSizeBytes")) {
      db.execSQL("ALTER TABLE `templates` ADD COLUMN `minSizeBytes` INTEGER NOT NULL DEFAULT 0")
    }
    if (!db.hasColumn("templates", "maxSizeBytes")) {
      db.execSQL("ALTER TABLE `templates` ADD COLUMN `maxSizeBytes` INTEGER NOT NULL DEFAULT 0")
    }
    if (!db.hasColumn("templates", "maxAgeDays")) {
      db.execSQL("ALTER TABLE `templates` ADD COLUMN `maxAgeDays` INTEGER NOT NULL DEFAULT 0")
    }
    if (!db.hasColumn("templates", "autoEnabled")) {
      db.execSQL("ALTER TABLE `templates` ADD COLUMN `autoEnabled` INTEGER NOT NULL DEFAULT 0")
    }
    if (!db.hasColumn("templates", "autoAction")) {
      db.execSQL("ALTER TABLE `templates` ADD COLUMN `autoAction` TEXT NOT NULL DEFAULT 'MOVE'")
    }
  }
}

class SortitApplication : Application(), ImageLoaderFactory {
  lateinit var db: AppDatabase
    private set
  lateinit var prefs: Prefs
    private set
  val fileOps = RealFileOps()

  override fun onCreate() {
    super.onCreate()
    db = Room.databaseBuilder(this, AppDatabase::class.java, "sortit.db")
      .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
      .build()
    prefs = Prefs(this)
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        SeedUseCase(db).seedIfEmpty(prefs)
      } catch (t: Throwable) {
        // Jangan sampai kegagalan seed menggagalkan startup app.
        // Sesi berikutnya akan mencoba lagi (seedVersion belum naik).
      }
    }
    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
      "trash_cleanup",
      ExistingPeriodicWorkPolicy.KEEP,
      PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS).build()
    )
    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
      "auto_apply",
      ExistingPeriodicWorkPolicy.KEEP,
      PeriodicWorkRequestBuilder<AutoApplyWorker>(15, TimeUnit.MINUTES).build()
    )
  }

  override fun newImageLoader(): ImageLoader {
    return ImageLoader.Builder(this)
      .components {
        add(VideoFrameDecoder.Factory())
      }
      .memoryCache {
        MemoryCache.Builder(this)
          .maxSizePercent(0.25)
          .build()
      }
      .diskCache {
        DiskCache.Builder()
          .directory(cacheDir.resolve("image_cache"))
          .maxSizePercent(0.02)
          .build()
      }
      .respectCacheHeaders(false)
      .build()
  }
}
