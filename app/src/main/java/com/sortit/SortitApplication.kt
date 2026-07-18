package com.sortit

import android.app.Application
import androidx.room.Room
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
import com.sortit.util.Prefs
import com.sortit.util.TrashCleanupWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SortitApplication : Application(), ImageLoaderFactory {
  lateinit var db: AppDatabase
    private set
  lateinit var prefs: Prefs
    private set
  val fileOps = RealFileOps()

  override fun onCreate() {
    super.onCreate()
    db = Room.databaseBuilder(this, AppDatabase::class.java, "sortit.db")
      .fallbackToDestructiveMigration()
      .build()
    prefs = Prefs(this)
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      SeedUseCase(db).seedIfEmpty(prefs)
    }
    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
      "trash_cleanup",
      ExistingPeriodicWorkPolicy.KEEP,
      PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS).build()
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
