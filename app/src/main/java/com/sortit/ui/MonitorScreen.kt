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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoveUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.sortit.repo.RealFileOps
import com.sortit.ui.components.DashedDivider
import com.sortit.ui.components.EmptyState
import com.sortit.ui.components.InfoBanner
import com.sortit.ui.components.Kicker
import com.sortit.ui.components.MediaThumb
import com.sortit.ui.components.MonoText
import com.sortit.ui.components.OneLinePath
import com.sortit.ui.components.PathInputField
import com.sortit.ui.components.SortitDialog
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
  val autoMsg by vm.autoMsg.collectAsState()
  val operationMsg by vm.operationMsg.collectAsState()
  var showAdd by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<MonitorEntity?>(null) }

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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(onClick = { vm.runAutoAll() }) { Text("Auto") }
          OutlinedButton(onClick = { showAdd = true }) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Tambah")
          }
        }
      }
    }
    autoMsg?.let { msg ->
      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          InfoBanner(msg, kind = StampKind.MOVE)
          TextButton(onClick = { vm.clearAutoMsg() }) { Text("Tutup") }
        }
      }
    }
    operationMsg?.let { msg ->
      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          InfoBanner(msg, kind = StampKind.MOVE)
          TextButton(onClick = { vm.clearOperationMsg() }) { Text("Tutup") }
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
      items(list, key = { it.id }) { m -> MonitorCard(m, vm, onEdit = { editing = m }) }
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
  editing?.let { m ->
    EditMonitorDialog(
      initial = m,
      onDismiss = { editing = null },
      onSave = { name, path ->
        vm.update(m.copy(name = name, path = path))
        editing = null
      }
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonitorCard(m: MonitorEntity, vm: MonitorViewModel, onEdit: () -> Unit) {
  val context = LocalContext.current
  val scan = remember { ScanPathUseCase(RealFileOps()) }
  val paths = remember(m.path) { splitMonitorPaths(m.path) }
  val tick by vm.refreshTick.collectAsState()
  val busy by vm.operationBusy.collectAsState()
  val inspection by produceState<ScanPathUseCase.PathInspection?>(
    initialValue = null,
    m.path,
    m.enabled,
    tick
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
    vm.moveSelected(selectedPaths, destDir)
    selectMode = false
    selectedPaths = emptySet()
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
        IconButton(onClick = onEdit) {
          Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = { vm.runAutoFor(m) }) {
          Icon(Icons.Default.PlayArrow, contentDescription = "Jalankan auto")
        }
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

          if (inspection!!.truncated) {
            InfoBanner(
              "Menampilkan ${inspection!!.files.size} dari ${inspection!!.fileCount} file. " +
                "Gunakan 'Pilih semua' untuk memproses semua."
            )
            Spacer(Modifier.height(2.dp))
          }

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
              IconButton(
                enabled = !busy,
                onClick = {
                  // Ambil sampai MOVE_ALL_MAX biar file di luar preview UI ikut
                  val all = scan.inspect(m.path, ScanPathUseCase.MOVE_ALL_MAX).files.map { it.path }.toSet()
                  selectedPaths = all.ifEmpty { inspection?.files?.map { it.path }?.toSet() ?: selectedPaths }
                }
              ) {
                Icon(Icons.Default.SelectAll, contentDescription = "Pilih semua")
              }
              IconButton(enabled = !busy, onClick = { safLauncher.launch(null) }) {
                Icon(Icons.Default.MoveUp, contentDescription = "Pindah")
              }
              IconButton(
                enabled = !busy,
                onClick = {
                  vm.trashSelected(selectedPaths)
                  selectMode = false
                  selectedPaths = emptySet()
                }
              ) {
                Icon(Icons.Default.Delete, contentDescription = "Trash")
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
private fun ExtraPathRow(path: String, onRemove: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    OneLinePath(path, Modifier.weight(1f))
    IconButton(onClick = onRemove) {
      Icon(
        Icons.Default.Close,
        contentDescription = "Hapus folder",
        modifier = Modifier.size(16.dp)
      )
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

  SortitDialog(
    title = "Pemantau path",
    onDismiss = onDismiss,
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
  ) {
    PathInputField(
      value = name,
      onValueChange = { name = it; error = null },
      label = "Nama",
      onPickFolder = null
    )
    PathInputField(
      value = path,
      onValueChange = { path = it; error = null },
      label = "Path utama",
      onPickFolder = { launcher.launch(null) }
    )
    if (extraPaths.isNotEmpty()) {
      Text(
        "Folder tambahan (${extraPaths.size}):",
        style = MaterialTheme.typography.labelMedium
      )
      extraPaths.forEach { p ->
        ExtraPathRow(p, onRemove = { extraPaths = extraPaths - p })
      }
    }
    OutlinedButton(onClick = { launcher.launch(null) }) {
      Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.size(6.dp))
      Text(if (path.isBlank()) "Pilih folder" else "Tambah folder")
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
}

@Composable
private fun EditMonitorDialog(
  initial: MonitorEntity,
  onDismiss: () -> Unit,
  onSave: (String, String) -> Unit
) {
  val context = LocalContext.current
  var name by remember { mutableStateOf(initial.name) }
  val initialPaths = remember(initial.path) { splitMonitorPaths(initial.path) }
  var path by remember { mutableStateOf(initialPaths.firstOrNull() ?: "") }
  var extraPaths by remember { mutableStateOf(initialPaths.drop(1)) }
  var error by remember { mutableStateOf<String?>(null) }

  fun persist(uri: Uri) = runCatching {
    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri?.let {
      persist(it)
      val picked = StoragePaths.uriToPath(it) ?: it.toString()
      if (path.isBlank()) path = picked
      else if (picked !in extraPaths && picked != path) extraPaths = extraPaths + picked
    }
  }

  fun validate(): String? {
    if (name.trim().isBlank()) return "Nama monitor wajib diisi."
    val all = (listOf(path.trim()) + extraPaths).map { it.trim() }.filter { it.isNotBlank() }
    if (all.isEmpty()) return "Minimal 1 path wajib diisi."
    all.forEach { pth ->
      if (pth.startsWith("content://")) return "URI SAF non-primary belum didukung: $pth"
      if (SystemExcludes.isSystemPath(pth)) return "Path sistem tidak boleh dimonitor: $pth"
      val f = File(pth)
      if (!f.exists() || !f.isDirectory || !f.canRead()) return "Path tidak readable: $pth"
    }
    return null
  }

  SortitDialog(
    title = "Edit pemantau path",
    onDismiss = onDismiss,
    confirmButton = {
      Button(onClick = {
        val e = validate()
        if (e != null) error = e
        else {
          val all = (listOf(path.trim()) + extraPaths).map { it.trim() }.filter { it.isNotBlank() }
          onSave(name.trim(), joinMonitorPaths(all))
        }
      }) { Text("Simpan") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
  ) {
    PathInputField(
      value = name,
      onValueChange = { name = it; error = null },
      label = "Nama",
      onPickFolder = null
    )
    PathInputField(
      value = path,
      onValueChange = { path = it; error = null },
      label = "Path utama",
      onPickFolder = { launcher.launch(null) }
    )
    if (extraPaths.isNotEmpty()) {
      Text("Folder tambahan (${extraPaths.size}):", style = MaterialTheme.typography.labelMedium)
      extraPaths.forEach { p ->
        ExtraPathRow(p, onRemove = { extraPaths = extraPaths - p })
      }
    }
    OutlinedButton(onClick = { launcher.launch(null) }) {
      Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.size(6.dp))
      Text("Tambah / ganti folder")
    }
    if (error != null) {
      Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
  }
}
