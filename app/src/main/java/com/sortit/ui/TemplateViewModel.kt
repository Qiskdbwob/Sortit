package com.sortit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortit.data.TemplateEntity
import com.sortit.repo.ScanSessionRepository
import com.sortit.repo.TemplateRepository
import com.sortit.util.parseExtensions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplateViewModel(
    private val repo: TemplateRepository,
    private val scanRepo: ScanSessionRepository
) : ViewModel() {
    val templates = repo.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun toggle(id: Long, enabled: Boolean) = viewModelScope.launch {
        val t = repo.get(id) ?: return@launch
        scanRepo.dismissForRules(listOf(id))
        repo.update(t.copy(enabled = enabled))
    }

    fun delete(t: TemplateEntity) = viewModelScope.launch {
        scanRepo.dismissForRules(listOf(t.id))
        repo.delete(t)
    }

    fun add(name: String, extensions: String, target: String, mode: String, dirs: String?) =
        viewModelScope.launch {
            val normalizedExt = parseExtensions(extensions).joinToString(",")
            repo.insert(
                TemplateEntity(
                    name = name.trim(),
                    extensions = normalizedExt,
                    targetTreeUri = target.trim(),
                    sourceMode = mode,
                    sourceDirs = dirs?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.joinToString(",")
                )
            )
        }

    fun update(template: TemplateEntity) = viewModelScope.launch {
        scanRepo.dismissForRules(listOf(template.id))
        repo.update(
            template.copy(
                name = template.name.trim(),
                extensions = parseExtensions(template.extensions).joinToString(","),
                targetTreeUri = template.targetTreeUri.trim(),
                sourceDirs = template.sourceDirs?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.joinToString(",")
            )
        )
    }
}
