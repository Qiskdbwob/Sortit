package com.sortit

import com.sortit.data.MonitorEntity
import com.sortit.data.TemplateEntity
import com.sortit.repo.SeedDb
import com.sortit.repo.SeedPrefs
import com.sortit.repo.SeedUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test migrasi SeedUseCase — JVM murni, tanpa Android framework.
 * Memakai abstraksi SeedDb/SeedPrefs (bukan Room/SharedPreferences).
 */
class SeedUseCaseTest {

    private class FakeSeedDb : SeedDb {
        val monitors = mutableListOf<MonitorEntity>()
        val templates = mutableListOf<TemplateEntity>()
        private var nextMonitorId = 1L
        private var nextTemplateId = 1L

        override suspend fun countDefaultMonitors(): Int = monitors.count { it.isDefault }
        override suspend fun insertMonitor(m: MonitorEntity): Long {
            val copy = m.copy(id = nextMonitorId++)
            monitors += copy
            return copy.id
        }
        override suspend fun getAllMonitors(): List<MonitorEntity> = monitors.toList()

        override suspend fun countDefaultTemplates(): Int = templates.count { it.isDefault }
        override suspend fun insertTemplate(t: TemplateEntity): Long {
            val copy = t.copy(id = nextTemplateId++)
            templates += copy
            return copy.id
        }
        override suspend fun getAllTemplates(): List<TemplateEntity> = templates.toList()
        override suspend fun updateTemplate(t: TemplateEntity) {
            val i = templates.indexOfFirst { it.id == t.id }
            if (i >= 0) templates[i] = t
        }
    }

    private class FakeSeedPrefs(var seeded: Boolean = false, var seedVersion: Int = 0) : SeedPrefs

    @Test
    fun freshInstall_createsDefaultMonitorsIncludingWaStatuses() = runTest {
        val db = FakeSeedDb()
        SeedUseCase(db, FakeSeedPrefs()).seedIfEmpty()

        val names = db.monitors.map { it.name.lowercase() }
        assertTrue("WA Statuses monitor must exist", "wa statuses" in names)
        val statuses = db.monitors.first { it.name.equals("wa statuses", ignoreCase = true) }
        assertTrue(
            "WA Statuses path must point to .Statuses",
            statuses.path.contains(".Statuses", ignoreCase = true)
        )
        assertTrue("must be default", statuses.isDefault)
    }

    @Test
    fun upgradeFromV4_addsWaStatusesMonitor() = runTest {
        val db = FakeSeedDb().apply {
            monitors += MonitorEntity(id = 1, name = "dokument WA", path = "/x", isDefault = true)
            monitors += MonitorEntity(id = 2, name = "foto Wa", path = "/y", isDefault = true)
            monitors += MonitorEntity(id = 3, name = "Video Wa", path = "/z", isDefault = true)
        }
        val prefs = FakeSeedPrefs(seeded = true, seedVersion = 4)

        SeedUseCase(db, prefs).seedIfEmpty()

        val names = db.monitors.map { it.name.lowercase() }
        assertTrue("WA Statuses must be added on upgrade", "wa statuses" in names)
        assertEquals("seed version must bump to 5", 5, prefs.seedVersion)
        // Tidak duplikat — jalankan dua kali
        SeedUseCase(db, prefs).seedIfEmpty()
        assertEquals(4, db.monitors.size)
    }

    @Test
    fun upgradeFromV4_keepsExistingMonitors() = runTest {
        val db = FakeSeedDb().apply {
            monitors += MonitorEntity(id = 1, name = "custom", path = "/custom", isDefault = false)
        }
        val prefs = FakeSeedPrefs(seeded = true, seedVersion = 4)

        SeedUseCase(db, prefs).seedIfEmpty()

        assertTrue(db.monitors.any { it.name == "custom" })
        assertTrue(db.monitors.any { it.name.equals("wa statuses", ignoreCase = true) })
    }
}
