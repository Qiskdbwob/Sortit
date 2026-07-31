package com.sortit.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Notifikasi ringan untuk hasil auto-apply background.
 * Channel: sortit_auto — silent, tanpa suara/getar supaya tidak ganggu.
 */
object NotifHelper {
    private const val CHANNEL_ID = "sortit_auto"
    private const val NOTIF_ID = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Sortir",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hasil pemindahan otomatis oleh Sortit"
                setSound(null, null)
                enableVibration(false)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun showAutoResult(context: Context, moved: Int, trashed: Int, failed: Int) {
        if (moved == 0 && trashed == 0 && failed == 0) return
        val parts = buildList {
            if (moved > 0) add("$moved dipindah")
            if (trashed > 0) add("$trashed ke trash")
            if (failed > 0) add("$failed gagal")
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
            .setContentTitle("Sortit: Auto selesai")
            .setContentText(parts.joinToString(" · "))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS belum di-grant (API 33+) — abaikan.
        }
    }
}
