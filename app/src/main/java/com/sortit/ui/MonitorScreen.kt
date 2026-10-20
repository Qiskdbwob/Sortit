package com.sortit.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoveUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.sortit.ui.components.formatSize
import com.sortit.util.truncateFileName
import com.sortit.util.LinkUtils
import com.sortit.util.StoragePaths
import com.sortit.util.SystemExcludes
import com.sortit.util.joinMonitorPaths
import com.sortit.util.splitMonitorPaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MonitorScreen(
  vm: MonitorViewModel,
  selectedMonitorId: Long,
  onSelectedMonitorChange: (Long) -> Unit,
  addMonitorRequest: Boolean,
  onAddMonitorConsumed: () -> Unit,
  autoAllRequest: Boolean,
  onAutoAllConsumed: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val list by vm.monitors.collectAsState()
  val autoMsg by vm.autoMsg.collectAsState()
  val operationMsg by vm.operationMsg.collectAsState()
  val tick by vm.refreshTick.collectAsState()
  val busy by vm.operationBusy.collectAsState()

  var showAdd by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<MonitorEntity?>(null) }
  var confirmAutoAll by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf<MonitorEntity?>(null) }
  var confirmTrash by remember { mutableStateOf<Set<String>?>(null) }
  var showPaths by remember { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }

  LaunchedEffect(addMonitorRequest) {
    if (addMonitorRequest) { showAdd = true; onAddMonitorConsumed() }
  }
  LaunchedEffect(autoAllRequest) {
    if (autoAllRequest) { confirmAutoAll = true; onAutoAllConsumed() }
  }

  // Pilih monitor pertama bila belum ada / tidak ditemukan
  LaunchedEffect(list, selectedMonitorId) {
    if (list.isNotEmpty() && (selectedMonitorId == 0L || list.none { it.id == selectedMonitorId })) {
      onSelectedMonitorChange(list.first().id)
    }
  }

  val selected = list.firstOrNull { it.id == selectedMonitorId } ?: list.firstOrNull()

  // Reset pilihan saat ganti monitor
  var selectMode by remember(selected?.id) { mutableStateOf(false) }
  var selectedPaths by remember(selected?.id) { mutableStateOf(setOf<String>()) }
  val selectAllScope = rememberCoroutineScope()

  val scan = remember { ScanPathUseCase(RealFileOps()) }
  val inspection by produceState<ScanPathUseCase.PathInspection?>(initialValue = null, selected?.path, selected?.enabled, tick) {
    value = withContext(Dispatchers.IO) {
      if (selected != null && selected.enabled) scan.inspect(selected.path, ScanPathUseCase.DEFAULT_MAX) else null
    }
  }

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

  Column(modifier.fillMaxSize()) {
    Column(Modifier.padding(16.dp)) {
      Kicker("Monitor")
      Spacer(Modifier.height(2.dp))
      Text("Monitor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      Text("File terbaru di folder yang kamu pantau", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    autoMsg?.let { msg ->
      Column(Modifier.padding(horizontal = 16.dp)) {
        InfoBanner(msg, kind = StampKind.MOVE)
        TextButton(onClick = { vm.clearAutoMsg() }) { Text("Tutup") }
      }
    }
    operationMsg?.let { msg ->
      Column(Modifier.padding(horizontal = 16.dp)) {
        InfoBanner(msg, kind = StampKind.MOVE)
        TextButton(onClick = { vm.clearOperationMsg() }) { Text("Tutup") }
      }
    }

    if (list.isEmpty()) {
      Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        EmptyState(Icons.Default.Folder, "Belum ada monitor", "Tambah path seperti folder WhatsApp untuk melihat file terbaru.")
      }
    } else {
      // Chip row
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp)
      ) {
        items(list, key = { it.id }) { m ->
          MonitorChip(m, m.id == selected?.id, tick) { onSelectedMonitorChange(m.id) }
        }
        item {
          OutlinedButton(onClick = { showAdd = true }) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("+ Tambah")
          }
        }
      }

      selected?.let { m ->
        val paths = remember(m.path) { splitMonitorPaths(m.path) }
        // Meta row
        Row(
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (paths.size <= 1) {
            Text(
              paths.firstOrNull() ?: m.path,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
              maxLines = 1
            )
          } else {
            Text(
              "${paths.size} folder",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = { showPaths = true })
            )
          }
          Switch(checked = m.enabled, onCheckedChange = { vm.toggle(m.id, it) })
          Box {
            IconButton(onClick = { menuOpen = true }) {
              Icon(Icons.Default.MoreVert, contentDescription = "Menu")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              DropdownMenuItem(text = { Text("Edit monitor") }, onClick = { menuOpen = false; editing = m })
              DropdownMenuItem(text = { Text("Jalankan Auto folder ini") }, onClick = { menuOpen = false; vm.runAutoFor(m) })
              DropdownMenuItem(text = { Text("Auto semua rule…") }, onClick = { menuOpen = false; confirmAutoAll = true })
              DropdownMenuItem(text = { Text("Hapus monitor") }, onClick = { menuOpen = false; confirmDelete = m })
            }
          }
        }

        val uiFiles = remember(inspection) { inspection?.files?.take(ScanPathUseCase.PREVIEW_UI) ?: emptyList() }

        if (inspection != null && inspection!!.truncated) {
          Column(Modifier.padding(horizontal = 16.dp)) {
            InfoBanner(
              "Menampilkan ${ScanPathUseCase.PREVIEW_UI} dari ${inspection!!.fileCount}+ file. Tekan 'Semua' untuk memilih semuanya.",
              kind = StampKind.WARN
            )
          }
        }

        if (uiFiles.isEmpty()) {
          Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Folder ini sudah bersih.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            items(uiFiles, key = { it.path }) { item ->
              MonitorFileRow(
                item = item,
                selectMode = selectMode,
                isSelected = item.path in selectedPaths,
                onToggle = {
                  if (!selectMode) selectMode = true
                  selectedPaths = if (item.path in selectedPaths) selectedPaths - item.path else selectedPaths + item.path
                  if (selectedPaths.isEmpty()) selectMode = false
                },
                onTap = {
                  if (item.isMedia) LinkUtils.openMedia(context, item.path, item.mimeType)
                  else LinkUtils.openInFileManager(context, item.path)
                }
              )
            }
          }
        }

        // Selection bar
        if (selectMode && selectedPaths.isNotEmpty()) {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.inverseSurface,
            tonalElevation = 6.dp
          ) {
            Row(
              Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("${selectedPaths.size} dipilih", color = MaterialTheme.colorScheme.inverseOnSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
              TextButton(onClick = {
                selectAllScope.launch {
                  val all = withContext(Dispatchers.IO) {
                    scan.inspect(m.path, ScanPathUseCase.MOVE_ALL_MAX).files.map { it.path }.toSet()
                  }
                  selectedPaths = all.ifEmpty { inspection?.files?.map { it.path }?.toSet() ?: selectedPaths }
                }
              }) { Text("Semua", color = MaterialTheme.colorScheme.inverseOnSurface) }
              TextButton(enabled = !busy, onClick = { safLauncher.launch(null) }) {
                Text("Pindah", color = MaterialTheme.colorScheme.inverseOnSurface)
              }
              TextButton(enabled = !busy, onClick = {
                val paths = selectedPaths
                selectMode = false
                selectedPaths = emptySet()
                confirmTrash = paths
              }) { Text("Trash", color = MaterialTheme.colorScheme.error) }
              IconButton(onClick = { selectMode = false; selectedPaths = emptySet() }) {
                Icon(Icons.Default.Close, contentDescription = "Batal", tint = MaterialTheme.colorScheme.inverseOnSurface)
              }
            }
          }
        }
      }
    }
  }

  // Dialogs
  if (showAdd) {
    AddMonitorDialog(onDismiss = { showAdd = false }, onAdd = { name, path ->
      vm.add(name, path)
      showAdd = false
    })
  }
  editing?.let { m ->
    EditMonitorDialog(initial = m, onDismiss = { editing = null }, onSave = { name, path ->
      vm.update(m.copy(name = name, path = path))
      editing = null
    })
  }
  confirmDelete?.let { m ->
    SortitDialog(
      title = "Hapus monitor?",
      onDismiss = { confirmDelete = null },
      confirmButton = {
        TextButton(onClick = { vm.delete(m); confirmDelete = null }) { Text("Hapus") }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Batal") } }
    ) {
      Text("Monitor \"${m.name}\" akan dihapus. File di folder tetap aman.")
    }
  }
  confirmTrash?.let { paths ->
    SortitDialog(
      title = "Pindahkan ke trash?",
      onDismiss = { confirmTrash = null },
      confirmButton = {
        Button(onClick = { vm.trashSelected(paths); confirmTrash = null }) { Text("Trash") }
      },
      dismissButton = { TextButton(onClick = { confirmTrash = null }) { Text("Batal") } }
    ) {
      Text("${paths.size} file akan dipindah ke .sortit-trash dan bisa dikembalikan dari Riwayat.")
    }
  }
  if (confirmAutoAll) {
    SortitDialog(
      title = "Jalankan Auto semua?",
      onDismiss = { confirmAutoAll = false },
      confirmButton = {
        Button(onClick = { confirmAutoAll = false; vm.runAutoAll() }) { Text("Ya, jalankan") }
      },
      dismissButton = { TextButton(onClick = { confirmAutoAll = false }) { Text("Batal") } }
    ) {
      Text("Semua rule auto yang aktif akan memproses file di folder sumbernya sekarang — tanpa review.")
      Text("File yang cocok langsung dipindah atau dipindah ke trash sesuai aturan rule.")
    }
  }
  if (showPaths) {
    selected?.let { m ->
      val ps = splitMonitorPaths(m.path)
      SortitDialog(
        title = "Folder monitor",
        onDismiss = { showPaths = false },
        confirmButton = { Button(onClick = { showPaths = false }) { Text("Tutup") } }
      ) {
        ps.forEach { OneLinePath(it) }
      }
    }
  }
}

