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
