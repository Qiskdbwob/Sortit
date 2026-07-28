package com.sortit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sortit.SortitApplication
import com.sortit.data.TemplateEntity
import com.sortit.util.StorageAccess
import kotlinx.coroutines.launch
import com.sortit.util.SystemExcludes
import com.sortit.util.StoragePaths
import com.sortit.repo.ExcludeRepository
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import android.content.Intent

@Composable
fun MainScreen(
  dashboardVm: DashboardViewModel,
  templateVm: TemplateViewModel,
  monitorVm: MonitorViewModel,
  scanVm: ScanViewModel,
  historyVm: HistoryViewModel,
  onDynamicColorChange: (Boolean) -> Unit = {},
  themeMode: String = "system",
  onThemeModeChange: (String) -> Unit = {}
) {
  val context = LocalContext.current
  var granted by remember { mutableStateOf(StorageAccess.has(context)) }
  val legacyLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
  ) { granted = StorageAccess.has(context) }
  val settingsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
  ) { granted = StorageAccess.has(context) }

  LaunchedEffect(Unit) { granted = StorageAccess.has(context) }

  if (!granted) {
    PermissionGate(
      legacy = StorageAccess.needsRuntimeRequest(),
      onGrant = {
        if (StorageAccess.needsRuntimeRequest()) {
          legacyLauncher.launch(StorageAccess.runtimePermissions())
        } else {
          settingsLauncher.launch(StorageAccess.manageSettingsIntent(context))
        }
      }
    )
    return
  }

  val scanState by scanVm.state.collectAsState()
  Box(Modifier.fillMaxSize()) {
    MainTabs(dashboardVm, templateVm, monitorVm, scanVm, historyVm, onDynamicColorChange, themeMode, onThemeModeChange)
    when (val s = scanState) {
      is ScanUiState.Idle -> Unit
      else -> ScanFlowScreen(state = s, scanVm = scanVm)
    }
  }
}

