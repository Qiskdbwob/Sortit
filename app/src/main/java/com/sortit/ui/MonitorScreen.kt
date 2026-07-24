package com.sortit.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoveUp
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.sortit.data.MonitorEntity
import com.sortit.domain.FileItem
import com.sortit.domain.ScanPathUseCase
import com.sortit.repo.FileOps
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
import com.sortit.util.joinMonitorPaths
import com.sortit.util.splitMonitorPaths
import com.sortit.util.truncateFileName
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MonitorScreen(vm: MonitorViewModel, modifier: Modifier = Modifier) {
  val list by vm.monitors.collectAsState()
  var showAdd by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
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
        EmptyState(
          Icons.Default.Folder,
          "Belum ada monitor",
          "Tambah path seperti folder WhatsApp untuk melihat file terbaru."
        )
      }
    } else {
      items(list, key = { it.id }) { m -> MonitorCard(m, vm) }
    }
  }

  if (showAdd) {
    AddMonitorDialog(
      onDismiss = { showAdd = false },
      onAdd = { name, path ->
        vm.add(name, path)
        showAdd = false
      }
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonitorCard(m: MonitorEntity, vm: MonitorViewModel) {
  val context = LocalContext.current
  val scan = remember { ScanPathUseCase(RealFileOpsHolder.ops) }
  val paths = remember(m.path) { splitMonitorPaths(m.path) }
  val inspection by produceState<ScanPathUseCase.PathInspection?>(
    initialValue = null,
    m.path,
    m.enabled
  ) {
    value = withContext(Dispatchers.IO) {
      if (m.enabled) scan.inspect(m.path, ScanPathUseCase.DEFAULT_MAX) else null
    }
  }
  var showFiles by remember { mutableStateOf(false) }
  var selectMode by remember { mutableStateOf(false) }
  var selectedPaths by remember { mutableStateOf(setOf<String>()) }

  val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    runCatching {
      context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      )
    }
    val destDir = StoragePaths.uriToPath(uri) ?: return@rememberLauncherForActivityResult
    selectedPaths.forEach { path ->
      val file = File(path)
      if (!file.exists()) return@forEach
      val subDir = "$destDir/${extensionFolder(file.name)}"
      File(subDir).mkdirs()
      val target = File(subDir, file.name)
      if (!file.renameTo(target)) {
        runCatching {
          file.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
          }
          if (target.exists() && target.length() == file.length()) file.delete()
        }
      }
    }
    selectMode = false
    selectedPaths = emptySet()
    // force re-inspect by toggling showFiles state consumer via reassignment
    showFiles = showFiles
  }

  Ticket(accent = if (m.enabled) MaterialTheme.colorScheme.primary else null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(m.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          if (paths.size == 1) {
            OneLinePath(paths[0])
          } else {
            MonoText(
              "${paths.size} folder",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            paths.take(3).forEach { OneLinePath(it) }
            if (paths.size > 3) {
              MonoText(
                "+${paths.size - 3} folder lain",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
        Switch(checked = m.enabled, onCheckedChange = { vm.toggle(m.id, it) })
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val insp = inspection
        if (!m.enabled) StampBadge("Nonaktif", StampKind.PENDING)
        else if (insp == null) StampBadge("Cek", StampKind.PENDING)
        else if (insp.readable) {
          val label = buildString {
            append("Readable · ${insp.fileCount}")
            if (insp.pathsScanned > 1) append(" · ${insp.pathsReadable}/${insp.pathsScanned} folder")
            if (insp.truncated) append(" · cap")
          }
          StampBadge(label, StampKind.MOVE)
        } else StampBadge("Tidak readable", StampKind.WARN)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { vm.delete(m) }) {
          Icon(Icons.Default.Delete, contentDescription = "Hapus")
        }
      }

      if (inspection != null && inspection!!.fileCount > 0) {
        OutlinedButton(
          onClick = {
            showFiles = !showFiles
            if (!showFiles) {
              selectMode = false
              selectedPaths = emptySet()
            }
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            if (showFiles) "Sembunyikan file"
            else "Lihat file (${inspection!!.fileCount}${if (inspection!!.truncated) "+" else ""})"
          )
        }

        if (showFiles) {
          DashedDivider(Modifier.fillMaxWidth().height(1.dp), vertical = false)

          // Lazy list capped for UI — avoid composing thousands of rows
          val uiFiles = remember(inspection) {
            inspection!!.files.take(ScanPathUseCase.PREVIEW_UI)
          }
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uiFiles.forEach { item ->
              MonitorFileRow(
                item = item,
                selectMode = selectMode,
                isSelected = item.path in selectedPaths,
                onLongPress = {
                  selectMode = true
                  selectedPaths = selectedPaths + item.path
                },
                onSelectToggle = { sel ->
                  selectedPaths = if (sel) selectedPaths + item.path else selectedPaths - item.path
                  if (selectedPaths.isEmpty()) selectMode = false
                },
                onTap = {
                  if (item.isMedia) LinkUtils.openMedia(context, item.path, item.mimeType)
                  else LinkUtils.openInFileManager(context, item.path)
                }
              )
            }
            if (inspection!!.fileCount > uiFiles.size) {
              Text(
                "+${inspection!!.fileCount - uiFiles.size} file lainnya (tampil ${uiFiles.size} terbaru)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }

      if (selectMode && selectedPaths.isNotEmpty()) {
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.small) {
          Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "${selectedPaths.size} dipilih",
              fontWeight = FontWeight.SemiBold,
              style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
              IconButton(onClick = {
                selectedPaths = inspection?.files?.map { it.path }?.toSet() ?: selectedPaths
              }) {
                Icon(Icons.Default.SelectAll, contentDescription = "Pilih semua")
              }
              IconButton(onClick = { safLauncher.launch(null) }) {
                Icon(Icons.Default.MoveUp, contentDescription = "Pindah")
              }
              IconButton(onClick = {
                selectedPaths.forEach { path ->
                  val file = File(path)
                  if (!file.exists()) return@forEach
                  val ext = extensionFolder(file.name)
                  val trashDir = "${FileOps.TRASH_ROOT}/$ext"
                  File(trashDir).mkdirs()
                  val target = File(trashDir, file.name)
                  if (!file.renameTo(target)) {
                    runCatching {
                      file.inputStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                      }
                      if (target.exists()) file.delete()
                    }
                  }
                }
                selectMode = false
                selectedPaths = emptySet()
              }) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus")
              }
              IconButton(onClick = {
                selectMode = false
                selectedPaths = emptySet()
              }) {
                Icon(Icons.Default.Close, contentDescription = "Batal")
              }
            }
          }
        }
      }
    }
  }
}

