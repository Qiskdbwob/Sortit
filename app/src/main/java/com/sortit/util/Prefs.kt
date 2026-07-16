package com.sortit.util

import android.content.Context
import androidx.core.content.edit

// Simpan flag sederhana (seeded, all-files granted) via SharedPreferences.
class Prefs(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("sortit_prefs", Context.MODE_PRIVATE)
    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) = sp.edit { putBoolean("seeded", v) }
    var allFilesGranted: Boolean
        get() = sp.getBoolean("all_files_granted", false)
        set(v) = sp.edit { putBoolean("all_files_granted", v) }
}
