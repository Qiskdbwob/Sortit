package com.sortit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortit.data.SortLogEntity
import com.sortit.repo.FileOps
import com.sortit.repo.MonitorRepository
import com.sortit.repo.ScanSessionRepository
import com.sortit.repo.SortLogRepository
import com.sortit.repo.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DashboardViewModel(
    templateRepo: TemplateRepository,
    monitorRepo: MonitorRepository,
    private val logRepo: SortLogRepository,
    scanRepo: ScanSessionRepository
) : ViewModel() {
    val templates = templateRepo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val monitors = monitorRepo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val counts = logRepo.observeCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.sortit.repo.SortCounts())
    val recentLogs = logRepo.observeRecent(10).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingScanCount = scanRepo.observeGlobalPendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val activeScan = scanRepo.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _trashedFiles = MutableStateFlow<List<SortLogEntity>>(emptyList())
    val trashedFiles: StateFlow<List<SortLogEntity>> = _trashedFiles

    init { refreshTrashed() }

    fun refreshTrashed() = viewModelScope.launch {
        _trashedFiles.value = logRepo.getTrashedFiles(5)
    }

    suspend fun restoreFile(log: SortLogEntity, fileOps: FileOps): Boolean {
        val parentDir = File(log.srcPath).parent ?: return false
        val restored = fileOps.restoreFromTrash(log.dstPath, parentDir)
        if (restored != null) {
            logRepo.markRestored(log.id)
            refreshTrashed()
            return true
        }
        return false
    }
}
