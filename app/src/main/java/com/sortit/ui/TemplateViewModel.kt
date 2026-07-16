package com.sortit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortit.data.TemplateEntity
import com.sortit.repo.TemplateRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplateViewModel(private val repo: TemplateRepository) : ViewModel() {
    val templates = repo.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun toggle(id: Long, enabled: Boolean) = viewModelScope.launch {
        val t = repo.get(id) ?: return@launch
        repo.update(t.copy(enabled = enabled))
    }

    fun delete(t: TemplateEntity) = viewModelScope.launch { repo.delete(t) }

    fun add(name: String, extensions: String, target: String, mode: String, dirs: String?) =
        viewModelScope.launch {
            repo.insert(
                TemplateEntity(
                    name = name,
                    extensions = extensions,
                    targetTreeUri = target,
                    sourceMode = mode,
                    sourceDirs = dirs
                )
            )
        }
}
