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
import com.sortit.data.TemplateEntity

@Composable
fun TemplateScreen(vm: TemplateViewModel) {
    val list by vm.templates.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
            items(list) { t ->
                TemplateRow(t,
                    onToggle = { vm.toggle(t.id, it) },
                    onDelete = { vm.delete(t) },
                    onRun = { /* buka tab Sortir dengan template ini */ }
                )
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, "Tambah") }
    }
    if (showAdd) AddTemplateDialog(onDismiss = { showAdd = false }, onAdd = { n, e, tg, m, d ->
        vm.add(n, e, tg, m, d); showAdd = false
    })
}

@Composable
fun TemplateRow(t: TemplateEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onRun: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(4.dp).clickable { onRun() }) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium)
                Text("${t.extensions}  ->  ${t.targetTreeUri}", style = MaterialTheme.typography.bodySmall)
                Text(if (t.sourceMode == "ALL") "Scan: Semua" else "Scan: Folder pilihan", style = MaterialTheme.typography.labelSmall)
            }
            Switch(checked = t.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Hapus") }
        }
    }
}

@Composable
fun AddTemplateDialog(onDismiss: () -> Unit, onAdd: (String, String, String, String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var ext by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("/storage/emulated/0/Sortit/") }
    var mode by remember { mutableStateOf("ALL") }
    var dirs by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onAdd(name, ext, target, mode, if (mode == "FOLDERS") dirs else null) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        title = { Text("Template Sortir") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nama") })
                OutlinedTextField(ext, { ext = it }, label = { Text("Ekstensi (txt,bak,tmp)") })
                OutlinedTextField(target, { target = it }, label = { Text("Folder tujuan") })
                Row {
                    RadioButton(mode == "ALL", { mode = "ALL" }); Text("Scan Semua")
                    RadioButton(mode == "FOLDERS", { mode = "FOLDERS" }); Text("Folder pilihan")
                }
                if (mode == "FOLDERS") OutlinedTextField(dirs, { dirs = it }, label = { Text("Path folder (pisah koma)") })
            }
        }
    )
}
