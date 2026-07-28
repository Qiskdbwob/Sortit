package com.sortit.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sortit.data.TemplateEntity
import com.sortit.ui.components.DashedDivider
import com.sortit.ui.components.EmptyState
import com.sortit.ui.components.Kicker
import com.sortit.ui.components.MonoText
import com.sortit.ui.components.TagChip
import com.sortit.ui.components.Ticket
import com.sortit.util.StoragePaths
import com.sortit.util.SystemExcludes
import com.sortit.util.parseExtensions
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun RulesScreen(
  modifier: Modifier = Modifier,
  vm: TemplateViewModel,
  scanVm: ScanViewModel,
  addRuleRequest: Boolean,
  onAddRuleConsumed: () -> Unit,
  onScanRules: (List<Long>) -> Unit
) {
  val list by vm.templates.collectAsState()
  var showEditor by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<TemplateEntity?>(null) }
  var confirmDelete by remember { mutableStateOf<TemplateEntity?>(null) }

  LaunchedEffect(addRuleRequest) {
    if (addRuleRequest) { editing = null; showEditor = true; onAddRuleConsumed() }
  }

  LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Kicker("Rules")
          Spacer(Modifier.height(4.dp))
          Text("Rules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
          Text("${list.count { it.enabled }} aktif · ${list.size} total", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { editing = null; showEditor = true }) {
          Icon(Icons.Default.Add, contentDescription = "Rule baru")
        }
        Spacer(Modifier.size(2.dp))
        OutlinedButton(
          onClick = { onScanRules(list.filter { it.enabled }.map { it.id }) },
          enabled = list.any { it.enabled }
        ) {
          Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text("Scan all")
        }
      }
    }
    if (list.isEmpty()) {
      item {
        EmptyState(
          icon = Icons.Default.Folder,
          title = "Belum ada rule",
          message = "Buat rule pertama untuk mulai mengorganisir file.",
          actionLabel = "Buat rule baru",
          onAction = { editing = null; showEditor = true }
        )
      }
    } else {
      items(list, key = { it.id }) { t ->
        RuleCard(
          t = t,
          onToggle = { vm.toggle(t.id, it) },
          onEdit = { editing = t; showEditor = true },
          onDelete = { confirmDelete = t },
          onScan = { onScanRules(listOf(t.id)) }
        )
      }
    }
  }

  if (showEditor) {
    RuleEditorDialog(
      initial = editing,
      existing = list,
      loadExcludes = { id -> vm.excludesFor(id) },
      onDismiss = { showEditor = false },
      onSave = { name, ext, target, mode, dirs, minB, maxB, age, auto, autoAct, excludes ->
        if (editing == null) {
          vm.add(name, ext, target, mode, dirs, minB, maxB, age, auto, autoAct, excludes)
        } else {
          vm.update(
            editing!!.copy(
              name = name,
              extensions = ext,
              targetTreeUri = target,
              sourceMode = mode,
              sourceDirs = dirs,
              minSizeBytes = minB,
              maxSizeBytes = maxB,
              maxAgeDays = age,
              autoEnabled = auto,
              autoAction = autoAct
            ),
            excludePaths = excludes
          )
        }
        showEditor = false
      }
    )
  }

  confirmDelete?.let { t ->
    AlertDialog(
      onDismissRequest = { confirmDelete = null },
      title = { Text("Hapus rule?") },
      text = { Text("Rule '${t.name}' dihapus dari konfigurasi. File di disk tidak dihapus.") },
      confirmButton = { TextButton(onClick = { vm.delete(t); confirmDelete = null }) { Text("Hapus") } },
      dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Batal") } }
    )
  }
}

