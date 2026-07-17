package com.sortit.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sortit.data.MonitorEntity
import com.sortit.data.SortLogEntity
import com.sortit.domain.ScanPathUseCase
import com.sortit.ui.components.EmptyState
import com.sortit.ui.components.MetricCard
import com.sortit.ui.components.OneLinePath
import com.sortit.ui.components.SectionCard
import com.sortit.ui.components.StatusBadge
import com.sortit.ui.components.formatDate
import com.sortit.util.openPathInFileManager
import com.sortit.util.truncateFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    scanVm: ScanViewModel,
    onScan: () -> Unit,
    onAddRule: () -> Unit
) {
    val templates by vm.templates.collectAsState()
    val monitors by vm.monitors.collectAsState()
    val counts by vm.counts.collectAsState()
    val pending by vm.pendingScanCount.collectAsState()
    val active by vm.activeScan.collectAsState()
    val recent by vm.recentLogs.collectAsState()
    val trashed by vm.trashedFiles.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Ringkasan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                "${templates.count { it.enabled }} rule aktif • ${monitors.count { it.enabled }} monitor aktif",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Dipindahkan", counts.moved.toString(), "file masuk folder target", Icons.Default.CheckCircle, Modifier.weight(1f))
                MetricCard("Trash", counts.trashed.toString(), "file diamankan ke .sortit-trash", Icons.Default.Delete, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Pending", pending.toString(), "file menunggu review", Icons.Default.PlayArrow, Modifier.weight(1f))
                MetricCard("Gagal", counts.failed.toString(), "aksi gagal tercatat", Icons.Default.Warning, Modifier.weight(1f))
            }
        }

        if (active != null && pending > 0) {
            item {
                SectionCard(
                    title = "Review belum selesai",
                    subtitle = "Hasil scan terakhir masih menunggu tindakan. Lanjutkan tanpa scan ulang."
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${active?.totalFound ?: pending} file ditemukan", fontWeight = FontWeight.SemiBold)
                            Text("Dibuat ${formatDate(active?.createdAt ?: 0)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { active?.let { scanVm.continuePending(it) } }) { Text("Lanjutkan") }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Aksi cepat", subtitle = "Scan beberapa rule sekaligus atau buat rule baru.") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onScan, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Scan rules")
                    }
                    OutlinedButton(onClick = onAddRule, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Rule baru")
                    }
                }
            }
        }

        item {
            SectionCard(title = "Monitor path", subtitle = "Status readable path yang dipantau.") {
                if (monitors.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Folder,
                        title = "Belum ada monitor",
                        message = "Tambah path seperti WA Statuses untuk melihat file terbaru."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        monitors.take(4).forEach { MonitorStatusLine(it) }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Aktivitas terbaru", subtitle = "Log move/trash terakhir.") {
                if (recent.isEmpty()) {
                    Text("Belum ada aktivitas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        recent.forEach { LogRow(it) }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Restore dari Trash", subtitle = "Kembalikan file yang sudah di-trash ke lokasi asal.") {
                if (trashed.isEmpty()) {
                    Text("Tidak ada file di trash.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val scope = rememberCoroutineScope()
                    val context = LocalContext.current
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        trashed.forEach { log ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                StatusBadge("TRASH", ok = false)
                                Spacer(Modifier.size(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(truncateFileName(log.fileName), fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    OneLinePath(log.dstPath)
                                }
                                IconButton(onClick = { openPathInFileManager(context, log.dstPath) }) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "Buka", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        vm.restoreFile(log, RealFileOpsHolder.ops)
                                    }
                                }) {
                                    Icon(Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorStatusLine(m: MonitorEntity) {
    val scan = remember { ScanPathUseCase(RealFileOpsHolder.ops) }
    val inspection by produceState<ScanPathUseCase.PathInspection?>(initialValue = null, m.path, m.enabled) {
        value = withContext(Dispatchers.IO) { if (m.enabled) scan.inspect(m.path) else null }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(m.name, fontWeight = FontWeight.SemiBold)
            OneLinePath(m.path)
        }
        if (!m.enabled) {
            StatusBadge("Nonaktif", ok = null)
        } else {
            val insp = inspection
            if (insp == null) StatusBadge("Cek...", ok = null)
            else StatusBadge(if (insp.readable) "Readable • ${insp.fileCount}" else "Tidak readable", ok = insp.readable)
        }
    }
}

@Composable
private fun LogRow(log: SortLogEntity) {
    val ok = log.status == "OK"
    val trashed = log.dstPath.contains("/.sortit-trash/")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusBadge(if (ok) (if (trashed) "TRASH" else "MOVE") else "FAIL", ok = ok)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(log.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1)
            OneLinePath(if (ok) "${log.srcPath} → ${log.dstPath}" else log.srcPath)
        }
        Text(formatDate(log.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
