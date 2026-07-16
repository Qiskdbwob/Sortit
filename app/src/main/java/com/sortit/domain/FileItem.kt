package com.sortit.domain

data class FileItem(
    val path: String,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val lastModified: Long,
    val isMedia: Boolean
)
