package com.sortit.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File

object LinkUtils {

  /**
   * Buka folder di file manager.
   * Strategy:
   * 1. Sortit paths → FileProvider (reliable untuk app-owned paths)
   * 2. Semua path lain → file:// URI langsung (MANAGE_EXTERNAL_STORAGE cover)
   * 3. Fallback → FileProvider parent folder
   *
   * Catatan: Behavior navigate ke folder spesifik tergantung file manager.
   * Google Files, Samsung My Files, dll punya behavior berbeda.
   */
  fun openInFileManager(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) return

    // Step 1: Sortit-owned paths via FileProvider
    if (path.startsWith("/storage/emulated/0/Sortit")) {
      try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        return
      } catch (_: Exception) { /* fallback */ }
    }

    // Step 2: file:// URI langsung (work karena MANAGE_EXTERNAL_STORAGE)
    try {
      val uri = Uri.fromFile(file)
      context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
      })
      return
    } catch (_: Exception) { /* fallback */ }

    // Step 3: FileProvider fallback
    try {
      val target = if (file.isDirectory) file else (file.parentFile ?: return)
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
      context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      })
    } catch (_: Exception) {
      // Last resort
      try {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3A"),
            DocumentsContract.Document.MIME_TYPE_DIR
          )
        })
      } catch (_: Exception) { /* silently fail */ }
    }
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
