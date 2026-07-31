package com.sortit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortit.data.TemplateEntity
import com.sortit.repo.ExcludeRepository
import com.sortit.repo.ScanSessionRepository
import com.sortit.repo.TemplateRepository
import com.sortit.util.parseExtensions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplateViewModel(
    private val repo: TemplateRepository,
    private val scanRepo: ScanSessionRepository,
    private val excludeRepo: ExcludeRepository
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

    fun add(
        name: String,
        extensions: String,
        target: String,
        mode: String,
        dirs: String?,
        minSizeBytes: Long = 0,
        maxSizeBytes: Long = 0,
        maxAgeDays: Int = 0,
        autoEnabled: Boolean = false,
        autoAction: String = "MOVE",
        excludePaths: List<String> = emptyList()
    ) = viewModelScope.launch {
        val normalizedExt = parseExtensions(extensions).joinToString(",")
        val id = repo.insert(
            TemplateEntity(
                name = name.trim(),
                extensions = normalizedExt,
                targetTreeUri = target.trim(),
                sourceMode = mode,
                sourceDirs = dirs?.let { com.sortit.domain.FileScanner.splitSourceDirs(it).joinToString("\n") },
                minSizeBytes = minSizeBytes.coerceAtLeast(0),
                maxSizeBytes = maxSizeBytes.coerceAtLeast(0),
                maxAgeDays = maxAgeDays.coerceAtLeast(0),
                autoEnabled = autoEnabled,
                autoAction = if (autoAction.equals("TRASH", true)) "TRASH" else "MOVE"
            )
        )
        excludePaths.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.distinct().forEach {
            excludeRepo.add(id, it)
        }
    }

    fun update(
        template: TemplateEntity,
        excludePaths: List<String>? = null
    ) = viewModelScope.launch {
        scanRepo.dismissForRules(listOf(template.id))
        repo.update(
            template.copy(
                name = template.name.trim(),
                extensions = parseExtensions(template.extensions).joinToString(","),
                targetTreeUri = template.targetTreeUri.trim(),
                sourceDirs = template.sourceDirs?.let { com.sortit.domain.FileScanner.splitSourceDirs(it).joinToString("\n") },
                minSizeBytes = template.minSizeBytes.coerceAtLeast(0),
                maxSizeBytes = template.maxSizeBytes.coerceAtLeast(0),
                maxAgeDays = template.maxAgeDays.coerceAtLeast(0),
                autoAction = if (template.autoAction.equals("TRASH", true)) "TRASH" else "MOVE"
            )
        )
        if (excludePaths != null) {
            val current = excludeRepo.patternsFor(template.id).map { it.trimEnd('/') }.toSet()
            val wanted = excludePaths.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.toSet()
            (current - wanted).forEach { excludeRepo.remove(template.id, it) }
            (wanted - current).forEach { excludeRepo.add(template.id, it) }
        }
    }

    suspend fun excludesFor(templateId: Long): List<String> = excludeRepo.patternsFor(templateId)
}
