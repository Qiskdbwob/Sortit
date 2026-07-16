package com.sortit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "excludes", indices = [androidx.room.Index("templateId")])
data class ExcludeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val pattern: String              // path atau nama file yang dikecualikan
)
