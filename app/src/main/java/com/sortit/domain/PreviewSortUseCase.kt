package com.sortit.domain

import com.sortit.data.TemplateEntity
import com.sortit.repo.FileOps

// Hasilkan list kandidat untuk preview gate.
// Menggunakan FileScanner shared engine untuk filter logic.
class PreviewSortUseCase(private val fileOps: FileOps) {

    data class ScannedFile(
        val templateId: Long,
        val item: FileItem
    )

    fun collect(template: TemplateEntity, excludePatterns: Set<String>): List<FileItem> =
        collectMany(listOf(template), excludePatterns).map { it.item }

    fun collectMany(
        templates: List<TemplateEntity>,
        excludePatterns: Set<String>
    ): List<ScannedFile> {
        val seen = mutableSetOf<String>()
        return templates.filter { it.enabled }.flatMap { template ->
            FileScanner.scanTemplate(template, fileOps, excludePatterns, seen)
                .map { ScannedFile(it.templateId, it.item) }
        }.sortedWith(compareByDescending<ScannedFile> { it.item.isMedia }.thenBy { it.item.name.lowercase() })
    }
}