@Composable
private fun RuleCard(
  t: TemplateEntity,
  onToggle: (Boolean) -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onScan: () -> Unit
) {
  Ticket(accent = if (t.enabled) MaterialTheme.colorScheme.primary else null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (t.isDefault) {
              Spacer(Modifier.size(8.dp))
              TagChip("Default")
            }
            if (t.autoEnabled) {
              Spacer(Modifier.size(8.dp))
              TagChip("Auto")
            }
          }
          MonoText(
            text = t.extensions.replace(",", " · "),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
          )
        }
        Switch(checked = t.enabled, onCheckedChange = onToggle)
      }
      Column {
        MonoText("Target — ${StoragePaths.displayPath(t.targetTreeUri)}")
        Text(
          buildString {
            append(if (t.sourceMode == "ALL") "Sumber: semua storage" else "Sumber: ${t.sourceDirs ?: "-"}")
            if (t.minSizeBytes > 0 || t.maxSizeBytes > 0) {
              append(" · size ")
              if (t.minSizeBytes > 0) append("≥${t.minSizeBytes}")
              if (t.maxSizeBytes > 0) append("≤${t.maxSizeBytes}")
            }
            if (t.maxAgeDays > 0) append(" · umur ≥${t.maxAgeDays}h")
            if (t.autoEnabled) append(" · auto ${t.autoAction}")
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      DashedDivider(Modifier.fillMaxWidth().height(1.dp), vertical = false)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onScan, enabled = t.enabled) {
          Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text("Scan")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Hapus") }
      }
    }
  }
}

