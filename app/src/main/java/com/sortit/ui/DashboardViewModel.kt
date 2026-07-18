package com.sortit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortit.repo.MonitorRepository
import com.sortit.repo.ScanSessionRepository
import com.sortit.repo.SortLogRepository
import com.sortit.repo.TemplateRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    templateRepo: TemplateRepository,
    monitorRepo: MonitorRepository,
    logRepo: SortLogRepository,
    scanRepo: ScanSessionRepository
) : ViewModel() {
    val templates = templateRepo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val monitors = monitorRepo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val counts = logRepo.observeCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.sortit.repo.SortCounts())
    val recentLogs = logRepo.observeRecent(10).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingScanCount = scanRepo.observeGlobalPendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val activeScan = scanRepo.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
