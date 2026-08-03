package com.sortit.util

import android.content.Context
import androidx.core.content.edit

class Prefs(private val ctx: Context) {
  private val sp = ctx.getSharedPreferences("sortit_prefs", Context.MODE_PRIVATE)
  var seeded: Boolean
    get() = sp.getBoolean("seeded", false)
    set(v) = sp.edit { putBoolean("seeded", v) }
  /** Naikkan saat default seed berubah (rule/monitor baru). */
  var seedVersion: Int
    get() = sp.getInt("seed_version", 0)
    set(v) = sp.edit { putInt("seed_version", v) }
  var allFilesGranted: Boolean
    get() = sp.getBoolean("all_files_granted", false)
    set(v) = sp.edit { putBoolean("all_files_granted", v) }
  var useDynamicColor: Boolean
    get() = sp.getBoolean("use_dynamic_color", false)
    set(v) = sp.edit { putBoolean("use_dynamic_color", v) }
  /**
   * Mode tampilan: "system" | "light" | "dark"
   * default system = ikuti HP.
   */
  var themeMode: String
    get() = sp.getString("theme_mode", "system") ?: "system"
    set(v) = sp.edit { putString("theme_mode", v) }
  var trashRetentionDays: Int
    get() = sp.getInt("trash_retention_days", 14)
    set(v) = sp.edit { putInt("trash_retention_days", v) }
}