@Composable
private fun RuleEditorDialog(
  initial: TemplateEntity?,
  existing: List<TemplateEntity>,
  loadExcludes: suspend (Long) -> List<String>,
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    extensions: String,
    target: String,
    mode: String,
    dirs: String?,
    minSizeBytes: Long,
    maxSizeBytes: Long,
    maxAgeDays: Int,
    autoEnabled: Boolean,
    autoAction: String,
    excludePaths: List<String>
  ) -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var ext by remember { mutableStateOf(initial?.extensions ?: "") }
  var target by remember { mutableStateOf(initial?.targetTreeUri ?: "/storage/emulated/0/Sortit/") }
  var mode by remember { mutableStateOf(initial?.sourceMode ?: "ALL") }
  var dirs by remember { mutableStateOf(initial?.sourceDirs ?: "") }
  var minMb by remember { mutableStateOf(if ((initial?.minSizeBytes ?: 0) > 0) ((initial!!.minSizeBytes) / (1024 * 1024)).toString() else "") }
  var maxMb by remember { mutableStateOf(if ((initial?.maxSizeBytes ?: 0) > 0) ((initial!!.maxSizeBytes) / (1024 * 1024)).toString() else "") }
  var ageDays by remember { mutableStateOf(if ((initial?.maxAgeDays ?: 0) > 0) initial!!.maxAgeDays.toString() else "") }
  var autoEnabled by remember { mutableStateOf(initial?.autoEnabled ?: false) }
  var autoAction by remember { mutableStateOf(initial?.autoAction ?: "MOVE") }
  var excludes by remember { mutableStateOf<List<String>>(emptyList()) }
  var error by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(initial?.id) {
    excludes = if (initial != null) loadExcludes(initial.id) else emptyList()
  }

  fun persist(uri: Uri) = runCatching {
    context.contentResolver.takePersistableUriPermission(
      uri,
      Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    )
  }

  val targetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri?.let { persist(it); target = StoragePaths.uriToPath(it) ?: it.toString() }
  }
  val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri?.let {
      persist(it)
      val picked = StoragePaths.uriToPath(it) ?: it.toString()
      dirs = (dirs.split(",") + picked).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(",")
    }
  }
  val excludeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri?.let {
      persist(it)
      val picked = (StoragePaths.uriToPath(it) ?: return@let).trimEnd('/')
      if (picked.isNotBlank() && picked !in excludes) excludes = excludes + picked
    }
  }

  fun validate(): String? {
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) return "Nama rule wajib diisi."
    if (existing.any { it.name.equals(trimmedName, ignoreCase = true) && it.id != (initial?.id ?: -1L) }) return "Nama rule sudah dipakai."
    if (parseExtensions(ext).isEmpty()) return "Minimal 1 ekstensi valid. Contoh: txt,bak,tmp."
    val cleanTarget = target.trim()
    if (cleanTarget.isBlank()) return "Folder tujuan wajib diisi."
    if (cleanTarget.startsWith("content://")) return "URI SAF non-primary belum didukung untuk scan."
    if (SystemExcludes.isBlockedTarget(cleanTarget)) return "Folder tujuan tidak boleh di path sistem."
    val targetFile = File(cleanTarget)
    if (targetFile.exists() && (!targetFile.isDirectory || !targetFile.canWrite())) return "Folder tujuan ada tapi tidak writable."
    if (mode == "FOLDERS") {
      val list = dirs.split(",").map { it.trim() }.filter { it.isNotBlank() }
      if (list.isEmpty()) return "Mode Folder Pilihan butuh minimal 1 path."
      list.forEach { p ->
        if (p.startsWith("content://")) return "URI SAF non-primary belum didukung: $p"
        if (SystemExcludes.isSystemPath(p)) return "Path sumber dikecualikan sistem: $p"
        val f = File(p)
        if (!f.exists() || !f.isDirectory || !f.canRead()) return "Path sumber tidak readable: $p"
      }
    }
    if (autoEnabled && mode == "ALL") {
      return "Auto rule wajib mode Folder Pilihan (aman). Hindari auto di seluruh storage."
    }
    val minV = minMb.trim().toLongOrNull()
    val maxV = maxMb.trim().toLongOrNull()
    if (minMb.isNotBlank() && minV == null) return "Min size MB tidak valid."
    if (maxMb.isNotBlank() && maxV == null) return "Max size MB tidak valid."
    if (minV != null && maxV != null && minV > maxV) return "Min size > max size."
    if (ageDays.isNotBlank() && ageDays.toIntOrNull() == null) return "Umur (hari) tidak valid."
    return null
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "Rule baru" else "Edit rule") },
    text = {
      Column(
        Modifier
          .fillMaxWidth()
          .heightIn(max = 520.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("Nama") }, singleLine = true)
        OutlinedTextField(value = ext, onValueChange = { ext = it; error = null }, label = { Text("Ekstensi (txt,bak,tmp)") }, singleLine = true)
        OutlinedTextField(value = target, onValueChange = { target = it; error = null }, label = { Text("Folder tujuan") })
        OutlinedButton(onClick = { targetLauncher.launch(null) }) {
          Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text("Pilih folder tujuan")
        }
        Text("Sumber scan:", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
          RadioButton(selected = mode == "ALL", onClick = { mode = "ALL"; if (autoEnabled) autoEnabled = false })
          Text("Semua storage")
          Spacer(Modifier.size(12.dp))
          RadioButton(selected = mode == "FOLDERS", onClick = { mode = "FOLDERS" })
          Text("Folder pilihan")
        }
        if (mode == "FOLDERS") {
          OutlinedButton(onClick = { dirLauncher.launch(null) }) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Tambah folder sumber")
          }
          if (dirs.isNotBlank()) {
            Text("Sumber: $dirs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        Text("Filter size / umur (opsional)", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = minMb, onValueChange = { minMb = it; error = null },
            label = { Text("Min MB") }, singleLine = true, modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = maxMb, onValueChange = { maxMb = it; error = null },
            label = { Text("Max MB") }, singleLine = true, modifier = Modifier.weight(1f)
          )
        }
        OutlinedTextField(
          value = ageDays, onValueChange = { ageDays = it; error = null },
          label = { Text("Umur min (hari) — file lebih tua dari N hari") },
          singleLine = true
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("Otomatis", fontWeight = FontWeight.SemiBold)
            Text(
              "File di folder sumber langsung diproses tanpa scan/review. Wajib Folder Pilihan.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = autoEnabled,
            onCheckedChange = {
              if (it && mode != "FOLDERS") {
                error = "Aktifkan Folder Pilihan dulu untuk auto."
              } else {
                autoEnabled = it; error = null
              }
            }
          )
        }
        if (autoEnabled) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = autoAction == "MOVE", onClick = { autoAction = "MOVE" })
            Text("Pindah")
            Spacer(Modifier.size(12.dp))
            RadioButton(selected = autoAction == "TRASH", onClick = { autoAction = "TRASH" })
            Text("Trash")
          }
        }
        Text("Kecualikan path (per-rule)", fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { excludeLauncher.launch(null) }) {
          Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text("Tambah path exclude")
        }
        excludes.forEach { p ->
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(p, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { excludes = excludes - p }) {
              Icon(Icons.Default.Close, contentDescription = "Hapus")
            }
          }
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      }
    },
    confirmButton = {
      Button(onClick = {
        val e = validate()
        if (e != null) error = e
        else {
          val minB = minMb.trim().toLongOrNull()?.times(1024 * 1024) ?: 0L
          val maxB = maxMb.trim().toLongOrNull()?.times(1024 * 1024) ?: 0L
          val age = ageDays.trim().toIntOrNull() ?: 0
          onSave(
            name.trim(), ext.trim(), target.trim(), mode,
            dirs.trim().ifBlank { null },
            minB, maxB, age, autoEnabled, autoAction, excludes
          )
        }
      }) { Text("Simpan") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
  )
}
