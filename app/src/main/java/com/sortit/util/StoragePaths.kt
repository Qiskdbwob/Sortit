package com.sortit.util

import android.net.Uri
import android.provider.DocumentsContract

// Util kecil untuk memetakan tree URI SAF primary storage ke path absolut.
// Jika URI bukan primary external storage, return null dan UI akan menyimpan URI mentah.
object StoragePaths {
    fun uriToPath(uri: Uri): String? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(':', limit = 2)
        if (parts.isEmpty() || parts[0] != "primary") return@runCatching null
        val rel = parts.getOrNull(1)?.trim('/') ?: ""
        if (rel.isBlank()) "/storage/emulated/0" else "/storage/emulated/0/$rel"
    }.getOrNull()

    fun displayPath(pathOrUri: String): String =
        if (pathOrUri.startsWith("content://")) "SAF: $pathOrUri" else pathOrUri
}
