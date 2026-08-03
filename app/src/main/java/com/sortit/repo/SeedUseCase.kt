package com.sortit.repo

import com.sortit.data.AppDatabase
import com.sortit.data.MonitorEntity
import com.sortit.data.TemplateEntity
import com.sortit.util.Prefs
import com.sortit.util.joinMonitorPaths

/**
 * Seed default + migrasi ringan.
 * seedVersion 3: sampah tanpa txt/bak/nomedia; monitor WA multi-folder.
 */
class SeedUseCase(private val db: AppDatabase) {

    companion object {
        const val CURRENT_SEED_VERSION = 5

        // Ekstensi sampah yang jarang penting bagi user (tanpa txt/bak).
        const val SAMPAH_EXTS =
            "tmp,temp,cache,crdownload,part,download,partial,log,old,swp,swo,thumbs,thumb,torrent,aria2,dmp,chk"

        private val WA_DOCS = listOf(
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents",
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/Sent",
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/Private"
        )
        private val WA_IMAGES = listOf(
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent",
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Private"
        )
        private val WA_VIDEO = listOf(
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video",
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Sent",
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Private"
        )
        private const val WA_STATUSES =
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses/"
    }

    suspend fun seedIfEmpty(prefs: Prefs) {
        if (!prefs.seeded) {
            insertInitialDefaults()
            prefs.seeded = true
            prefs.seedVersion = CURRENT_SEED_VERSION
            return
        }
        if (prefs.seedVersion < CURRENT_SEED_VERSION) {
            migrateToV5()
            prefs.seedVersion = CURRENT_SEED_VERSION
        }
    }

    private suspend fun insertInitialDefaults() {
        if (db.templateDao().countDefaults() == 0) {
            db.templateDao().insert(
                TemplateEntity(
                    name = "sampah",
                    extensions = SAMPAH_EXTS,
                    targetTreeUri = "/storage/emulated/0/Sortit/sampah",
                    sourceMode = "ALL",
                    sourceDirs = null,
                    isDefault = true
                )
            )
            db.templateDao().insert(
                TemplateEntity(
                    name = "dokumen",
                    extensions = "pdf,doc,docx,xls,xlsx,ppt,pptx",
                    targetTreeUri = "/storage/emulated/0/Sortit/dokumen",
                    sourceMode = "ALL",
                    sourceDirs = null,
                    isDefault = true
                )
            )
        }
        if (db.monitorDao().countDefaults() == 0) {
            insertDefaultMonitors()
        }
    }

    private suspend fun insertDefaultMonitors() {
        db.monitorDao().insert(
            MonitorEntity(
                name = "WA Statuses",
                path = WA_STATUSES,
                isDefault = true
            )
        )
        db.monitorDao().insert(
            MonitorEntity(
                name = "dokument WA",
                path = joinMonitorPaths(WA_DOCS),
                isDefault = true
            )
        )
        db.monitorDao().insert(
            MonitorEntity(
                name = "foto Wa",
                path = joinMonitorPaths(WA_IMAGES),
                isDefault = true
            )
        )
        db.monitorDao().insert(
            MonitorEntity(
                name = "Video Wa",
                path = joinMonitorPaths(WA_VIDEO),
                isDefault = true
            )
        )
    }

    /** Update rule sampah bawaan + tambah monitor WA multi-folder jika belum ada. */
    private suspend fun migrateToV2() {
        updateSampahTemplate()
        ensureWaMonitors()
    }

    /**
     * Migrasi idempotent untuk versi lama yang terlanjur ter-seed sebagian:
     * pastikan rule default & monitor default selalu ada, apapun kondisi DB.
     */
    private suspend fun migrateToV4() {
        migrateToV2()
        ensureDefaultTemplates()
    }

    /** v5: tambah monitor WA Statuses untuk user lama (fresh install sudah dapat via insertInitialDefaults). */
    private suspend fun migrateToV5() {
        migrateToV4()
        ensureWaStatusesMonitor()
    }

    private suspend fun ensureDefaultTemplates() {
        val all = db.templateDao().getAllOnce()
        val hasSampah = all.any { it.isDefault && it.name.equals("sampah", ignoreCase = true) }
        val hasDokumen = all.any { it.isDefault && it.name.equals("dokumen", ignoreCase = true) }
        if (!hasSampah) {
            db.templateDao().insert(
                TemplateEntity(
                    name = "sampah",
                    extensions = SAMPAH_EXTS,
                    targetTreeUri = "/storage/emulated/0/Sortit/sampah",
                    sourceMode = "ALL",
                    sourceDirs = null,
                    isDefault = true
                )
            )
        }
        if (!hasDokumen) {
            db.templateDao().insert(
                TemplateEntity(
                    name = "dokumen",
                    extensions = "pdf,doc,docx,xls,xlsx,ppt,pptx",
                    targetTreeUri = "/storage/emulated/0/Sortit/dokumen",
                    sourceMode = "ALL",
                    sourceDirs = null,
                    isDefault = true
                )
            )
        }
    }

    private suspend fun updateSampahTemplate() {
        val all = db.templateDao().getAllOnce()
        val sampah = all.firstOrNull { it.isDefault && it.name.equals("sampah", ignoreCase = true) }
        if (sampah != null) {
            val old = sampah.extensions.lowercase()
            if (old.contains("txt") || old.contains("bak") || old.contains("nomedia") || old == "tmp,cache,webp") {
                db.templateDao().update(sampah.copy(extensions = SAMPAH_EXTS))
            }
        }
    }

    private suspend fun ensureWaMonitors() {
        val all = db.monitorDao().getAllOnce()
        val names = all.map { it.name.lowercase() }.toSet()
        if ("dokument wa" !in names) {
            db.monitorDao().insert(
                MonitorEntity(name = "dokument WA", path = joinMonitorPaths(WA_DOCS), isDefault = true)
            )
        }
        if ("foto wa" !in names) {
            db.monitorDao().insert(
                MonitorEntity(name = "foto Wa", path = joinMonitorPaths(WA_IMAGES), isDefault = true)
            )
        }
        if ("video wa" !in names) {
            db.monitorDao().insert(
                MonitorEntity(name = "Video Wa", path = joinMonitorPaths(WA_VIDEO), isDefault = true)
            )
        }
        ensureWaStatusesMonitor()
    }

    /** Idempotent: pastikan monitor "WA Statuses" ada (path .Statuses). */
    private suspend fun ensureWaStatusesMonitor() {
        val all = db.monitorDao().getAllOnce()
        if (all.none { it.name.equals("wa statuses", ignoreCase = true) }) {
            db.monitorDao().insert(
                MonitorEntity(name = "WA Statuses", path = WA_STATUSES, isDefault = true)
            )
        }
    }
}
