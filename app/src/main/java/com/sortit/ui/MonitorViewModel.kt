package com.sortit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortit.data.MonitorEntity
import com.sortit.repo.MonitorRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MonitorViewModel(private val repo: MonitorRepository) : ViewModel() {
    val monitors = repo.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun toggle(id: Long, enabled: Boolean) = viewModelScope.launch {
        val m = repo.get(id) ?: return@launch
        repo.update(m.copy(enabled = enabled))
    }

    fun delete(m: MonitorEntity) = viewModelScope.launch { repo.delete(m) }

    fun add(name: String, path: String) = viewModelScope.launch {
        repo.insert(MonitorEntity(name = name, path = path))
    }
}
