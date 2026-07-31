package com.sortit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sortit.data.ScanItemEntity
import com.sortit.domain.SortFilesUseCase
import com.sortit.ui.components.DashedDivider
import com.sortit.ui.components.EmptyState
import com.sortit.ui.components.InfoBanner
import com.sortit.ui.components.MediaThumb
import com.sortit.ui.components.MonoText
import com.sortit.ui.components.OneLinePath
import com.sortit.ui.components.SectionCard
import com.sortit.ui.components.SortitDialog
import com.sortit.ui.components.StampKind
import com.sortit.ui.components.formatDate
import com.sortit.ui.components.formatSize
import com.sortit.util.truncateFileName

@Composable
fun ScanFlowScreen(state: ScanUiState, scanVm: ScanViewModel) {
  when (state) {
    is ScanUiState.ExistingPending -> ExistingPendingDialog(state, scanVm)
    is ScanUiState.Scanning -> ScanProgressDialog(state, scanVm)
    is ScanUiState.Empty -> MessageScreen(
      Icons.Default.Search, "Tidak ada hasil", state.message, "Kembali", { scanVm.reset() }
    )
    is ScanUiState.Error -> MessageScreen(
      Icons.Default.Warning, "Scan gagal", state.message, "Kembali", { scanVm.reset() }
    )
    is ScanUiState.Preview -> ScanPreviewScreen(state, scanVm)
    is ScanUiState.Running -> SortProgressDialog(state, scanVm)
    is ScanUiState.Done -> DoneDialog(state) { scanVm.reset() }
    ScanUiState.Idle -> Unit
  }
}

@Composable
private fun ExistingPendingDialog(state: ScanUiState.ExistingPending, scanVm: ScanViewModel) {
  SortitDialog(
    title = "Ada review belum selesai",
    onDismiss = { scanVm.closePreview() },
    confirmButton = { Button(onClick = { scanVm.continuePending(state.session) }) { Text("Lanjutkan") } },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { scanVm.dismissPending(state.session) }) {
          Text("Buang", color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = { scanVm.rescanPending(state.session, state.requestedRuleIds) }) { Text("Scan ulang") }
      }
    }
  ) {
    Text("Hasil scan sebelumnya berisi ${state.session.totalFound} file dan masih menunggu tindakan.")
    MonoText("Dibuat ${formatDate(state.session.createdAt)}")
  }
}

@Composable
private fun ScanProgressDialog(state: ScanUiState.Scanning, scanVm: ScanViewModel) {
  SortitDialog(
    title = "Scanning...",
    onDismiss = { },
    confirmButton = {
      TextButton(onClick = { scanVm.reset() }) {
        Text("Batalkan", color = MaterialTheme.colorScheme.error)
      }
    }
  ) {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text("${state.scanned} file dicek \u00b7 ${state.found} cocok", fontWeight = FontWeight.SemiBold)
    if (state.current.isNotBlank()) OneLinePath(state.current)
  }
}

@Composable
private fun SortProgressDialog(state: ScanUiState.Running, scanVm: ScanViewModel) {
  val progress = if (state.total == 0) 0f else (state.done + state.failed).toFloat() / state.total.toFloat()
  SortitDialog(
    title = if (state.action == SortFilesUseCase.Action.TRASH) "Memindah ke trash..." else "Memindahkan file...",
    onDismiss = { }
  ) {
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    Text("${state.done + state.failed}/${state.total} \u00b7 gagal ${state.failed}", fontWeight = FontWeight.SemiBold)
    if (state.current.isNotBlank()) OneLinePath(state.current)
  }
}

@Composable
private fun DoneDialog(state: ScanUiState.Done, onDone: () -> Unit) {
  SortitDialog(
    title = "Aksi selesai",
    onDismiss = onDone,
    confirmButton = { Button(onClick = onDone) { Text("Kembali") } }
  ) {
    Text("Dipindahkan: ${state.moved} \u00b7 Trash: ${state.trashed} \u00b7 Gagal: ${state.failed}")
  }
}

