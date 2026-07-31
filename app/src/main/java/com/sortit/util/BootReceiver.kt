package com.sortit.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sortit.SortitApplication
import com.sortit.repo.SeedUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Jalankan seed ulang setelah device reboot.
 * Menutup celah: seed hanya jalan di onCreate Application — kalau prefs
 * terlanjur "seeded" tapi DB kosong (mis. restore parsial), default tidak
 * pernah dibuat. Dengan receiver ini, setiap boot memastikan rule & monitor
 * default ada (migrasi idempotent).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? SortitApplication ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SeedUseCase(app.db).seedIfEmpty(app.prefs)
            } catch (_: Throwable) {
            }
        }
    }
}
