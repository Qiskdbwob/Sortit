package com.sortit.ui

import android.content.Intent
import android.net.Uri
import android.os.FileObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sortit.data.MonitorEntity
import com.sortit.domain.FileItem
import com.sortit.domain.ScanPathUseCase
import com.sortit.ui.components.EmptyState
import com.sortit.ui.components.MediaThumb
import com.sortit.ui.components.OneLinePath
import com.sortit.ui.components.StatusBadge
import com.sortit.ui.components.formatSize
import com.sortit.util.StoragePaths
import com.sortit.util.SystemExcludes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MonitorScreen(vm: MonitorViewModel) {
    val list by vm.monitors.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Monitor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("${list.count { it.enabled }} aktif • realtime FileObserver", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Path")
                }
            }
        }

        if (list.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Visibility,
                    title = "Belum ada path dipantau",
                    message = "Tambah path seperti WA Statuses untuk melihat file masuk secara realtime.",
                    actionLabel = "Tambah path",
                    onAction = { showAdd = true }
                )
            }
        } else {
            items(list, key = { it.id }) { m ->
                MonitorCard(
                    m = m,
                    onToggle = { vm.toggle(m.id, it) },
                    onDelete = { vm.delete(m) }
                )
            }
        }
    }

    if (showAdd) {
        AddMonitorDialog(onDismiss = { showAdd = false }, onAdd = { name, path ->
            vm.add(name, path)
            showAdd = false
        })
    }
}

@Composable
private fun MonitorCard(m: MonitorEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val tick = rememberFileObserverTick(m.path, m.enabled)
    val inspection = rememberPathInspection(m.path, m.enabled, tick)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OneLinePath(m.path)
                }
                Switch(checked = m.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Hapus") }
            }

            when {
                !m.enabled -> StatusBadge("Monitor nonaktif", ok = null)
                inspection == null -> StatusBadge("Mengecek path...", ok = null)
                inspection!!.readable -> StatusBadge("Readable • ${inspection!!.fileCount} file", ok = true)
                else -> StatusBadge(inspection!!.message, ok = false)
            }

            if (m.enabled && inspection?.readable == true) {
                val files = inspection!!.files.take(8)
                if (files.isEmpty()) {
                    Text("Folder kosong.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        files.forEach { MonitorFileRow(it) }
                        if (inspection!!.fileCount > files.size) {
                            Text("+${inspection!!.fileCount - files.size} file lainnya", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorFileRow(item: FileItem) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MediaThumb(item.name, item.path, item.mimeType, item.isMedia, Modifier.size(44.dp))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(formatSize(item.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddMonitorDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun persist(uri: Uri) = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            persist(it)
            path = StoragePaths.uriToPath(it) ?: it.toString()
        }
    }

    fun validate(): String? {
        if (name.trim().isBlank()) return "Nama monitor wajib diisi."
        val p = path.trim()
        if (p.isBlank()) return "Path wajib diisi."
        if (p.startsWith("content://")) return "URI SAF non-primary belum didukung. Pilih primary storage atau input path manual."
        if (SystemExcludes.isSystemPath(p)) return "Path sistem tidak boleh dimonitor."
        val f = File(p)
        if (!f.exists() || !f.isDirectory || !f.canRead()) return "Path tidak ditemukan atau tidak readable."
        return null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pemantau path") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("Nama") }, singleLine = true)
                OutlinedTextField(value = path, onValueChange = { path = it; error = null }, label = { Text("Path") })
                OutlinedButton(onClick = { launcher.launch(null) }) {
                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Pilih via SAF")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val err = validate()
                if (err != null) error = err else onAdd(name.trim(), path.trim())
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Suppress("DEPRECATION")
@Composable
private fun rememberFileObserverTick(path: String, enabled: Boolean): Int {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(path, enabled) {
        if (!enabled) {
            onDispose { }
        } else {
            val dir = File(path)
            val observer = if (dir.exists()) {
                object : FileObserver(
                    dir,
                    FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
                            FileObserver.MOVED_TO or FileObserver.MODIFY or FileObserver.ATTRIB
                ) {
                    override fun onEvent(event: Int, p: String?) {
                        tick++
                    }
                }
            } else null
            observer?.startWatching()
            onDispose { observer?.stopWatching() }
        }
    }
    return tick
}

@Composable
private fun rememberPathInspection(path: String, enabled: Boolean, tick: Int): ScanPathUseCase.PathInspection? {
    val scan = remember { ScanPathUseCase(RealFileOpsHolder.ops) }
    val state = produceState<ScanPathUseCase.PathInspection?>(initialValue = null, path, enabled, tick) {
        value = withContext(Dispatchers.IO) {
            if (!enabled) null else scan.inspect(path)
        }
    }
    return state.value
}
