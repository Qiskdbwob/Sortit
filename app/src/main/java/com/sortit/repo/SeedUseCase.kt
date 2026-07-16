package com.sortit.repo

import com.sortit.data.AppDatabase
import com.sortit.data.MonitorEntity
import com.sortit.data.TemplateEntity

// Seed default sekali saja (guard via flag di Preferences).
class SeedUseCase(private val db: AppDatabase) {
    suspend fun seedIfEmpty(prefs: com.sortit.util.Prefs) {
        if (prefs.seeded) return
        if (db.templateDao().countDefaults() == 0) {
            db.templateDao().insert(
                TemplateEntity(
                    name = "sampah",
                    extensions = "txt,bak,tmp,cache,webp",
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
            db.monitorDao().insert(
                MonitorEntity(
                    name = "WA Statuses",
                    path = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses/",
                    isDefault = true
                )
            )
        }
        prefs.seeded = true
    }
}
