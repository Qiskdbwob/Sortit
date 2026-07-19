package com.sortit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sortit.data.ScanItemEntity
import com.sortit.domain.SortFilesUseCase
import com.sortit.ui.components.EmptyState
import com.sortit.ui.components.MediaThumb
import com.sortit.ui.components.OneLinePath
import com.sortit.ui.components.SectionCard
import com.sortit.ui.components.StatusBadge
import com.sortit.ui.components.formatDate
import com.sortit.ui.components.formatSize

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
  AlertDialog(
    onDismissRequest = { scanVm.closePreview() },
    title = { Text("Ada review belum selesai") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hasil scan sebelumnya berisi ${state.session.totalFound} file dan masih menunggu tindakan.")
        Text("Dibuat ${formatDate(state.session.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { scanVm.dismissPending(state.session) }) { Text("Buang") }
        TextButton(onClick = { scanVm.rescanPending(state.session, state.requestedRuleIds) }) { Text("Scan ulang") }
        Button(onClick = { scanVm.continuePending(state.session) }) { Text("Lanjutkan") }
      }
    }
  )
}

@Composable
private fun ScanProgressDialog(state: ScanUiState.Scanning, scanVm: ScanViewModel) {
  AlertDialog(
    onDismissRequest = { },
    title = { Text("Scanning...", fontWeight = FontWeight.ExtraBold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text("${state.scanned} file dicek \u2022 ${state.found} cocok", fontWeight = FontWeight.SemiBold)
        if (state.current.isNotBlank()) OneLinePath(state.current)
      }
    },
    confirmButton = { TextButton(onClick = { scanVm.reset() }) { Text("Batal") } }
  )
}

@Composable
private fun SortProgressDialog(state: ScanUiState.Running, scanVm: ScanViewModel) {
  val progress = if (state.total == 0) 0f else (state.done + state.failed).toFloat() / state.total.toFloat()
  AlertDialog(
    onDismissRequest = { },
    title = {
      Text(
        if (state.action == SortFilesUseCase.Action.TRASH) "Memindah ke trash..." else "Memindahkan file...",
        fontWeight = FontWeight.ExtraBold
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text("${state.done + state.failed}/${state.total} \u2022 gagal ${state.failed}", fontWeight = FontWeight.SemiBold)
        if (state.current.isNotBlank()) OneLinePath(state.current)
      }
    },
    confirmButton = { }
  )
}

@Composable
private fun DoneDialog(state: ScanUiState.Done, onDone: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDone,
    icon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary) },
    title = { Text("Aksi selesai", fontWeight = FontWeight.ExtraBold) },
    text = { Text("Dipindahkan: ${state.moved} \u2022 Trash: ${state.trashed} \u2022 Gagal: ${state.failed}") },
    confirmButton = { Button(onClick = onDone) { Text("Kembali") } }
  )
}

private fun ScanPreviewScreen(state: ScanUiState.Preview, scanVm: ScanViewModel) {
  val pending = state.items.count { it.status == "PENDING" }
  val excluded = state.items.count { it.status == "EXCLUDED" }
  val media = state.items.count { it.isMedia }

  AlertDialog(
    onDismissRequest = { scanVm.closePreview() },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { scanVm.closePreview() }) { Icon(Icons.Default.Close, contentDescription = "Tutup") }
        Spacer(Modifier.size(4.dp))
        Text("Review hasil scan", fontWeight = FontWeight.ExtraBold)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("$pending siap ditindak \u2022 $excluded dikecualikan \u2022 $media media",
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { scanVm.setAllExcluded(true) }) { Text("Kecualikan semua") }
          TextButton(onClick = { scanVm.setAllExcluded(false) }) { Text("Sertikan semua") }
        }
        if (state.items.isEmpty()) {
          Text("Tidak ada file dalam sesi ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 400.dp)) {
            items(state.items, key = { it.id }) { item ->
              ScanItemRow(
                item = item,
                templateName = state.templates[item.templateId]?.name ?: "Rule #${item.templateId}",
                onToggle = { scanVm.toggleExcluded(item.id, it) }
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = { scanVm.executePending(SortFilesUseCase.Action.TRASH) },
          enabled = pending > 0
        ) {
          Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text("Trash")
        }
        Button(
          onClick = { scanVm.executePending(SortFilesUseCase.Action.MOVE) },
          enabled = pending > 0
        ) { Text("Pindah") }
      }
    }
  )
}


@Composable
private fun ScanItemRow(item: ScanItemEntity, templateName: String, onToggle: (Boolean) -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      MediaThumb(item.name, item.path, item.mimeType, item.isMedia, Modifier.size(58.dp))
      Spacer(Modifier.size(12.dp))
      Column(Modifier.weight(1f)) {
        Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$templateName \u2022 ${formatSize(item.size)} \u2022 ${formatDate(item.lastModified)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OneLinePath(item.path)
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Checkbox(checked = item.status == "EXCLUDED", onCheckedChange = onToggle)
        Text("Kecualikan", style = MaterialTheme.typography.labelSmall)
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
      Icon(icon, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(14.dp))
      Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
      Spacer(Modifier.height(8.dp))
      Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(20.dp))
      Button(onClick = onAction) { Text(actionLabel) }
    }
  }
}