@Composable
private fun MonitorChip(m: MonitorEntity, selected: Boolean, tick: Long, onClick: () -> Unit) {
  val scan = remember { ScanPathUseCase(RealFileOps()) }
  val insp by produceState<ScanPathUseCase.PathInspection?>(initialValue = null, m.path, m.enabled, tick) {
    value = withContext(Dispatchers.IO) { if (m.enabled) scan.inspect(m.path, 1) else null }
  }
  val count = insp?.fileCount ?: 0
  // chip button
  Surface(
    onClick = onClick,
    shape = MaterialTheme.shapes.extraLarge,
    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    tonalElevation = if (selected) 0.dp else 1.dp,
    border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
  ) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(m.name, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
      Spacer(Modifier.size(6.dp))
      Text("$count", color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonitorFileRow(
  item: FileItem,
  selectMode: Boolean,
  isSelected: Boolean,
  onToggle: () -> Unit,
  onTap: () -> Unit
) {
  Row(
    Modifier
      .fillMaxWidth()
      .combinedClickable(onClick = { if (selectMode) onToggle() else onTap() }, onLongClick = { onToggle() })
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    MediaThumb(item.name, item.path, item.mimeType, item.isMedia, Modifier.size(44.dp))
    Spacer(Modifier.size(10.dp))
    Column(Modifier.weight(1f)) {
      Text(truncateFileName(item.name), maxLines = 1, fontWeight = FontWeight.SemiBold)
      MonoText("${formatSize(item.size)} · ${ageText(item.lastModified)}")
    }
    Spacer(Modifier.size(8.dp))
    // circle
    Surface(
      onClick = onToggle,
      shape = androidx.compose.foundation.shape.CircleShape,
      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
      border = androidx.compose.foundation.BorderStroke(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
      modifier = Modifier.size(26.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
      }
    }
  }
}

private fun ageText(lastModified: Long): String {
  val diff = System.currentTimeMillis() - lastModified
  val mins = diff / 60000
  return when {
    mins < 1 -> "baru saja"
    mins < 60 -> "$mins menit"
    mins < 1440 -> "${mins / 60} jam"
    else -> "${mins / 1440} hari"
  } + " lalu"
}

// ---------- dialogs (unchanged) ----------
// [keep AddMonitorDialog, EditMonitorDialog, ExtraPathRow below]

@Composable
private fun ExtraPathRow(path: String, onRemove: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    OneLinePath(path, Modifier.weight(1f))
    IconButton(onClick = onRemove) {
      Icon(Icons.Default.Close, contentDescription = "Hapus folder", modifier = Modifier.size(16.dp))
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
    PathInputField(value = name, onValueChange = { name = it; error = null }, label = "Nama", onPickFolder = null)
    PathInputField(value = path, onValueChange = { path = it; error = null }, label = "Path utama", onPickFolder = { launcher.launch(null) })
    if (extraPaths.isNotEmpty()) {
      Text("Folder tambahan (${extraPaths.size}):", style = MaterialTheme.typography.labelMedium)
      extraPaths.forEach { p -> ExtraPathRow(p, onRemove = { extraPaths = extraPaths - p }) }
    }
    OutlinedButton(onClick = { launcher.launch(null) }) {
      Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.size(6.dp))
      Text(if (path.isBlank()) "Pilih folder" else "Tambah folder")
    }
    Text("Bisa multi-folder: pilih berulang (mis. Documents + Sent + Private).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun EditMonitorDialog(initial: MonitorEntity, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
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
    PathInputField(value = name, onValueChange = { name = it; error = null }, label = "Nama", onPickFolder = null)
    PathInputField(value = path, onValueChange = { path = it; error = null }, label = "Path utama", onPickFolder = { launcher.launch(null) })
    if (extraPaths.isNotEmpty()) {
      Text("Folder tambahan (${extraPaths.size}):", style = MaterialTheme.typography.labelMedium)
      extraPaths.forEach { p -> ExtraPathRow(p, onRemove = { extraPaths = extraPaths - p }) }
    }
    OutlinedButton(onClick = { launcher.launch(null) }) {
      Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.size(6.dp))
      Text("Tambah / ganti folder")
    }
    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
  }
}
