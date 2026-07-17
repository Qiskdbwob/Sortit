package com.sortit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortit.data.ScanItemEntity
import com.sortit.data.ScanSessionEntity
import com.sortit.data.TemplateEntity
import com.sortit.domain.FileItem
import com.sortit.domain.PreviewSortUseCase
import com.sortit.domain.ScanSortUseCase
import com.sortit.domain.SortFilesUseCase
import com.sortit.repo.ExcludeRepository
import com.sortit.repo.ScanSessionRepository
import com.sortit.repo.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ScanUiState {
    object Idle : ScanUiState
    data class ExistingPending(val session: ScanSessionEntity, val requestedRuleIds: List<Long>) : ScanUiState
    data class Scanning(val scanned: Int, val found: Int, val current: String) : ScanUiState
    data class Empty(val message: String) : ScanUiState
    data class Preview(
        val sessionId: Long,
        val items: List<ScanItemEntity>,
        val templates: Map<Long, TemplateEntity>
    ) : ScanUiState
    data class Running(
        val total: Int,
        val done: Int,
        val failed: Int,
        val current: String,
        val action: SortFilesUseCase.Action
    ) : ScanUiState
    data class Done(val moved: Int, val trashed: Int, val failed: Int) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

class ScanViewModel(
    private val templateRepo: TemplateRepository,
    private val excludeRepo: ExcludeRepository,
    private val scanRepo: ScanSessionRepository,
    private val scan: ScanSortUseCase,
    private val sort: SortFilesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state

    fun requestScan(ruleIds: List<Long>) = viewModelScope.launch {
        val templates = ruleIds.distinct().mapNotNull { templateRepo.get(it) }.filter { it.enabled }
        if (templates.isEmpty()) {
            _state.value = ScanUiState.Error("Pilih minimal 1 rule aktif untuk discan.")
            return@launch
        }
        val active = scanRepo.latestActive()
        if (active != null) {
            _state.value = ScanUiState.ExistingPending(active, templates.map { it.id })
            return@launch
        }
        startScan(templates)
    }

    fun continuePending(session: ScanSessionEntity) = viewModelScope.launch {
        val items = scanRepo.itemsForSession(session.id)
        if (items.isEmpty()) {
            scanRepo.dismiss(session.id)
            _state.value = ScanUiState.Idle
            return@launch
        }
        val templates = session.ruleIds.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .mapNotNull { templateRepo.get(it) }
            .associateBy { it.id }
        _state.value = ScanUiState.Preview(session.id, items, templates)
    }

    fun rescanPending(session: ScanSessionEntity, requestedRuleIds: List<Long>) = viewModelScope.launch {
        scanRepo.dismiss(session.id)
        val templates = requestedRuleIds.distinct().mapNotNull { templateRepo.get(it) }.filter { it.enabled }
        if (templates.isEmpty()) {
            _state.value = ScanUiState.Error("Rule untuk scan ulang tidak ditemukan.")
            return@launch
        }
        startScan(templates)
    }

    fun dismissPending(session: ScanSessionEntity) = viewModelScope.launch {
        scanRepo.dismiss(session.id)
        _state.value = ScanUiState.Idle
    }

    fun closePreview() { _state.value = ScanUiState.Idle }

    fun toggleExcluded(itemId: Long, excluded: Boolean) = viewModelScope.launch {
        scanRepo.setExcluded(itemId, excluded)
        val s = _state.value
        if (s is ScanUiState.Preview) {
            _state.value = s.copy(items = s.items.map {
                if (it.id == itemId) it.copy(status = if (excluded) "EXCLUDED" else "PENDING") else it
            })
        }
    }

    fun setAllExcluded(excluded: Boolean) = viewModelScope.launch {
        val s = _state.value as? ScanUiState.Preview ?: return@launch
        scanRepo.setAllExcluded(s.sessionId, excluded)
        _state.value = s.copy(items = s.items.map {
            if (it.status == "PENDING" || it.status == "EXCLUDED") it.copy(status = if (excluded) "EXCLUDED" else "PENDING") else it
        })
    }

    fun executePending(action: SortFilesUseCase.Action) = viewModelScope.launch {
        val s = _state.value as? ScanUiState.Preview ?: return@launch
        val pending = s.items.filter { it.status == "PENDING" }
        if (pending.isEmpty()) return@launch

        var done = 0
        var failed = 0
        _state.value = ScanUiState.Running(pending.size, 0, 0, "", action)
        val byPath = pending.associateBy { it.path }

        pending.groupBy { it.templateId }.forEach { (templateId, items) ->
            val template = s.templates[templateId] ?: return@forEach
            val fileItems = items.map { FileItem(it.path, it.name, it.size, it.mimeType, it.lastModified, it.isMedia) }
            sort.execute(
                templateId = template.id,
                targetDir = template.targetTreeUri,
                items = fileItems,
                action = action,
                onItemResult = { src, dst, status ->
                    val scanItem = byPath[src]
                    if (scanItem != null) {
                        val mapped = when {
                            status == "OK" && action == SortFilesUseCase.Action.MOVE -> "MOVED"
                            status == "OK" && action == SortFilesUseCase.Action.TRASH -> "TRASHED"
                            else -> "FAILED"
                        }
                        scanRepo.markResult(scanItem.id, mapped, dst)
                    }
                    if (status == "OK") done++ else failed++
                    _state.value = ScanUiState.Running(pending.size, done, failed, src, action)
                }
            ).collect { }
        }

        scanRepo.markDone(s.sessionId)
        _state.value = ScanUiState.Done(
            moved = if (action == SortFilesUseCase.Action.MOVE) done else 0,
            trashed = if (action == SortFilesUseCase.Action.TRASH) done else 0,
            failed = failed
        )
    }

    fun reset() { _state.value = ScanUiState.Idle }

    private suspend fun startScan(templates: List<TemplateEntity>) {
        val excludes = templates.flatMap { excludeRepo.patternsFor(it.id) }.distinct()
        val candidates = mutableListOf<PreviewSortUseCase.ScannedFile>()
        _state.value = ScanUiState.Scanning(0, 0, "")
        scan.executeMany(templates, excludes.toSet()) { templateId, item ->
            candidates.add(PreviewSortUseCase.ScannedFile(templateId, item))
        }.collect { p ->
            _state.value = ScanUiState.Scanning(p.scanned, p.found, p.currentPath)
        }

        if (candidates.isEmpty()) {
            _state.value = ScanUiState.Empty("Tidak ada file yang cocok dengan rule terpilih.")
            return
        }

        val items = candidates.map {
            ScanItemEntity(
                sessionId = 0,
                templateId = it.templateId,
                path = it.item.path,
                name = it.item.name,
                size = it.item.size,
                mimeType = it.item.mimeType,
                lastModified = it.item.lastModified,
                isMedia = it.item.isMedia,
                status = "PENDING"
            )
        }
        val sessionId = scanRepo.createSession(templates, excludes, items)
        val saved = scanRepo.itemsForSession(sessionId)
        _state.value = ScanUiState.Preview(sessionId, saved, templates.associateBy { it.id })
    }
}
