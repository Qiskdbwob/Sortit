package com.sortit.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import java.io.File

object LinkUtils {

  /**
   * Buka folder di file manager.
   * Strategy: FileProvider untuk semua path.
   * Karena Android tidak punya intent universal navigate ke folder spesifik,
   * fallback ke DocumentsContract URI untuk SAF-compatible file manager.
   *
   * Tips: Jika file manager tidak navigate sesuai, install Material Files
   * atau Cx File Explorer yang support SAF document URI.
   */
  fun openInFileManager(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) return

    // Step 1: FileProvider untuk semua path
    try {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      })
      return
    } catch (_: Exception) { /* fallback */ }

    // Step 2: DocumentsContract URI (SAF authority)
    try {
      val docId = "primary:${path.removePrefix("/storage/emulated/0/")}".trimEnd('/')
      val uri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents", docId
      )
      context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      })
      return
    } catch (_: Exception) { /* fallback */ }

    // Step 3: parent folder
    try {
      val target = if (file.isDirectory) file else (file.parentFile ?: return)
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
      context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      })
    } catch (_: Exception) { /* silent */ }
  }

  /** Buka file media via galeri/pemutar default. */
  fun openMedia(context: Context, path: String, mimeType: String?) {
    val file = File(path)
    if (!file.exists()) return
    try {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      })
    } catch (_: Exception) {
      openInFileManager(context, path)
    }
  }
}