@Composable
private fun PermissionGate(legacy: Boolean, onGrant: () -> Unit) {
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
      Modifier.fillMaxSize().padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(16.dp))
      Text("Izin storage dibutuhkan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(8.dp))
      Text(
        if (legacy) "Sortit perlu izin baca storage untuk scan file, monitor path, dan memindah file sesuai rule."
        else "Sortit memakai All-files access agar scan by ekstensi bisa menjangkau seluruh internal storage. Aktifkan izin untuk Sortit di pengaturan sistem.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(20.dp))
      Button(onClick = onGrant) { Text(if (legacy) "Izinkan akses" else "Buka pengaturan") }
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainTabs(
  dashboardVm: DashboardViewModel,
  templateVm: TemplateViewModel,
  monitorVm: MonitorViewModel,
  scanVm: ScanViewModel,
  historyVm: HistoryViewModel,
  onDynamicColorChange: (Boolean) -> Unit,
  themeMode: String,
  onThemeModeChange: (String) -> Unit
) {
  var tab by remember { mutableIntStateOf(0) }
  var showScanLauncher by remember { mutableStateOf(false) }
  var addRuleRequest by remember { mutableStateOf(false) }
  var showSettings by remember { mutableStateOf(false) }
  val templates by templateVm.templates.collectAsState()

  Scaffold(
    topBar = {
      androidx.compose.material3.CenterAlignedTopAppBar(
        title = {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              "SORTIT",
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
            )
            Text(
              "penyortir file lokal",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        actions = {
          IconButton(onClick = { showSettings = true }) {
            Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
          }
        }
      )
    },
    bottomBar = {
      NavigationBar {
        NavigationBarItem(
          selected = tab == 0,
          onClick = { tab = 0 },
          icon = { Icon(Icons.Default.Folder, contentDescription = null) },
          label = { Text("Beranda") }
        )
        NavigationBarItem(
          selected = tab == 1,
          onClick = { tab = 1 },
          icon = { Icon(Icons.Default.Add, contentDescription = null) },
          label = { Text("Rules") }
        )
        NavigationBarItem(
          selected = tab == 2,
          onClick = { tab = 2 },
          icon = { Icon(Icons.Default.Folder, contentDescription = null) },
          label = { Text("Monitor") }
        )
        NavigationBarItem(
          selected = tab == 3,
          onClick = { tab = 3 },
          icon = { Icon(Icons.Default.Delete, contentDescription = null) },
          label = { Text("Riwayat") }
        )
      }
    }
  ) { pad ->
    when (tab) {
      0 -> DashboardScreen(
        vm = dashboardVm,
        scanVm = scanVm,
        onScan = { showScanLauncher = true },
        onAddRule = { tab = 1; addRuleRequest = true },
        modifier = Modifier.padding(pad)
      )
      1 -> RulesScreen(
        modifier = Modifier.padding(pad),
        vm = templateVm,
        scanVm = scanVm,
        addRuleRequest = addRuleRequest,
        onAddRuleConsumed = { addRuleRequest = false },
        onScanRules = { ids -> scanVm.requestScan(ids) }
      )
      2 -> MonitorScreen(monitorVm, modifier = Modifier.padding(pad))
      else -> HistoryScreen(historyVm, modifier = Modifier.padding(pad))
    }
  }

  if (showScanLauncher) {
    ScanLauncherDialog(
      templates = templates,
      onDismiss = { showScanLauncher = false },
      onScan = { ids ->
        showScanLauncher = false
        scanVm.requestScan(ids)
      }
    )
  }

  if (showSettings) {
    SettingsDialog(onDismiss = { showSettings = false }, onDynamicColorChange = onDynamicColorChange, themeMode = themeMode, onThemeModeChange = onThemeModeChange)
  }
}

@Composable
private fun SettingsDialog(
  onDismiss: () -> Unit,
  onDynamicColorChange: (Boolean) -> Unit,
  themeMode: String,
  onThemeModeChange: (String) -> Unit
) {
  val context = LocalContext.current
  val app = context.applicationContext as SortitApplication
  val scope = rememberCoroutineScope()
  val excludeRepo = remember { ExcludeRepository(app.db) }
  var dynamicColor by remember { mutableStateOf(app.prefs.useDynamicColor) }
  var mode by remember { mutableStateOf(themeMode) }
  var retentionDays by remember { mutableIntStateOf(app.prefs.trashRetentionDays) }
  var globalExcludes by remember { mutableStateOf<List<String>>(emptyList()) }
  var excludeError by remember { mutableStateOf<String?>(null) }
  var reloadTick by remember { mutableIntStateOf(0) }

  fun reloadExcludes() {
    scope.launch {
      globalExcludes = excludeRepo.globalPatterns().map { it.trimEnd('/') }.distinct().sorted()
    }
  }

  LaunchedEffect(reloadTick) { reloadExcludes() }

  fun persistUri(uri: Uri) = runCatching {
    context.contentResolver.takePersistableUriPermission(
      uri,
      Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    )
  }

  fun addExcludePath(raw: String) {
    val path = raw.trim().trimEnd('/')
    excludeError = null
    when {
      path.isBlank() -> excludeError = "Path kosong."
      path.startsWith("content://") -> excludeError = "URI SAF non-primary belum didukung."
      SystemExcludes.isSystemPath(path) -> excludeError = "Path sistem sudah dilindungi otomatis."
      else -> scope.launch {
        excludeRepo.addGlobal(path)
        reloadTick++
      }
    }
  }

  val excludeFolderLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree()
  ) { uri ->
    uri?.let {
      persistUri(it)
      val picked = StoragePaths.uriToPath(it)
      if (picked == null) {
        excludeError = "URI SAF non-primary belum didukung."
      } else {
        addExcludePath(picked)
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Pengaturan") },
    text = {
      Column(
        Modifier
          .fillMaxWidth()
          .height(480.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Mode tampilan", fontWeight = FontWeight.SemiBold)
          Text(
            "Siang / malam / ikuti HP.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
              "system" to "Sistem",
              "light" to "Siang",
              "dark" to "Malam"
            ).forEach { (value, label) ->
              val selected = mode == value
              if (selected) {
                Button(onClick = { }) { Text(label) }
              } else {
                OutlinedButton(onClick = {
                  mode = value
                  onThemeModeChange(value)
                }) { Text(label) }
              }
            }
          }
        }
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(Modifier.weight(1f)) {
            Text("Warna dinamis", fontWeight = FontWeight.SemiBold)
            Text(
              "Ikuti warna wallpaper (Material You). Nonaktifkan untuk tema Sortit.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = dynamicColor,
            onCheckedChange = { dynamicColor = it; onDynamicColorChange(it) }
          )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Sampah otomatis", fontWeight = FontWeight.SemiBold)
          Text(
            "Hapus file di trash setelah $retentionDays hari",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Slider(
            value = retentionDays.toFloat(),
            onValueChange = { retentionDays = it.toInt() },
            onValueChangeFinished = { app.prefs.trashRetentionDays = retentionDays },
            valueRange = 1f..90f,
            steps = 0
          )
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1 hari", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("90 hari", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Path dikecualikan (global)", fontWeight = FontWeight.SemiBold)
          Text(
            "Berlaku semua scan & aksi. Folder + isinya tidak ikut discan. Override rule.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedButton(onClick = { excludeFolderLauncher.launch(null) }) {
            Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Tambah folder")
          }
          excludeError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          }
          if (globalExcludes.isEmpty()) {
            Text(
              "Belum ada. Contoh: Download, folder kerja, backup.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            globalExcludes.forEach { path ->
              Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  path,
                  modifier = Modifier.weight(1f),
                  style = MaterialTheme.typography.bodySmall,
                  fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = {
                  scope.launch {
                    excludeRepo.removeGlobal(path)
                    reloadTick++
                  }
                }) {
                  Icon(Icons.Default.Close, contentDescription = "Hapus exclude")
                }
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
  )
}

@Composable
private fun ScanLauncherDialog(
  templates: List<TemplateEntity>,
  onDismiss: () -> Unit,
  onScan: (List<Long>) -> Unit
) {
  val enabled = templates.filter { it.enabled }
  var selected by remember { mutableStateOf(enabled.map { it.id }.toSet()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Scan rule") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pilih rule yang akan discan bersamaan. Hasilnya masuk satu review.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { selected = enabled.map { it.id }.toSet() }) { Text("Pilih semua") }
          TextButton(onClick = { selected = emptySet() }) { Text("Bersihkan") }
        }
        if (enabled.isEmpty()) {
          Text("Belum ada rule aktif.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          androidx.compose.foundation.lazy.LazyColumn(Modifier.height(360.dp)) {
            items(enabled.size) { idx ->
              val t = enabled[idx]
              Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Checkbox(
                  checked = t.id in selected,
                  onCheckedChange = { sel -> selected = if (sel) selected + t.id else selected - t.id }
                )
                Spacer(Modifier.width(8.dp))
                Column {
                  Text(t.name, fontWeight = FontWeight.SemiBold)
                  Text(t.extensions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = { onScan(selected.toList()) }, enabled = selected.isNotEmpty()) { Text("Scan") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
  )
}
