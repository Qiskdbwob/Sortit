package com.sortit.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File

object LinkUtils {

  /**
   * Buka folder di file manager. Navigate ke path spesifik.
   * Semua path lewat FileProvider (content://) + ACTION_VIEW + MIME_TYPE_DIR.
   * File manager yang support akan navigate ke folder tsb.
   */
  fun openInFileManager(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) return

    // Sortit-owned paths via FileProvider
    if (path.startsWith("/storage/emulated/0/Sortit")) {
      try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        return
      } catch (_: Exception) { /* fallback below */ }
    }

    // Semua path lain: coba FileProvider langsung
    // file_paths.xml punya <external-path path="." /> → cover semua external storage
    try {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      })
      return
    } catch (_: Exception) { /* fallback below */ }

    // Fallback: buka parent folder
    try {
      val target = if (file.isDirectory) file else (file.parentFile ?: return)
      val parentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
      context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(parentUri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      })
    } catch (_: Exception) {
      // Last resort: root storage
      try {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), DocumentsContract.Document.MIME_TYPE_DIR)
        })
      } catch (_: Exception) { /* silently fail */ }
    }
  }

  /** Buka file media (image/video/audio) via galeri atau pemutar default. */
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
