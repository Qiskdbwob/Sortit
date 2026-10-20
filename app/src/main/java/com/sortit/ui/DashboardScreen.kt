package com.sortit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sortit.data.MonitorEntity
import com.sortit.data.SortLogEntity
import com.sortit.domain.ScanPathUseCase
import com.sortit.repo.RealFileOps
import com.sortit.ui.components.EmptyState
import com.sortit.ui.components.Kicker
import com.sortit.ui.components.ManifestTally
import com.sortit.ui.components.MediaThumb
import com.sortit.ui.components.MonoText
import com.sortit.ui.components.SectionCard
import com.sortit.ui.components.StampBadge
import com.sortit.ui.components.StampKind
import com.sortit.ui.components.StubTicket
import com.sortit.ui.components.TallyItem
import com.sortit.ui.components.formatDate
import com.sortit.util.LinkUtils
import com.sortit.util.splitMonitorPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
  vm: DashboardViewModel,
  scanVm: ScanViewModel,
  onScan: () -> Unit,
  onOpenMonitor: (Long) -> Unit,
  modifier: Modifier = Modifier
) {
  val templates by vm.templates.collectAsState()
  val monitors by vm.monitors.collectAsState()
  val counts by vm.counts.collectAsState()
  val totalCounts by vm.totalCounts.collectAsState()
  val pending by vm.pendingScanCount.collectAsState()
  val active by vm.activeScan.collectAsState()
  val recent by vm.recentLogs.collectAsState()

  LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Column {
        Kicker("Beranda")
        Spacer(Modifier.height(2.dp))
        Text("Ringkasan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
          "${templates.count { it.enabled }} rule aktif \u00b7 ${monitors.count { it.enabled }} monitor aktif",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    item {
      ManifestTally(
        kicker = "Manifest hari ini",
        items = listOf(
          TallyItem(counts.moved.toString(), "Dipindahkan", LocalSortitColors.current.move),
          TallyItem(counts.trashed.toString(), "Trash", LocalSortitColors.current.trash),
          TallyItem(pending.toString(), "Pending", null),
          TallyItem(counts.failed.toString(), "Gagal", LocalSortitColors.current.warn)
        ),
        footnote = "Hari ini · total ${totalCounts.moved + totalCounts.trashed} diproses"
      )
    }
    if (active != null && pending > 0) {
      item {
        SectionCard(
          title = "Review belum selesai",
          subtitle = "Hasil scan terakhir masih menunggu tindakan.",
          accent = MaterialTheme.colorScheme.primary
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("${active?.totalFound ?: pending} file ditemukan", fontWeight = FontWeight.SemiBold)
              MonoText("Dibuat ${formatDate(active?.createdAt ?: 0)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { active?.let { scanVm.continuePending(it) } }) { Text("Lanjutkan") }
          }
        }
      }
    }
    item { Kicker("Monitor", modifier = Modifier.padding(top = 2.dp)) }
    if (monitors.isEmpty()) {
      item { EmptyState(Icons.Default.Folder, "Belum ada monitor", "Tambah path seperti WA Statuses untuk melihat file terbaru.") }
    } else {
      items(monitors.take(4)) { MonitorSummaryCard(it, onOpenMonitor) }
    }
    item { Kicker("Aktivitas terbaru", modifier = Modifier.padding(top = 2.dp)) }
    if (recent.isEmpty()) {
      item { Text("Belum ada aktivitas.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp)) }
    } else {
      items(recent) { LogRow(it) }
    }
  }
}

@Composable
private fun MonitorSummaryCard(m: MonitorEntity, onOpenMonitor: (Long) -> Unit) {
  val context = LocalContext.current
  val scan = remember { ScanPathUseCase(RealFileOps()) }
  val inspection by produceState<ScanPathUseCase.PathInspection?>(initialValue = null, m.path, m.enabled) {
    value = withContext(Dispatchers.IO) { if (m.enabled) scan.inspect(m.path, 4) else null }
  }
  val paths = remember(m.path) { splitMonitorPaths(m.path) }
  val pathLabel = if (paths.size <= 1) (paths.firstOrNull() ?: m.path) else "${paths.size} folder · ${paths.first()}"
  val files = inspection?.files ?: emptyList()
  val count = inspection?.fileCount ?: 0
  SectionCard(title = m.name, subtitle = pathLabel) {
    Row(
      modifier = Modifier.fillMaxWidth().clickable { onOpenMonitor(m.id) },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        files.take(4).forEach { f ->
          MediaThumb(f.name, f.path, f.mimeType, f.isMedia, Modifier.size(44.dp))
        }
        if (count > 4) {
          Box(
            modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
          ) {
            Text("+${count - 4}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          }
        }
      }
      StampBadge(
        if (!m.enabled) "Nonaktif"
        else if (inspection == null) "Cek"
        else if (inspection!!.readable) "OK · ${inspection!!.fileCount}"
        else "Error",
        if (!m.enabled) StampKind.PENDING else if (inspection == null) StampKind.PENDING else if (inspection!!.readable) StampKind.MOVE else StampKind.WARN
      )
    }
  }
}

@Composable
private fun LogRow(log: SortLogEntity) {
  val context = LocalContext.current
  val ok = log.status == "OK"
  val trashed = log.dstPath.contains("/.sortit-trash/")
  val isClickable = ok && log.dstPath.isNotBlank()
  val kind = if (!ok) StampKind.WARN else if (trashed) StampKind.TRASH else StampKind.MOVE
  val label = if (!ok) "Fail" else if (trashed) "Trash" else "Move"

  StubTicket(
    title = log.fileName,
    modifier = if (isClickable) Modifier.clickable { LinkUtils.openInFileManager(context, log.dstPath) } else Modifier,
    path = if (ok) "${log.srcPath} \u2192 ${log.dstPath}" else log.srcPath,
    stub = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StampBadge(label, kind)
        Spacer(Modifier.height(4.dp))
        MonoText(text = formatDate(log.timestamp), style = MaterialTheme.typography.labelSmall)
      }
    }
  )
}
