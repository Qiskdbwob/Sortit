package com.sortit.domain

import com.sortit.data.SortLogDao
import com.sortit.data.TemplateEntity
import com.sortit.repo.ExcludeRepository
import com.sortit.repo.FileOps
import com.sortit.repo.TemplateRepository
import com.sortit.util.SystemExcludes
import com.sortit.util.matchesExtension
import com.sortit.util.parseExtensions
import com.sortit.util.extensionFolder
import com.sortit.util.splitMonitorPaths
import java.io.File

/**
 * Auto rule: file baru/ada di source folder rule (FOLDERS) langsung MOVE/TRASH
 * tanpa preview. Hormati system + global + per-rule exclude, size, age.
 */
class AutoApplyUseCase(
    private val fileOps: FileOps,
    private val templateRepo: TemplateRepository,
    private val excludeRepo: ExcludeRepository,
    private val logDao: SortLogDao
) {
    data class Result(
        val moved: Int = 0,
        val trashed: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0
    )

    suspend fun applyAllEnabled(): Result {
        val rules = templateRepo.getAllOnce().filter { it.enabled && it.autoEnabled }
        if (rules.isEmpty()) return Result()
        var moved = 0
        var trashed = 0
        var failed = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        for (rule in rules) {
            val r = applyOne(rule, now)
            moved += r.moved
            trashed += r.trashed
            failed += r.failed
            skipped += r.skipped
        }
        return Result(moved, trashed, failed, skipped)
    }

    /** Apply auto rules that touch given monitor paths (intersection with sourceDirs). */
    suspend fun applyForMonitorPaths(monitorPathCsvOrJoined: String): Result {
        val monitorPaths = splitMonitorPaths(monitorPathCsvOrJoined).map { it.trimEnd('/') }.toSet()
        if (monitorPaths.isEmpty()) return Result()
        val rules = templateRepo.getAllOnce().filter { it.enabled && it.autoEnabled }
        var acc = Result()
        val now = System.currentTimeMillis()
        for (rule in rules) {
            val roots = FileScanner.resolveRoots(rule).map { it.trimEnd('/') }
            val hit = roots.any { root ->
                monitorPaths.any { mp ->
                    root == mp || root.startsWith("$mp/") || mp.startsWith("$root/")
                }
            }
            if (!hit && rule.sourceMode != "ALL") continue
            // For ALL, only process files under monitor paths to avoid full-storage auto wipe
            val r = if (rule.sourceMode == "ALL") {
                applyOnRoots(rule, monitorPaths.toList(), now)
            } else {
                applyOne(rule, now)
            }
            acc = Result(
                moved = acc.moved + r.moved,
                trashed = acc.trashed + r.trashed,
                failed = acc.failed + r.failed,
                skipped = acc.skipped + r.skipped
            )
        }
        return acc
    }

    private suspend fun applyOne(rule: TemplateEntity, now: Long): Result {
        val roots = FileScanner.resolveRoots(rule)
        return applyOnRoots(rule, roots, now)
    }

    private suspend fun applyOnRoots(rule: TemplateEntity, roots: List<String>, now: Long): Result {
        val exts = parseExtensions(rule.extensions)
        if (exts.isEmpty()) return Result()
        val excludes = excludeRepo.patternsForScan(listOf(rule.id))
        val actionTrash = rule.autoAction.equals("TRASH", ignoreCase = true)
        val baseDir = if (actionTrash) FileOps.TRASH_ROOT else rule.targetTreeUri
        fileOps.mkdirs(baseDir)

        var moved = 0
        var trashed = 0
        var failed = 0
        var skipped = 0

        for (root in roots) {
            if (SystemExcludes.isSystemPath(root) || FileScanner.isRootExcluded(root, excludes)) {
                skipped++; continue
            }
            if (!fileOps.isReadableDir(root)) { skipped++; continue }
            // Rekursif (walkDeep) supaya konsisten dengan scan manual.
            val files = try { fileOps.walkDeep(root).toList() } catch (_: Exception) { emptyList() }
            for (f in files) {
                val path = f.absolutePath
                if (SystemExcludes.isSystemPath(path)) { skipped++; continue }
                if (!matchesExtension(f.name, exts)) { skipped++; continue }
                if (FileScanner.isExcluded(path, f.name, excludes)) { skipped++; continue }
                if (!FileScanner.matchesMeta(rule, f.length(), f.lastModified(), now)) { skipped++; continue }

                val sub = "$baseDir/${extensionFolder(f.name)}"
                fileOps.mkdirs(sub)
                val dst = fileOps.move(path, sub)
                if (dst != null) {
                    logDao.insert(
                        com.sortit.data.SortLogEntity(
                            templateId = rule.id,
                            fileName = f.name,
                            srcPath = path,
                            dstPath = dst,
                            status = "OK",
                            size = f.length()
                        )
                    )
                    if (actionTrash) trashed++ else moved++
                } else {
                    failed++
                    logDao.insert(
                        com.sortit.data.SortLogEntity(
                            templateId = rule.id,
                            fileName = f.name,
                            srcPath = path,
                            dstPath = "",
                            status = "FAIL",
                            size = f.length()
                        )
                    )
                }
            }
        }
        return Result(moved, trashed, failed, skipped)
    }
}
