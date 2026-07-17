package com.sortit.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "templates",
    indices = [Index(value = ["name"], unique = true)]
)
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val extensions: String,          // CSV lowercase tanpa titik, misal "txt,bak,tmp"
    val targetTreeUri: String,       // folder tujuan (path absolut karena all-files; SAF dipetakan ke path bila memungkinkan)
    val sourceMode: String,          // "ALL" | "FOLDERS"
    val sourceDirs: String?,         // CSV path, null jika mode ALL
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
