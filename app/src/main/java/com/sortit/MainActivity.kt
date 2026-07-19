package com.sortit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sortit.domain.ScanSortUseCase
import com.sortit.domain.SortFilesUseCase
import com.sortit.repo.ExcludeRepository
import com.sortit.repo.MonitorRepository
import com.sortit.repo.ScanSessionRepository
import com.sortit.repo.SortLogRepository
import com.sortit.repo.TemplateRepository
import com.sortit.ui.DashboardViewModel
import com.sortit.ui.MainScreen
import com.sortit.ui.MonitorViewModel
import com.sortit.ui.ScanViewModel
import com.sortit.ui.SortitTheme
import com.sortit.ui.TemplateViewModel

class MainActivity : ComponentActivity() {

    private val templateRepo by lazy { TemplateRepository((application as SortitApplication).db) }
    private val monitorRepo by lazy { MonitorRepository((application as SortitApplication).db) }
    private val excludeRepo by lazy { ExcludeRepository((application as SortitApplication).db) }
    private val logRepo by lazy { SortLogRepository((application as SortitApplication).db) }
    private val scanRepo by lazy { ScanSessionRepository((application as SortitApplication).db) }

    private val scanVm by lazy {
        val app = application as SortitApplication
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ScanViewModel(
                    templateRepo, excludeRepo, scanRepo,
                    ScanSortUseCase(app.fileOps),
                    SortFilesUseCase(app.fileOps, app.db.sortLogDao())
                ) as T
        })[ScanViewModel::class.java]
    }

    private val templateVm by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TemplateViewModel(templateRepo, scanRepo) as T
        })[TemplateViewModel::class.java]
    }

    private val monitorVm by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MonitorViewModel(monitorRepo) as T
        })[MonitorViewModel::class.java]
    }

    private val dashboardVm by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DashboardViewModel(templateRepo, monitorRepo, logRepo, scanRepo) as T
        })[DashboardViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val useDynamic = (application as SortitApplication).prefs.useDynamicColor

        setContent {
            SortitTheme(useDynamicColor = useDynamic) {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen(dashboardVm, templateVm, monitorVm, scanVm)
                }
            }
        }
    }
}
