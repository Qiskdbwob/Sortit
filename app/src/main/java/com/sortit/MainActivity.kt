package com.sortit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sortit.domain.AutoApplyUseCase
import com.sortit.domain.ScanSortUseCase
import com.sortit.domain.SortFilesUseCase
import com.sortit.repo.ExcludeRepository
import com.sortit.repo.MonitorRepository
import com.sortit.repo.ScanSessionRepository
import com.sortit.repo.SortLogRepository
import com.sortit.repo.TemplateRepository
import com.sortit.ui.DashboardViewModel
import com.sortit.ui.HistoryViewModel
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
    private val autoApply by lazy {
        val app = application as SortitApplication
        AutoApplyUseCase(app.fileOps, templateRepo, excludeRepo, app.db.sortLogDao())
    }

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
                TemplateViewModel(templateRepo, scanRepo, excludeRepo) as T
        })[TemplateViewModel::class.java]
    }

    private val monitorVm by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MonitorViewModel(
                    monitorRepo, autoApply,
                    (application as SortitApplication).fileOps,
                    (application as SortitApplication).db.sortLogDao()
                ) as T
        })[MonitorViewModel::class.java]
    }

    private val dashboardVm by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DashboardViewModel(templateRepo, monitorRepo, logRepo, scanRepo) as T
        })[DashboardViewModel::class.java]
    }

    private val historyVm by lazy {
        val app = application as SortitApplication
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HistoryViewModel(logRepo, app.fileOps) as T
        })[HistoryViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SortitApplication

        setContent {
            var useDynamic by remember { mutableStateOf(app.prefs.useDynamicColor) }
            var themeMode by remember { mutableStateOf(app.prefs.themeMode) }
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> null
            }
            SortitTheme(useDynamicColor = useDynamic, darkTheme = darkTheme) {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen(
                        dashboardVm = dashboardVm,
                        templateVm = templateVm,
                        monitorVm = monitorVm,
                        scanVm = scanVm,
                        historyVm = historyVm,
                        onDynamicColorChange = { enabled ->
                            app.prefs.useDynamicColor = enabled
                            useDynamic = enabled
                        },
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            app.prefs.themeMode = mode
                            themeMode = mode
                        }
                    )
                }
            }
        }
    }
}
