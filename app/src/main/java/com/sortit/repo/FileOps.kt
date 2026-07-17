package com.sortit.repo

import android.webkit.MimeTypeMap
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
    fun moveToTrash(src: String): String?            // pindah ke TRASH_ROOT ext-subfolder
    fun cleanupOldTrash(maxAgeDays: Long = 14)
    fun restoreFromTrash(src: String, dstDir: String): String?  // pulihkan dari trash
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
        val ext = file.extension.lowercase()
        return if (ext.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) else null
    }

    override fun move(src: String, dstDir: String): String? {
        val s = File(src)
        if (!s.exists() || !s.isFile) return null
        val d = File(dstDir)
        if (!d.exists()) d.mkdirs()

        var target = File(d, s.name)
        if (s.absolutePath == target.absolutePath) return target.absolutePath

        // konflik nama -> rename name(1).ext
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
        val ext = File(src).extension.lowercase()
        val trashDir = if (ext.isNotEmpty()) "$TRASH_ROOT/$ext" else TRASH_ROOT
        mkdirs(trashDir)
        return move(src, trashDir)
    }

    override fun cleanupOldTrash(maxAgeDays: Long) {
        val trashDir = File(TRASH_ROOT)
        if (!trashDir.exists()) return
        val cutoff = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000)
        trashDir.walkTopDown().filter { it.isFile }.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    override fun restoreFromTrash(src: String, dstDir: String): String? {
        return move(src, dstDir)
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
