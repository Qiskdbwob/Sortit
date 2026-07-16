package com.sortit.repo

import java.io.File

// Abstraksi akses storage via all-files (MANAGE_EXTERNAL_STORAGE).
// Semua operasi file melewati ini supaya mudah di-mock saat test.
interface FileOps {
    fun listFiles(dir: String): List<File>
    fun walkDeep(dir: String): Sequence<File>
    fun exists(path: String): Boolean
    fun size(path: String): Long
    fun lastModified(path: String): Long
    fun mimeOf(file: File): String?
    fun move(src: String, dstDir: String): String?   // return dst path, null jika gagal
    fun mkdirs(dir: String): Boolean
}

class RealFileOps : FileOps {
    override fun listFiles(dir: String): List<File> {
        val d = File(dir)
        return if (d.isDirectory) d.listFiles()?.toList() ?: emptyList() else emptyList()
    }

    override fun walkDeep(dir: String): Sequence<File> =
        File(dir).walkTopDown().filter { it.isFile }

    override fun exists(path: String): Boolean = File(path).exists()
    override fun size(path: String): Long = File(path).length()
    override fun lastModified(path: String): Long = File(path).lastModified()

    override fun mimeOf(file: File): String? {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".webp") -> "image/webp"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") -> "video/*"
            name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg") -> "audio/*"
            name.endsWith(".pdf") -> "application/pdf"
            else -> null
        }
    }

    override fun move(src: String, dstDir: String): String? {
        val s = File(src)
        if (!s.exists()) return null
        val d = File(dstDir)
        if (!d.exists()) d.mkdirs()
        var target = File(d, s.name)
        // konflik nama -> rename name(1).ext
        var i = 1
        while (target.exists()) {
            val dot = s.name.lastIndexOf('.')
            val base = if (dot > 0) s.name.substring(0, dot) else s.name
            val ext = if (dot > 0) s.name.substring(dot) else ""
            target = File(d, "${base}($i)$ext")
            i++
        }
        return if (s.renameTo(target)) target.absolutePath else null
    }

    override fun mkdirs(dir: String): Boolean = File(dir).mkdirs()
}
