package com.sortit.util

import com.sortit.repo.FileOps

// Path sistem yang tidak boleh disentuh saat mode "Scan Semua".
// Termasuk folder app sendiri (termasuk trash) supaya rule "Scan Semua"
// tidak menyapu data internal app.
object SystemExcludes {
    private const val APP_ROOT = "/storage/emulated/0/Sortit"

    val PATHS: Set<String> = setOf(
        "/system",
        "/data",
        "/proc",
        "/dev",
        "/storage/emulated/0/Android/data",
        "/storage/emulated/0/Android/obb",
        "/storage/emulated/0/Android/media/com.android.vending",
        APP_ROOT,
        FileOps.TRASH_ROOT
    )

    fun isSystemPath(path: String): Boolean {
        val p = path.trimEnd('/')
        return PATHS.any { p == it || p.startsWith("$it/") }
    }
}