private enum class SortKey { NAME, SIZE, DATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanPreviewScreen(state: ScanUiState.Preview, scanVm: ScanViewModel) {
  val pending = state.items.count { it.status == "PENDING" }
  val excluded = state.items.count { it.status == "EXCLUDED" }
  val media = state.items.count { it.isMedia }
  val ruleCount = state.items.map { it.templateId }.distinct().size

  var query by remember { mutableStateOf("") }
  var ruleFilter by remember { mutableStateOf<Long?>(null) }
  var sortKey by remember { mutableStateOf(SortKey.DATE) }
  var descending by remember { mutableStateOf(true) }
  var showConfirm by remember { mutableStateOf(false) }
  var confirmAction by remember { mutableStateOf(SortFilesUseCase.Action.MOVE) }
  var extFilter by remember { mutableStateOf<String?>(null) }

  val filtered = remember(state.items, query, ruleFilter, sortKey, descending, extFilter) {
    val q = query.trim()
    state.items
      .filter { q.isBlank() || it.name.contains(q, true) || it.path.contains(q, true) }
      .filter { ruleFilter == null || it.templateId == ruleFilter }
      .filter { extFilter == null || extOfName(it.name) == extFilter }
      .sortedWith(
        when (sortKey) {
          SortKey.NAME -> compareBy { it.name.lowercase() }
          SortKey.SIZE -> compareBy { it.size }
          SortKey.DATE -> compareBy { it.lastModified }
        }
      )
      .let { if (descending) it.reversed() else it }
  }
  val filteredPending = filtered.count { it.status == "PENDING" }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Review hasil scan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            MonoText("$ruleCount rule \u00b7 ${state.items.size} file dicek", style = MaterialTheme.typography.labelSmall)
          }
        },
        navigationIcon = {
          IconButton(onClick = { scanVm.closePreview() }) { Icon(Icons.Default.Close, contentDescription = "Tutup") }
        }
      )
    },
    bottomBar = {
      Surface(tonalElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("$pending siap ditindak", fontWeight = FontWeight.Bold)
            MonoText("$excluded dikecualikan \u00b7 $media media", style = MaterialTheme.typography.bodySmall)
          }
          OutlinedButton(
            onClick = {
              confirmAction = SortFilesUseCase.Action.TRASH
              showConfirm = true
            },
            enabled = pending > 0
          ) {
            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Trash")
          }
          Spacer(Modifier.size(8.dp))
          Button(
            onClick = {
              confirmAction = SortFilesUseCase.Action.MOVE
              showConfirm = true
            },
            enabled = pending > 0
          ) { Text("Pindah") }
        }
      }
    }
  ) { pad ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(pad),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      if (state.items.size > 200) {
        item {
          InfoBanner(
            text = "Hasil besar: ${state.items.size} file. Gunakan pencarian/filter untuk mempersempit review.",
            kind = StampKind.WARN
          )
        }
      }
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            label = { Text("Cari nama / path") },
            singleLine = true
          )
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterDropdown(
              label = if (ruleFilter == null) "Semua rule" else (state.templates[ruleFilter]?.name ?: "Rule"),
              options = state.templates.values.sortedBy { it.name }.map { it.id to it.name },
              selected = ruleFilter,
              onSelect = { ruleFilter = it }
            )
            FilterDropdown(
              label = "Sort: ${if (sortKey == SortKey.NAME) "nama" else if (sortKey == SortKey.SIZE) "ukuran" else "tanggal"} ${if (descending) "↓" else "↑"}",
              options = listOf(
                SortKey.NAME to "Nama",
                SortKey.SIZE to "Ukuran",
                SortKey.DATE to "Tanggal"
              ),
              selected = sortKey,
              onSelect = { sortKey = it }
            )
            TextButton(onClick = { descending = !descending }) { Text(if (descending) "Turun" else "Naik") }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val exts = remember(state.items) {
              state.items.map { extOfName(it.name) }.filter { it.isNotBlank() }.distinct().sorted().take(8)
            }
            if (exts.isNotEmpty()) {
              FilterChipRow(
                selected = extFilter,
                options = exts,
                onSelect = { extFilter = it }
              )
            }
          }
          SectionCard(title = "Tindakan batch", subtitle = "Berlaku untuk ${filtered.size} file hasil filter.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TextButton(onClick = {
                scanVm.setExcludedForPaths(filtered.map { it.path }.toSet(), true)
              }, enabled = filteredPending > 0) {
                Text("Kecualikan semua terfilter")
              }
              TextButton(onClick = {
                scanVm.setExcludedForPaths(filtered.map { it.path }.toSet(), false)
              }) {
                Text("Sertakan semua terfilter")
              }
            }
          }
        }
      }
      if (state.items.isEmpty()) {
        item { EmptyState(icon = Icons.Default.Search, title = "Tidak ada file", message = "Sesi ini tidak punya item.") }
      } else if (filtered.isEmpty()) {
        item { EmptyState(icon = Icons.Default.Search, title = "Tidak ada hasil filter", message = "Coba ubah kata kunci / filter.") }
      } else {
        items(filtered, key = { it.id }) { item ->
          ScanItemRow(
            item = item,
            templateName = state.templates[item.templateId]?.name ?: "Rule #${item.templateId}",
            onToggle = { scanVm.toggleExcluded(item.id, it) }
          )
        }
      }
    }
  }

  if (showConfirm) {
    val action = confirmAction
    val verb = if (action == SortFilesUseCase.Action.MOVE) "dipindahkan" else "dipindah ke trash"
    SortitDialog(
      title = "Konfirmasi $verb",
      onDismiss = { showConfirm = false },
      confirmButton = {
        Button(onClick = {
          showConfirm = false
          scanVm.executePending(action)
        }) { Text("Ya, $verb") }
      },
      dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Batal") } }
    ) {
      val target = if (action == SortFilesUseCase.Action.TRASH) "trash (.sortit-trash)" else "folder tujuan rule masing-masing"
      Text("$pending file akan $verb ke $target.")
      Text("File yang dikecualikan tidak disentuh. Aksi ini tidak bisa dibatalkan (kecuali lewat Undo di Riwayat).")
    }
  }
}

