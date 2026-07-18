package com.sortit.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File

object LinkUtils {

  fun openInFileManager(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    try {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      val parent = file.parentFile ?: return
      try {
        val parentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", parent)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(parentUri, DocumentsContract.Document.MIME_TYPE_DIR)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
      } catch (_: Exception) {
        try {
          context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), DocumentsContract.Document.MIME_TYPE_DIR)
          })
        } catch (_: Exception) { /* silently fail */ }
      }
    }
  }
}
