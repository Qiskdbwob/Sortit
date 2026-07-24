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

/**
 * Potong nama file di TENGAH agar ekstensi tetap kelihatan.
 * "very_long_filename_photo.jpg" → "very_lo...photo.jpg"
 */
fun truncateFileName(name: String, maxLen: Int = 28): String {
    if (name.length <= maxLen) return name
    val dot = name.lastIndexOf('.')
    if (dot <= 0) {
        val half = (maxLen - 1) / 2
        return name.take(half) + "\u2026" + name.takeLast(maxLen - 1 - half)
    }
    val base = name.substring(0, dot)
    val ext = name.substring(dot) // includes '.'
    val avail = maxLen - ext.length - 1 // ellipsis
    if (avail <= 4) return name.take(maxLen - 1) + "\u2026"
    val head = avail / 2 + 1
    val tail = avail - head
    return base.take(head) + "\u2026" + base.takeLast(tail.coerceAtLeast(1)) + ext
}

/** Path multi-folder disimpan digabung newline di kolom path. */
fun splitMonitorPaths(raw: String): List<String> =
    raw.split('\n', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() }

fun joinMonitorPaths(paths: List<String>): String =
    paths.map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")
