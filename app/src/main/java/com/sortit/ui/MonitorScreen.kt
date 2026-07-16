package com.sortit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sortit.data.MonitorEntity
import com.sortit.domain.FileItem
import com.sortit.domain.ScanPathUseCase
import java.io.File

@Composable
fun MonitorScreen(vm: MonitorViewModel, scan: ScanPathUseCase = remember { ScanPathUseCase(RealFileOpsHolder.ops) }) {
    val list by vm.monitors.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
            items(list) { m ->
                MonitorRow(m, onToggle = { vm.toggle(m.id, it) }, onDelete = { vm.delete(m) })
                if (m.enabled) {
                    val items = remember(m.path) { scan.list(m.path) }
                    if (items.isEmpty()) Text("  (kosong / path tidak ditemukan)", style = MaterialTheme.typography.labelSmall)
                    items.take(50).forEach { FileRow(it) }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Tambah")
        }
    }
    if (showAdd) AddMonitorDialog(onDismiss = { showAdd = false }, onAdd = { n, p -> vm.add(n, p); showAdd = false })
}

@Composable
fun MonitorRow(m: MonitorEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium)
                Text(m.path, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = m.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Hapus") }
        }
    }
}

@Composable
fun FileRow(item: FileItem) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "${item.name}  •  ${item.size / 1024} KB" + if (item.isMedia) "  •  [media]" else "",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun AddMonitorDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onAdd(name, path) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        title = { Text("Pemantau Path") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nama") })
                OutlinedTextField(path, { path = it }, label = { Text("Path") })
            }
        }
    )
}
