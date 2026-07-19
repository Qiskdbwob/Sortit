package com.sortit.repo

import com.sortit.data.AppDatabase
import com.sortit.data.ScanItemEntity
import com.sortit.data.ScanSessionEntity
import com.sortit.data.TemplateEntity
import com.sortit.util.SystemExcludes
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow

class ScanSessionRepository(private val db: AppDatabase) {

  fun signatureFor(templates: List<TemplateEntity>, excludePatterns: List<String>): String {
    val rulePart = templates.sortedBy { it.id }.joinToString("|") {
      listOf(it.id, it.name, it.extensions, it.targetTreeUri, it.sourceMode, it.sourceDirs ?: "", it.enabled).joinToString(":")
    }
    val raw = buildString {
      append("v2|").append(rulePart)
      append("|ex=").append(excludePatterns.sorted().joinToString(","))
      append("|sys=").append(SystemExcludes.PATHS.sorted().joinToString(","))
    }
    val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
  }

  suspend fun createSession(
    templates: List<TemplateEntity>,
    excludePatterns: List<String>,
    items: List<ScanItemEntity>
  ): Long {
    val now = System.currentTimeMillis()
    val sessionId = db.scanSessionDao().insert(
      ScanSessionEntity(
        createdAt = now,
        updatedAt = now,
        status = "PREVIEW",
        ruleIds = templates.map { it.id }.sorted().joinToString(","),
        signature = signatureFor(templates, excludePatterns),
        totalFound = items.size
      )
    )
    if (items.isNotEmpty()) {
      db.scanItemDao().insertAll(items.map { it.copy(sessionId = sessionId) })
    }
    return sessionId
  }

  suspend fun latestActive(): ScanSessionEntity? = db.scanSessionDao().latestActive()
  fun observeActive(): Flow<ScanSessionEntity?> = db.scanSessionDao().observeActive()
  fun observeGlobalPendingCount(): Flow<Int> = db.scanItemDao().observeGlobalPendingCount()

  suspend fun itemsForSession(sessionId: Long): List<ScanItemEntity> = db.scanItemDao().forSession(sessionId)
  fun observeItems(sessionId: Long): Flow<List<ScanItemEntity>> = db.scanItemDao().observeForSession(sessionId)

  suspend fun setExcluded(itemId: Long, excluded: Boolean) =
    db.scanItemDao().updateStatus(itemId, if (excluded) "EXCLUDED" else "PENDING")

  suspend fun setAllExcluded(sessionId: Long, excluded: Boolean) {
    db.scanItemDao().bulkUpdateStatus(sessionId, if (excluded) "EXCLUDED" else "PENDING")
  }

  suspend fun markResult(itemId: Long, status: String, dstPath: String?) =
    db.scanItemDao().updateResult(itemId, status, dstPath)

  suspend fun markDone(sessionId: Long) = db.scanSessionDao().done(sessionId, System.currentTimeMillis())
  suspend fun dismiss(sessionId: Long) = db.scanSessionDao().dismiss(sessionId, System.currentTimeMillis())

  suspend fun dismissForRules(ruleIds: Collection<Long>) {
    if (ruleIds.isEmpty()) return
    val wanted = ruleIds.map { it.toString() }.toSet()
    db.scanSessionDao().activeSessions()
      .filter { session -> session.ruleIds.split(",").any { wanted.contains(it.trim()) } }
      .forEach { db.scanSessionDao().dismiss(it.id, System.currentTimeMillis()) }
  }
}
