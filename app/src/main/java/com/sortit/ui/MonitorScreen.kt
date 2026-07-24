package com.sortit.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.sortit.ui.components.DashedDivider
import com.sortit.ui.components.EmptyState
import com.sortit.ui.components.Kicker
import com.sortit.ui.components.MediaThumb
import com.sortit.ui.components.MonoText
import com.sortit.ui.components.OneLinePath
import com.sortit.ui.components.StampBadge
import com.sortit.ui.components.StampKind
import com.sortit.ui.components.Ticket
import com.sortit.ui.components.formatSize
import com.sortit.util.LinkUtils
import com.sortit.util.StoragePaths
import com.sortit.util.SystemExcludes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MonitorScreen(vm: MonitorViewModel, modifier: Modifier = Modifier) {
  val list by vm.monitors.collectAsState()
  var showAdd by remember { mutableStateOf(false) }

  LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
          Kicker("Monitor")
          Spacer(Modifier.height(4.dp))
          Text("Monitor path", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { showAdd = true }) {
          Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text("Tambah")
        }
      }
    }
    if (list.isEmpty()) {
      item {
        EmptyState(Icons.Default.Folder, "Belum ada monitor", "Tambah path seperti WA Statuses untuk melihat file terbaru.")
      }
    } else {
      items(list, key = { it.id }) { m -> MonitorCard(m, vm) }
    }
  }

  if (showAdd) {
    AddMonitorDialog(onDismiss = { showAdd = false }, onAdd = { name, path -> vm.add(name, path); showAdd = false })
  }
}

@Composable
private fun MonitorCard(m: MonitorEntity, vm: MonitorViewModel) {
  val scan = remember { ScanPathUseCase(RealFileOpsHolder.ops) }
  val inspection by produceState<ScanPathUseCase.PathInspection?>(initialValue = null, m.path, m.enabled) {
    value = withContext(Dispatchers.IO) { if (m.enabled) scan.inspect(m.path) else null }
  }
  var showFiles by remember { mutableStateOf(false) }

  Ticket(accent = if (m.enabled) MaterialTheme.colorScheme.primary else null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(m.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          OneLinePath(m.path)
        }
        Switch(checked = m.enabled, onCheckedChange = { vm.toggle(m.id, it) })
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val insp = inspection
        if (!m.enabled) StampBadge("Nonaktif", StampKind.PENDING)
        else if (insp == null) StampBadge("Cek", StampKind.PENDING)
        else if (insp.readable) StampBadge("Readable \u00b7 ${insp.fileCount}", StampKind.MOVE)
        else StampBadge("Tidak readable", StampKind.WARN)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { vm.delete(m) }) { Icon(Icons.Default.Delete, contentDescription = "Hapus") }
      }
      if (inspection != null && inspection!!.fileCount > 0) {
        OutlinedButton(onClick = { showFiles = !showFiles }, modifier = Modifier.fillMaxWidth()) {
          Text(if (showFiles) "Sembunyikan file" else "Lihat file (${inspection!!.fileCount})")
        }
        if (showFiles) {
          DashedDivider(Modifier.fillMaxWidth().height(1.dp), vertical = false)
          val files = inspection!!.files.take(8)
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            files.forEach { MonitorFileRow(it) }
            if (inspection!!.fileCount > files.size) {
              Text(
                "+${inspection!!.fileCount - files.size} file lainnya",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MonitorFileRow(item: FileItem) {
  val context = LocalContext.current
  Row(
    Modifier.fillMaxWidth().clickable { LinkUtils.openInFileManager(context, item.path) },
    verticalAlignment = Alignment.CenterVertically
  ) {
    MediaThumb(item.name, item.path, item.mimeType, item.isMedia, Modifier.size(44.dp))
    Spacer(Modifier.size(10.dp))
    Column(Modifier.weight(1f)) {
      Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
      MonoText(formatSize(item.size))
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
    uri?.let { persist(it); path = StoragePaths.uriToPath(it) ?: it.toString() }
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
          Text("Pilih folder")
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      }
    },
    confirmButton = {
      Button(onClick = { val e = validate(); if (e != null) error = e else onAdd(name.trim(), path.trim()) }) { Text("Tambah") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
  )
}