@Composable
private fun <T> FilterDropdown(
  label: String,
  options: List<Pair<T, String>>,
  selected: T?,
  onSelect: (T) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }
  OutlinedButton(onClick = { expanded = true }) {
    Text(label, maxLines = 1)
  }
  DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    options.forEach { (value, text) ->
      DropdownMenuItem(
        text = { Text(text, maxLines = 1) },
        onClick = {
          onSelect(value)
          expanded = false
        }
      )
    }
  }
}

@Composable
private fun FilterChipRow(
  selected: String?,
  options: List<String>,
  onSelect: (String?) -> Unit
) {
  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
      androidx.compose.material3.FilterChip(
        selected = selected == null,
        onClick = { onSelect(null) },
        label = { Text("Semua ext") }
      )
      options.forEach { e ->
        androidx.compose.material3.FilterChip(
          selected = selected == e,
          onClick = { onSelect(if (selected == e) null else e) },
          label = { Text(e) }
        )
      }
  }
}

private fun extOfName(name: String): String {
  val dot = name.lastIndexOf('.')
  return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1).lowercase() else "other"
}

@Composable
private fun ScanItemRow(item: ScanItemEntity, templateName: String, onToggle: (Boolean) -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.small,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border = BorderStroke(1.dp, LocalSortitColors.current.line)
  ) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      MediaThumb(item.name, item.path, item.mimeType, item.isMedia, Modifier.size(56.dp))
      Spacer(Modifier.size(12.dp))
      Column(Modifier.weight(1f)) {
        Text(truncateFileName(item.name), fontWeight = FontWeight.SemiBold, maxLines = 1)
        MonoText("$templateName \u00b7 ${formatSize(item.size)} \u00b7 ${formatDate(item.lastModified)}")
        OneLinePath(item.path)
      }
      Spacer(Modifier.size(6.dp))
      DashedDivider(Modifier.size(width = 1.dp, height = 46.dp))
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 6.dp)) {
        Checkbox(checked = item.status == "EXCLUDED", onCheckedChange = onToggle)
        Text("Kecualikan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun MessageScreen(
  icon: ImageVector,
  title: String,
  message: String,
  actionLabel: String,
  onAction: () -> Unit
) {
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(14.dp))
      Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(8.dp))
      Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(20.dp))
      Button(onClick = onAction) { Text(actionLabel) }
    }
  }
}
