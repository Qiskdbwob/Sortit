package com.sortit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortit.data.TemplateEntity
import com.sortit.domain.FileItem
import com.sortit.domain.PreviewSortUseCase
import com.sortit.domain.ScanSortUseCase
import com.sortit.domain.SortFilesUseCase
import com.sortit.repo.ExcludeRepository
import com.sortit.repo.FileOps
import com.sortit.repo.SortLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface SortUiState {
    object Idle : SortUiState
    data class Scanning(val scanned: Int, val found: Int, val current: String) : SortUiState
    data class Preview(val items: List<FileItem>) : SortUiState
    data class Running(val total: Int, val done: Int, val failed: Int, val current: String) : SortUiState
    data class Done(val done: Int, val failed: Int) : SortUiState
}

class SortRunViewModel(
    private val preview: PreviewSortUseCase,
    private val scan: ScanSortUseCase,
    private val sort: SortFilesUseCase,
    private val excludeRepo: ExcludeRepository
) : ViewModel() {

    private val _state = MutableStateFlow<SortUiState>(SortUiState.Idle)
    val state: StateFlow<SortUiState> = _state

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected

    fun runPreview(template: TemplateEntity) = viewModelScope.launch {
        val excludes = excludeRepo.patternsFor(template.id).toSet()
        // pakai scan flow untuk progress, lalu preview list untuk gate
        var last = SortScanProgress(0, 0, "")
        scan.execute(template, excludes).collect { p ->
            last = SortScanProgress(p.scanned, p.found, p.currentPath)
            _state.value = SortUiState.Scanning(p.scanned, p.found, p.currentPath)
        }
        val items = preview.collect(template, excludes)
        _selected.value = items.map { it.path }.toSet()
        _state.value = SortUiState.Preview(items)
    }

    fun toggleSelect(path: String) {
        val cur = _selected.value.toMutableSet()
        if (cur.contains(path)) cur.remove(path) else cur.add(path)
        _selected.value = cur
    }

    fun execute(template: TemplateEntity) = viewModelScope.launch {
        val items = (state.value as? SortUiState.Preview)?.items ?: return@launch
        val chosen = items.filter { _selected.value.contains(it.path) }
        if (chosen.isEmpty()) return@launch
        sort.execute(template.id, template.targetTreeUri, chosen).collect { p ->
            _state.value = SortUiState.Running(p.total, p.done, p.failed, p.currentPath)
        }
        val s = state.value as? SortUiState.Running
        _state.value = SortUiState.Done(s?.done ?: 0, s?.failed ?: 0)
    }

    fun reset() { _state.value = SortUiState.Idle; _selected.value = emptySet() }
}

private data class SortScanProgress(val scanned: Int, val found: Int, val current: String)
