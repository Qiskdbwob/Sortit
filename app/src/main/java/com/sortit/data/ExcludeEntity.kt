package com.sortit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Exclude pattern.
 * - templateId > 0  → per-rule exclude
 * - templateId = 0  → global user exclude (berlaku semua scan)
 * pattern: path absolut (prefix folder) atau nama file exact.
 */
@Entity(tableName = "excludes", indices = [androidx.room.Index("templateId")])
data class ExcludeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val pattern: String
) {
    companion object {
        const val GLOBAL_TEMPLATE_ID: Long = 0L
    }
}
