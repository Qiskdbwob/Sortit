package com.sortit

import android.app.Application
import androidx.room.Room
import com.sortit.data.AppDatabase
import com.sortit.repo.RealFileOps
import com.sortit.repo.SeedUseCase
import com.sortit.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SortitApplication : Application() {
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
        // SupervisorJob: jika seed gagal, coroutine lain tidak ikut ter-cancel
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            SeedUseCase(db).seedIfEmpty(prefs)
        }
    }
}
