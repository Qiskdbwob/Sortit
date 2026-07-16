package com.sortit.util

// Path sistem yang tidak boleh disentuh saat mode "Scan Semua".
// Dihardcode sebagai safety default; user bisa tambah di settings (v2).
object SystemExcludes {
    val PATHS: Set<String> = setOf(
        "/system",
        "/data",
        "/proc",
        "/dev",
        "/storage/emulated/0/Android/data",
        "/storage/emulated/0/Android/obb",
        "/storage/emulated/0/Android/media/com.android.vending"
    )

    fun isSystemPath(path: String): Boolean {
        val p = path.trimEnd('/')
        return PATHS.any { p == it || p.startsWith("$it/") }
    }
}