private fun extensionFolder(name: String): String {
  val dot = name.lastIndexOf('.')
  return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1).lowercase() else "other"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonitorFileRow(
  item: FileItem,
  selectMode: Boolean,
  isSelected: Boolean,
  onLongPress: () -> Unit,
  onSelectToggle: (Boolean) -> Unit,
  onTap: () -> Unit
) {
  Row(
    Modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = {
          if (selectMode) onSelectToggle(!isSelected) else onTap()
        },
        onLongClick = onLongPress
      ),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (selectMode) {
      Checkbox(checked = isSelected, onCheckedChange = onSelectToggle)
      Spacer(Modifier.size(4.dp))
    }
    MediaThumb(item.name, item.path, item.mimeType, item.isMedia, Modifier.size(44.dp))
    Spacer(Modifier.size(10.dp))
    Column(Modifier.weight(1f)) {
      Text(
        truncateFileName(item.name),
        maxLines = 1,
        fontWeight = FontWeight.SemiBold
      )
      MonoText(formatSize(item.size))
    }
  }
}

@Composable
private fun AddMonitorDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
  val context = LocalContext.current
  var name by remember { mutableStateOf("") }
  var path by remember { mutableStateOf("") }
  var extraPaths by remember { mutableStateOf(listOf<String>()) }
  var error by remember { mutableStateOf<String?>(null) }

  fun persist(uri: Uri) = runCatching {
    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri?.let {
      persist(it)
      val p = StoragePaths.uriToPath(it) ?: it.toString()
      if (path.isBlank()) path = p
      else if (p !in extraPaths && p != path) extraPaths = extraPaths + p
    }
  }

  fun validate(): String? {
    if (name.trim().isBlank()) return "Nama monitor wajib diisi."
    val all = (listOf(path.trim()) + extraPaths).map { it.trim() }.filter { it.isNotBlank() }
    if (all.isEmpty()) return "Minimal 1 path wajib diisi."
    all.forEach { p ->
      if (p.startsWith("content://")) {
        return "URI SAF non-primary belum didukung. Pilih primary storage atau input path manual."
      }
      if (SystemExcludes.isSystemPath(p)) return "Path sistem tidak boleh dimonitor: $p"
      val f = File(p)
      if (!f.exists() || !f.isDirectory || !f.canRead()) {
        return "Path tidak ditemukan atau tidak readable: $p"
      }
    }
    return null
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Pemantau path") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it; error = null },
          label = { Text("Nama") },
          singleLine = true
        )
        OutlinedTextField(
          value = path,
          onValueChange = { path = it; error = null },
          label = { Text("Path utama") }
        )
        if (extraPaths.isNotEmpty()) {
          Text(
            "Folder tambahan (${extraPaths.size}):",
            style = MaterialTheme.typography.labelMedium
          )
          extraPaths.forEach { p ->
            MonoText(p, style = MaterialTheme.typography.bodySmall)
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { launcher.launch(null) }) {
            Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(if (path.isBlank()) "Pilih folder" else "Tambah folder")
          }
        }
        Text(
          "Bisa multi-folder: pilih berulang (mis. Documents + Sent + Private).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (error != null) {
          Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      Button(onClick = {
        val e = validate()
        if (e != null) error = e
        else {
          val all = (listOf(path.trim()) + extraPaths).map { it.trim() }.filter { it.isNotBlank() }
          onAdd(name.trim(), joinMonitorPaths(all))
        }
      }) { Text("Tambah") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
  )
}
