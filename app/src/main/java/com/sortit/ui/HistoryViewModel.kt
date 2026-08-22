package com.sortit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortit.data.SortLogEntity
import com.sortit.repo.FileOps
import com.sortit.repo.SortLogRepository
import com.sortit.util.extensionFolder
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HistoryFile(
    val log: SortLogEntity?,
    val path: String,
    val name: String,
    val size: Long,
    val ext: String,
    val exists: Boolean
)

class HistoryViewModel(
    private val logRepo: SortLogRepository,
    private val fileOps: FileOps
) : ViewModel() {

    private val _trash = MutableStateFlow<List<HistoryFile>>(emptyList())
    val trash: StateFlow<List<HistoryFile>> = _trash

    private val _moved = MutableStateFlow<List<HistoryFile>>(emptyList())
    val moved: StateFlow<List<HistoryFile>> = _moved

    private val _msg = MutableStateFlow<String?>(null)
    val msg: StateFlow<String?> = _msg

    fun clearMsg() { _msg.value = null }

    fun refresh() = viewModelScope.launch {
        _trash.value = loadTrash()
        _moved.value = loadMoved()
    }

    private suspend fun loadTrash(): List<HistoryFile> {
        val logs = logRepo.listTrashed()
        val byDst = logs.associateBy { it.dstPath }
        val disk = fileOps.listTrashFiles()
        val fromDisk = disk.map { f ->
            val log = byDst[f.absolutePath]
            HistoryFile(
                log = log,
                path = f.absolutePath,
                name = f.name,
                size = f.length(),
                ext = extensionFolder(f.name),
                exists = true
            )
        }
        // include log-only missing?
        val diskPaths = fromDisk.map { it.path }.toSet()
        val orphans = logs.filter { it.dstPath !in diskPaths }.map {
            HistoryFile(it, it.dstPath, it.fileName, it.size, extensionFolder(it.fileName), fileOps.exists(it.dstPath))
        }
        return (fromDisk + orphans).sortedByDescending { it.log?.timestamp ?: 0L }
    }

    private suspend fun loadMoved(): List<HistoryFile> {
        return logRepo.listMoved().map {
            HistoryFile(
                log = it,
                path = it.dstPath,
                name = it.fileName,
                size = it.size,
                ext = extensionFolder(it.fileName),
                exists = fileOps.exists(it.dstPath)
            )
        }
    }

    fun undoToSource(items: List<HistoryFile>) = viewModelScope.launch {
        var ok = 0
        var fail = 0
        val failures = mutableListOf<String>()
        items.forEach { h ->
            val log = h.log
            // Fallback: log-only (log ada, file sudah dibersihkan worker) tetap bisa restore
            // pakai srcPath dari log. File yang sama sekali tidak punya log tidak bisa.
            val srcDir = log?.srcPath?.let { File(it).parent }
            if (srcDir == null) { fail++; failures.add(h.name + " (tanpa asal)"); return@forEach }
            if (!h.exists) { fail++; failures.add(h.name + " (hilang)"); return@forEach }
            val dst = fileOps.restore(h.path, srcDir)
            if (dst != null) {
                ok++
                if (log != null) {
                    // File kembali ke asal — log tidak relevan lagi.
                    logRepo.delete(log)
                }
            } else {
                fail++
                failures.add(h.name)
            }
        }
        _msg.value = if (failures.isEmpty()) "Undo: $ok sukses"
            else "Undo: $ok sukses · $fail gagal (${failures.take(3).joinToString(", ")})"
        refresh()
    }

    fun moveTo(items: List<HistoryFile>, destDir: String) = viewModelScope.launch {
        var ok = 0
        var fail = 0
        val failures = mutableListOf<String>()
        fileOps.mkdirs(destDir)
        items.forEach { h ->
            if (!h.exists) { fail++; failures.add(h.name + " (hilang)"); return@forEach }
            val sub = "$destDir/${h.ext}"
            fileOps.mkdirs(sub)
            val dst = fileOps.move(h.path, sub)
            if (dst != null) {
                ok++
                // Perbarui log ke path baru agar Riwayat tetap sinkron (bukan dihapus).
                // srcPath diset ke lokasi sebelumnya supaya Undo kembali ke folder itu,
                // bukan ke asal paling awal (konsisten maju-mundur).
                h.log?.let {
                    logRepo.update(it.copy(srcPath = h.path, dstPath = dst, templateId = 0L))
                }
            } else {
                fail++
                failures.add(h.name)
            }
        }
        _msg.value = if (failures.isEmpty()) "Pindah: $ok sukses"
            else "Pindah: $ok sukses · $fail gagal (${failures.take(3).joinToString(", ")})"
        refresh()
    }

    fun deletePermanent(items: List<HistoryFile>) = viewModelScope.launch {
        var ok = 0
        items.forEach { h ->
            if (fileOps.deleteFile(h.path) || !h.exists) {
                ok++
                h.log?.let { logRepo.delete(it) }
            }
        }
        _msg.value = "Hapus permanen: $ok"
        refresh()
    }

}
