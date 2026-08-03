package com.sortit

import com.sortit.data.MonitorEntity
import com.sortit.data.TemplateEntity
import com.sortit.repo.SeedUseCase
import com.sortit.util.Prefs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test migrasi SeedUseCase — tanpa Android framework (in-memory fake DAO/repo).
 */
class SeedUseCaseTest {

    // ---------- Fake DAO (in-memory) ----------

    private class FakeMonitorDao : com.sortit.data.MonitorDao {
        val rows = mutableListOf<MonitorEntity>()
        private var nextId = 1L

        override suspend fun get(id: Long): MonitorEntity? = rows.firstOrNull { it.id == id }
        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<MonitorEntity>> =
            kotlinx.coroutines.flow.flowOf(rows.toList())
        override suspend fun insert(m: MonitorEntity): Long {
            val copy = m.copy(id = nextId++)
            rows += copy
            return copy.id
        }
        override suspend fun update(m: MonitorEntity) {
            val i = rows.indexOfFirst { it.id == m.id }
            if (i >= 0) rows[i] = m
        }
        override suspend fun delete(m: MonitorEntity) {
            rows.removeAll { it.id == m.id }
        }
        override suspend fun countDefaults(): Int = rows.count { it.isDefault }
        override suspend fun getAllOnce(): List<MonitorEntity> = rows.toList()
    }

    private class FakeTemplateDao : com.sortit.data.TemplateDao {
        val rows = mutableListOf<TemplateEntity>()
        private var nextId = 1L

        override suspend fun insert(t: TemplateEntity): Long {
            val copy = t.copy(id = nextId++)
            rows += copy
            return copy.id
        }
        override suspend fun update(t: TemplateEntity) {
            val i = rows.indexOfFirst { it.id == t.id }
            if (i >= 0) rows[i] = t
        }
        override suspend fun delete(t: TemplateEntity) {
            rows.removeAll { it.id == t.id }
        }
        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<TemplateEntity>> =
            kotlinx.coroutines.flow.flowOf(rows.toList())
        override suspend fun get(id: Long): TemplateEntity? = rows.firstOrNull { it.id == id }
        override suspend fun getAllOnce(): List<TemplateEntity> = rows.toList()
        override suspend fun getAll(ids: List<Long>): List<TemplateEntity> =
            rows.filter { it.id in ids }
        override suspend fun countDefaults(): Int = rows.count { it.isDefault }
    }

    private class FakeDb(
        val monitorDao: FakeMonitorDao,
        val templateDao: FakeTemplateDao
    ) : com.sortit.data.AppDatabase() {
        override fun monitorDao() = monitorDao
        override fun templateDao() = templateDao
        override fun excludeDao() = throw UnsupportedOperationException()
        override fun sortLogDao() = throw UnsupportedOperationException()
        override fun scanSessionDao() = throw UnsupportedOperationException()
        override fun scanItemDao() = throw UnsupportedOperationException()
    }

    private class FakePrefs : Prefs(object : android.content.Context() {}) {
        private var seeded = false
        private var version = 0
        override var seeded: Boolean
            get() = seeded
            set(v) { seeded = v }
        override var seedVersion: Int
            get() = version
            set(v) { version = v }
    }

    // ---------- Tests ----------

    @Test
    fun freshInstall_createsDefaultMonitorsIncludingWaStatuses() = runTest {
        val db = FakeDb(FakeMonitorDao(), FakeTemplateDao())
        val useCase = SeedUseCase(db)

        useCase.seedIfEmpty(FakePrefs())

        val monitors = db.monitorDao.getAllOnce()
        val names = monitors.map { it.name.lowercase() }
        assertTrue("WA Statuses monitor must exist", "wa statuses" in names)
        val statuses = monitors.first { it.name.equals("wa statuses", ignoreCase = true) }
        assertTrue(
            "WA Statuses path must point to .Statuses",
            statuses.path.contains(".Statuses", ignoreCase = true)
        )
        assertTrue("must be default", statuses.isDefault)
    }

    @Test
    fun upgradeFromV4_addsWaStatusesMonitor() = runTest {
        val monitorDao = FakeMonitorDao()
        // Simulasikan user v4: sudah ada dokument/foto/video WA, belum ada statuses
        monitorDao.rows += MonitorEntity(id = 1, name = "dokument WA", path = "/x", isDefault = true)
        monitorDao.rows += MonitorEntity(id = 2, name = "foto Wa", path = "/y", isDefault = true)
        monitorDao.rows += MonitorEntity(id = 3, name = "Video Wa", path = "/z", isDefault = true)
        val db = FakeDb(monitorDao, FakeTemplateDao())
        val prefs = FakePrefs().apply { seeded = true; seedVersion = 4 }
        val useCase = SeedUseCase(db)

        useCase.seedIfEmpty(prefs)

        val names = db.monitorDao.getAllOnce().map { it.name.lowercase() }
        assertTrue("WA Statuses must be added on upgrade", "wa statuses" in names)
        assertEquals("seed version must bump to 5", 5, prefs.seedVersion)
        // Tidak duplikat — jalankan dua kali
        useCase.seedIfEmpty(prefs)
        assertEquals(4, db.monitorDao.getAllOnce().size)
    }

    @Test
    fun upgradeFromV4_keepsExistingMonitors() = runTest {
        val monitorDao = FakeMonitorDao()
        monitorDao.rows += MonitorEntity(id = 1, name = "custom", path = "/custom", isDefault = false)
        val db = FakeDb(monitorDao, FakeTemplateDao())
        val prefs = FakePrefs().apply { seeded = true; seedVersion = 4 }

        SeedUseCase(db).seedIfEmpty(prefs)

        val rows = db.monitorDao.getAllOnce()
        assertTrue(rows.any { it.name == "custom" })
        assertTrue(rows.any { it.name.equals("wa statuses", ignoreCase = true) })
    }
}
