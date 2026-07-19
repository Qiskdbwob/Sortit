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
   * Strategy:
   * 1. Sortit paths → FileProvider content:// (reliable)
   * 2. Semua path lain → FileProvider + vnd.android.document/directory
   * 3. Fallback → buka parent folder
   */
  fun openInFileManager(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) return

    // Step 1 & 2: FileProvider untuk semua path
    try {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "vnd.android.document/directory")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      })
      return
    } catch (_: Exception) { /* fallback */ }

    // Step 3: parent folder
    try {
      val target = if (file.isDirectory) file else (file.parentFile ?: return)
      val parentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
      context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(parentUri, "vnd.android.document/directory")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      })
    } catch (_: Exception) {
      // Last resort: root storage via SAF authority
      try {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3A"),
            "vnd.android.document/directory"
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
