package com.sortit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sortit.data.TemplateEntity
import com.sortit.domain.FileItem

@Composable
fun SortirScreen(vm: SortRunViewModel, templateVm: TemplateViewModel) {
    val templates by templateVm.templates.collectAsState()
    val state by vm.state.collectAsState()
    val selected by vm.selected.collectAsState()

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        // pilih template
        var picked by remember { mutableStateOf<TemplateEntity?>(null) }
        templates.filter { it.enabled }.forEach { t ->
            Row(Modifier.fillMaxWidth().clickable { picked = t }.padding(8.dp)) {
                RadioButton(picked == t, null); Text(t.name)
            }
        }
        Button(onClick = { picked?.let { vm.runPreview(it) } }, enabled = picked != null) { Text("Scan & Preview") }

        when (val s = state) {
            is SortUiState.Idle -> Text("Pilih template lalu Scan.")
            is SortUiState.Scanning -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is SortUiState.Preview -> {
                Text("${s.items.size} file cocok. Centang yang dikecualikan:")
                LazyColumn(Modifier.weight(1f)) {
                    items(s.items) { item ->
                        PreviewRow(item, selected.contains(item.path)) { vm.toggleSelect(item.path) }
                    }
                }
                Button(onClick = { picked?.let { vm.execute(it) } }) { Text("Pindah terpilih (${selected.size})") }
            }
            is SortUiState.Running -> {
                Text("Memindah ${s.done + s.failed}/${s.total}  (gagal: ${s.failed})")
                LinearProgressIndicator(Modifier.fillMaxWidth(), progress = { (s.done + s.failed).toFloat() / s.total })
            }
            is SortUiState.Done -> {
                Text("Selesai. Berhasil: ${s.done}, Gagal: ${s.failed}")
                Button(onClick = { vm.reset() }) { Text("Selesai") }
            }
        }
    }
}

@Composable
fun PreviewRow(item: FileItem, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(item.name)
            Text("${item.size / 1024} KB • ${item.path}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
