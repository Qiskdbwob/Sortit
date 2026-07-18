package com.sortit.repo

import android.webkit.MimeTypeMap
import java.io.File

interface FileOps {
  fun listFiles(dir: String): List<File>
  fun walkDeep(dir: String): Sequence<File>
  fun exists(path: String): Boolean
  fun size(path: String): Long
  fun lastModified(path: String): Long
  fun mimeOf(file: File): String?
  fun move(src: String, dstDir: String): String?
  fun moveToTrash(src: String): String?
  fun mkdirs(dir: String): Boolean
  fun isReadableDir(path: String): Boolean
  fun childCount(path: String): Int

  companion object {
    const val TRASH_ROOT: String = "/storage/emulated/0/Sortit/.sortit-trash"
  }
}

class RealFileOps : FileOps {
  override fun listFiles(dir: String): List<File> {
    val d = File(dir)
    return if (d.isDirectory && d.canRead()) d.listFiles()?.toList() ?: emptyList() else emptyList()
  }

  override fun walkDeep(dir: String): Sequence<File> =
    File(dir).walkTopDown().filter { it.isFile }

  override fun exists(path: String): Boolean = File(path).exists()
  override fun size(path: String): Long = File(path).length()
  override fun lastModified(path: String): Long = File(path).lastModified()

  override fun mimeOf(file: File): String? {
    val name = file.name.lowercase()
    val known = when {
      name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
      name.endsWith(".png") -> "image/png"
      name.endsWith(".webp") -> "image/webp"
      name.endsWith(".gif") -> "image/gif"
      name.endsWith(".mp4") -> "video/mp4"
      name.endsWith(".mkv") -> "video/x-matroska"
      name.endsWith(".webm") -> "video/webm"
      name.endsWith(".mp3") -> "audio/mpeg"
      name.endsWith(".wav") -> "audio/wav"
      name.endsWith(".ogg") -> "audio/ogg"
      name.endsWith(".pdf") -> "application/pdf"
      name.endsWith(".doc") || name.endsWith(".docx") -> "application/msword"
      name.endsWith(".xls") || name.endsWith(".xlsx") -> "application/vnd.ms-excel"
      name.endsWith(".ppt") || name.endsWith(".pptx") -> "application/vnd.ms-powerpoint"
      name.endsWith(".heic") || name.endsWith(".heif") -> "image/heic"
      name.endsWith(".avif") -> "image/avif"
      name.endsWith(".opus") -> "audio/opus"
      name.endsWith(".flac") -> "audio/flac"
      name.endsWith(".zip") -> "application/zip"
      name.endsWith(".rar") -> "application/x-rar-compressed"
      name.endsWith(".7z") -> "application/x-7z-compressed"
      name.endsWith(".txt") -> "text/plain"
      name.endsWith(".csv") -> "text/csv"
      name.endsWith(".json") -> "application/json"
      name.endsWith(".xml") -> "application/xml"
      name.endsWith(".html") || name.endsWith(".htm") -> "text/html"
      else -> null
    }
    if (known != null) return known
    val ext = name.substringAfterLast('.', "")
    return if (ext.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) else null
  }

  override fun move(src: String, dstDir: String): String? {
    val s = File(src)
    if (!s.exists() || !s.isFile) return null
    val d = File(dstDir)
    if (!d.exists()) d.mkdirs()

    var target = File(d, s.name)
    if (s.absolutePath == target.absolutePath) return target.absolutePath

    var i = 1
    while (target.exists()) {
      val dot = s.name.lastIndexOf('.')
      val base = if (dot > 0) s.name.substring(0, dot) else s.name
      val ext = if (dot > 0) s.name.substring(dot) else ""
      target = File(d, "${base}($i)$ext")
      i++
    }

    if (s.renameTo(target)) return target.absolutePath
    return copyThenDelete(s, target)
  }

  override fun moveToTrash(src: String): String? {
    mkdirs(FileOps.TRASH_ROOT)
    return move(src, FileOps.TRASH_ROOT)
  }

  override fun mkdirs(dir: String): Boolean = File(dir).mkdirs() || File(dir).isDirectory

  override fun isReadableDir(path: String): Boolean {
    val d = File(path)
    return d.exists() && d.isDirectory && d.canRead()
  }

  override fun childCount(path: String): Int = listFiles(path).size

  private fun copyThenDelete(src: File, dst: File): String? {
    return try {
      dst.parentFile?.mkdirs()
      src.inputStream().use { input ->
        dst.outputStream().use { output -> input.copyTo(output) }
      }
      if (src.length() == dst.length() && src.delete()) dst.absolutePath else {
        if (dst.exists() && src.exists()) dst.delete()
        null
      }
    } catch (_: Exception) {
      null
    }
  }
}
