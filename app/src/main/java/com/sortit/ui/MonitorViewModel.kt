package com.sortit.ui

import android.os.FileObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortit.data.MonitorEntity
import com.sortit.data.SortLogDao
import com.sortit.domain.AutoApplyUseCase
import com.sortit.domain.FileItem
import com.sortit.domain.SortFilesUseCase
import com.sortit.repo.FileOps
import com.sortit.repo.MonitorRepository
import com.sortit.repo.RealFileOps
import com.sortit.util.splitMonitorPaths
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MonitorViewModel(
    private val repo: MonitorRepository,
    private val autoApply: AutoApplyUseCase? = null,
    private val fileOps: FileOps = RealFileOps(),
    private val logDao: SortLogDao? = null
) : ViewModel() {
    val monitors = repo.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _autoMsg = MutableStateFlow<String?>(null)
    val autoMsg: StateFlow<String?> = _autoMsg

    fun clearAutoMsg() { _autoMsg.value = null }

    private val _operationMsg = MutableStateFlow<String?>(null)
    val operationMsg: StateFlow<String?> = _operationMsg

    fun clearOperationMsg() { _operationMsg.value = null }

    private val _operationBusy = MutableStateFlow(false)
    val operationBusy: StateFlow<Boolean> = _operationBusy

    // Naik tiap ada perubahan filesystem di path monitor aktif (sudah di-debounce).
    private val _refreshTick = MutableStateFlow(0L)
    val refreshTick: StateFlow<Long> = _refreshTick

    private var observers = emptyList<FileObserver>()
    private var debounceJob: Job? = null

    init {
        viewModelScope.launch {
            monitors.collect { list ->
                val paths = list.filter { it.enabled }
                    .flatMap { splitMonitorPaths(it.path) }
                    .distinct()
                restartObservers(paths)
            }
        }
    }

    fun toggle(id: Long, enabled: Boolean) = viewModelScope.launch {
        val m = repo.get(id) ?: return@launch
        repo.update(m.copy(enabled = enabled))
    }

    fun delete(m: MonitorEntity) = viewModelScope.launch { repo.delete(m) }

    fun add(name: String, path: String) = viewModelScope.launch {
        repo.insert(MonitorEntity(name = name, path = path))
    }

    fun update(m: MonitorEntity) = viewModelScope.launch {
        repo.update(m.copy(name = m.name.trim(), path = m.path.trim()))
    }

    fun runAutoFor(monitor: MonitorEntity) = viewModelScope.launch {
        val auto = autoApply ?: return@launch
        if (_operationBusy.value) return@launch
        _operationBusy.value = true
        try {
            val r = auto.applyForMonitorPaths(monitor.path)
            _autoMsg.value = "Auto: pindah ${r.moved} · trash ${r.trashed} · gagal ${r.failed}"
        } finally {
            _operationBusy.value = false
        }
    }

    fun runAutoAll() = viewModelScope.launch {
        val auto = autoApply ?: return@launch
        if (_operationBusy.value) return@launch
        _operationBusy.value = true
        try {
            val r = auto.applyAllEnabled()
            _autoMsg.value = "Auto semua: pindah ${r.moved} · trash ${r.trashed} · gagal ${r.failed}"
        } finally {
            _operationBusy.value = false
        }
    }

    fun moveSelected(paths: Set<String>, destDir: String) =
        runSort(paths, SortFilesUseCase.Action.MOVE, destDir)

    fun trashSelected(paths: Set<String>) =
        runSort(paths, SortFilesUseCase.Action.TRASH, FileOps.TRASH_ROOT)

    private fun runSort(paths: Set<String>, action: SortFilesUseCase.Action, targetDir: String) {
        if (paths.isEmpty() || _operationBusy.value) return
        val dao = logDao
        if (dao == null) {
            _operationMsg.value = "Log database tidak tersedia, operasi dibatalkan."
            return
        }
        viewModelScope.launch {
            _operationBusy.value = true
            try {
                val items = paths.mapNotNull { p ->
                    val f = File(p)
                    if (f.isFile) {
                        FileItem(
                            path = p,
                            name = f.name,
                            size = f.length(),
                            mimeType = null,
                            lastModified = f.lastModified(),
                            isMedia = false
                        )
                    } else null
                }
                var done = 0
                var failed = 0
                SortFilesUseCase(fileOps, dao)
                    .execute(0L, targetDir, items, action)
                    .collect { pr ->
                        done = pr.done
                        failed = pr.failed
                    }
                val verb = if (action == SortFilesUseCase.Action.MOVE) "dipindah" else "di-trash"
                _operationMsg.value = "$done $verb · $failed gagal"
                poke()
            } catch (e: Exception) {
                _operationMsg.value = "Operasi gagal: ${e.message ?: "unknown"}"
            } finally {
                _operationBusy.value = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun restartObservers(paths: List<String>) {
        observers.forEach { it.stopWatching() }
        val mask = FileObserver.CREATE or FileObserver.DELETE or
            FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE
        observers = paths.filter { File(it).isDirectory }.map { p ->
            object : FileObserver(p, mask) {
                override fun onEvent(event: Int, path: String?) = poke()
            }.also { it.startWatching() }
        }
    }

    // Debounce: FileObserver burst saat copy banyak file — satukan jadi 1 tick.
    private fun poke() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(400)
            _refreshTick.value++
            // Auto-apply realtime: ada file baru di folder monitor → proses rule auto
            // yang menyentuh path ini. Aman: hanya file di path monitor (bukan seluruh storage).
            val auto = autoApply
            if (auto != null && !_operationBusy.value) {
                _operationBusy.value = true
                try {
                    val paths = monitors.value.filter { it.enabled }
                        .flatMap { splitMonitorPaths(it.path) }
                        .distinct()
                    if (paths.isNotEmpty()) {
                        val r = auto.applyForMonitorPaths(paths.joinToString("\n"))
                        if (r.moved > 0 || r.trashed > 0 || r.failed > 0) {
                            _autoMsg.value = "Auto: pindah ${r.moved} · trash ${r.trashed} · gagal ${r.failed}"
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    _operationBusy.value = false
                }
            }
        }
    }

    override fun onCleared() {
        observers.forEach { it.stopWatching() }
        observers = emptyList()
        super.onCleared()
    }
}
