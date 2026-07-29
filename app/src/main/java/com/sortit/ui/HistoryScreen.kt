package com.sortit.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sortit.ui.components.EmptyState
import com.sortit.ui.components.InfoBanner
import com.sortit.ui.components.Kicker
import com.sortit.ui.components.MonoText
import com.sortit.ui.components.OneLinePath
import com.sortit.ui.components.StampKind
import com.sortit.ui.components.Ticket
import com.sortit.ui.components.formatSize
import com.sortit.util.StoragePaths
import com.sortit.util.truncateFileName

@Composable
fun HistoryScreen(vm: HistoryViewModel, modifier: Modifier = Modifier) {
  var tab by remember { mutableIntStateOf(0) }
  val trash by vm.trash.collectAsState()
  val moved by vm.moved.collectAsState()
  val msg by vm.msg.collectAsState()
  var query by remember { mutableStateOf("") }
  var selected by remember { mutableStateOf(setOf<String>()) }
  var extFilter by remember { mutableStateOf<String?>(null) }
  var destFilter by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) { vm.refresh() }
  LaunchedEffect(tab) { selected = emptySet(); extFilter = null; destFilter = null; query = "" }

  val context = LocalContext.current
  val pickDest = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    runCatching {
      context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      )
    }
    val path = StoragePaths.uriToPath(uri) ?: return@rememberLauncherForActivityResult
    val items = currentList(tab, trash, moved).filter { it.path in selected }
    vm.moveTo(items, path)
    selected = emptySet()
  }

  val source = if (tab == 0) trash else moved
  val destFolders = remember(moved) {
    moved.mapNotNull { it.log?.dstPath?.let { p -> java.io.File(p).parent } }.distinct().sorted()
  }
  val filtered = source
    .filter { destFilter == null || java.io.File(it.path).parent == destFilter }
    .filter { extFilter == null || it.ext == extFilter }
    .filter {
      query.isBlank() || it.name.contains(query, true) || it.path.contains(query, true) ||
        (it.log?.srcPath?.contains(query, true) == true)
    }
  val exts = source.map { it.ext }.distinct().sorted()

  Column(modifier.fillMaxSize()) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
      Kicker("Riwayat")
      Text("Sampah & Dipindahkan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      msg?.let {
        InfoBanner(text = it, kind = StampKind.MOVE, withIcon = false)
        TextButton(onClick = { vm.clearMsg() }) { Text("Tutup pesan") }
      }
    }
    ScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
      Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Sampah (${trash.size})") })
      Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Dipindahkan (${moved.size})") })
    }
    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      leadingIcon = { Icon(Icons.Default.Search, null) },
      label = { Text("Cari nama / path") },
      singleLine = true
    )
    if (tab == 1 && destFolders.isNotEmpty()) {
      Row(
        Modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        FilterChip(selected = destFilter == null, onClick = { destFilter = null }, label = { Text("Semua tujuan") })
        destFolders.take(6).forEach { d ->
          FilterChip(
            selected = destFilter == d,
            onClick = { destFilter = if (destFilter == d) null else d },
            label = { Text(d.substringAfterLast('/'), maxLines = 1) }
          )
        }
      }
    }
    Row(
      Modifier.padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      FilterChip(selected = extFilter == null, onClick = { extFilter = null }, label = { Text("Semua ext") })
      exts.take(8).forEach { e ->
        FilterChip(
          selected = extFilter == e,
          onClick = { extFilter = if (extFilter == e) null else e },
          label = { Text(e) }
        )
      }
    }
    if (selected.isNotEmpty()) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("${selected.size} dipilih", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = { selected = filtered.map { it.path }.toSet() }) {
          Icon(Icons.Default.SelectAll, contentDescription = "Pilih semua")
        }
        IconButton(onClick = {
          vm.undoToSource(filtered.filter { it.path in selected })
          selected = emptySet()
        }) { Icon(Icons.Default.Restore, contentDescription = "Undo") }
        IconButton(onClick = { pickDest.launch(null) }) {
          Icon(Icons.Default.Folder, contentDescription = "Pindah ke...")
        }
        if (tab == 0) {
          IconButton(onClick = {
            vm.deletePermanent(filtered.filter { it.path in selected })
            selected = emptySet()
          }) { Icon(Icons.Default.Delete, contentDescription = "Hapus permanen") }
        }
        TextButton(onClick = { selected = emptySet() }) { Text("Batal") }
      }
    }
    if (filtered.isEmpty()) {
      EmptyState(
        Icons.Default.Folder,
        if (tab == 0) "Sampah kosong" else "Belum ada file dipindahkan",
        "File hasil Trash/Pindah muncul di sini. Undo mengembalikan ke path asal."
      )
    } else {
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(filtered, key = { it.path + (it.log?.id ?: 0) }) { item ->
          Ticket {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = item.path in selected,
                onCheckedChange = { on ->
                  selected = if (on) selected + item.path else selected - item.path
                }
              )
              Column(Modifier.weight(1f)) {
                Text(truncateFileName(item.name), fontWeight = FontWeight.SemiBold, maxLines = 1)
                MonoText("${item.ext} · ${formatSize(item.size)}" + if (!item.exists) " · hilang" else "")
                OneLinePath(item.path)
                item.log?.srcPath?.let { OneLinePath("asal: $it") }
              }
            }
          }
        }
      }
    }
  }
}

private fun currentList(tab: Int, trash: List<HistoryFile>, moved: List<HistoryFile>) =
  if (tab == 0) trash else moved
