package com.sortit.util

// Normalisasi daftar ekstensi dari input user: " .TXT, .bak ,tmp"
// -> setOf("txt","bak","tmp")
fun parseExtensions(raw: String): Set<String> =
    raw.split(',', ' ', ';')
        .map { it.trim().lowercase().removePrefix(".") }
        .filter { it.isNotBlank() && it.matches(Regex("[a-z0-9]+")) }
        .toSet()

fun matchesExtension(name: String, exts: Set<String>): Boolean {
    val dot = name.lastIndexOf('.')
    if (dot < 0 || dot == name.length - 1) return false
    return exts.contains(name.substring(dot + 1).lowercase())
}

// Potong nama file di tengah: "very_long_filename.jpg" → "very_lo…ame.jpg"
fun truncateFileName(name: String, maxLen: Int = 24): String {
    if (name.length <= maxLen) return name
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return name.take(maxLen / 2) + "\u2026" + name.takeLast(maxLen / 2 - 1)
    val base = name.substring(0, dot)
    val ext = name.substring(dot)
    val avail = maxLen - ext.length - 1 // 1 for ellipsis
    if (avail <= 4) return name.take(maxLen - 1) + "\u2026"
    return base.take(avail / 2 + 1) + "\u2026" + base.takeLast(avail / 2) + ext
}

// Buka path di file manager bawaan atau pilihan user
fun openPathInFileManager(context: android.content.Context, path: String) {
    try {
        val file = java.io.File(path)
        val parentDir = if (file.isDirectory) file else (file.parentFile ?: return)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", parentDir
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "vnd.android.document/directory")
            addCategory(android.content.Intent.CATEGORY_DEFAULT)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Buka dengan"))
    } catch (_: Exception) {
        // No file manager available or path invalid
    }
}
