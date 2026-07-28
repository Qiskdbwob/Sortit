package com.sortit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortit.data.MonitorEntity
import com.sortit.domain.AutoApplyUseCase
import com.sortit.repo.MonitorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MonitorViewModel(
    private val repo: MonitorRepository,
    private val autoApply: AutoApplyUseCase? = null
) : ViewModel() {
    val monitors = repo.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _autoMsg = MutableStateFlow<String?>(null)
    val autoMsg: StateFlow<String?> = _autoMsg

    fun clearAutoMsg() { _autoMsg.value = null }

    fun toggle(id: Long, enabled: Boolean) = viewModelScope.launch {
        val m = repo.get(id) ?: return@launch
        repo.update(m.copy(enabled = enabled))
    }

    fun delete(m: MonitorEntity) = viewModelScope.launch { repo.delete(m) }

    fun add(name: String, path: String) = viewModelScope.launch {
        repo.insert(MonitorEntity(name = name, path = path))
    }

    fun update(m: MonitorEntity) = viewModelScope.launch {
        repo.update(m.copy(name = m.name.trim(), path = m.path.trim()))
    }

    fun runAutoFor(monitor: MonitorEntity) = viewModelScope.launch {
        val auto = autoApply ?: return@launch
        val r = auto.applyForMonitorPaths(monitor.path)
        _autoMsg.value = "Auto: pindah ${r.moved} · trash ${r.trashed} · gagal ${r.failed}"
    }

    fun runAutoAll() = viewModelScope.launch {
        val auto = autoApply ?: return@launch
        val r = auto.applyAllEnabled()
        _autoMsg.value = "Auto semua: pindah ${r.moved} · trash ${r.trashed} · gagal ${r.failed}"
    }
}
