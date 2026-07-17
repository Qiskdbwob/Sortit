package com.sortit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SortitApplication
        val templateRepo = TemplateRepository(app.db)
        val monitorRepo = MonitorRepository(app.db)
        val excludeRepo = ExcludeRepository(app.db)
        val logRepo = SortLogRepository(app.db)
        val scanRepo = ScanSessionRepository(app.db)

        val templateVm = TemplateViewModel(templateRepo, scanRepo)
        val monitorVm = MonitorViewModel(monitorRepo)
        val dashboardVm = DashboardViewModel(templateRepo, monitorRepo, logRepo, scanRepo)
        val scanVm = ScanViewModel(
            templateRepo = templateRepo,
            excludeRepo = excludeRepo,
            scanRepo = scanRepo,
            scan = ScanSortUseCase(app.fileOps),
            sort = SortFilesUseCase(app.fileOps, app.db.sortLogDao())
        )

        setContent {
            SortitTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen(dashboardVm, templateVm, monitorVm, scanVm)
                }
            }
        }
    }
}
