package com.sortit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
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
import com.sortit.SortitApplication
import com.sortit.data.TemplateEntity
import com.sortit.util.StorageAccess

@Composable
fun MainScreen(
    dashboardVm: DashboardViewModel,
    templateVm: TemplateViewModel,
    monitorVm: MonitorViewModel,
    scanVm: ScanViewModel
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StorageAccess.has(context)) }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        granted = StorageAccess.has(context)
        (context.applicationContext as? SortitApplication)?.prefs?.allFilesGranted = granted
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = StorageAccess.has(context)
        (context.applicationContext as? SortitApplication)?.prefs?.allFilesGranted = granted
    }

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
    when (val s = scanState) {
        is ScanUiState.Idle -> MainTabs(dashboardVm, templateVm, monitorVm, scanVm)
        else -> ScanFlowScreen(state = s, scanVm = scanVm)
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
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Izin storage dibutuhkan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (legacy) {
                    "Sortit perlu izin baca storage untuk scan file, monitor path, dan memindah file sesuai rule."
                } else {
                    "Sortit memakai All-files access agar scan by ekstensi bisa menjangkau seluruh internal storage. Aktifkan izin untuk Sortit di pengaturan sistem."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onGrant) { Text(if (legacy) "Izinkan akses" else "Buka pengaturan") }
        }
    }
}

@Composable
private fun MainTabs(
    dashboardVm: DashboardViewModel,
    templateVm: TemplateViewModel,
    monitorVm: MonitorViewModel,
    scanVm: ScanViewModel
) {
    var tab by remember { mutableIntStateOf(0) }
    var showScanLauncher by remember { mutableStateOf(false) }
    var addRuleRequest by remember { mutableStateOf(false) }
    val templates by templateVm.templates.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Sortit", fontWeight = FontWeight.ExtraBold) })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.List, null) },
                    label = { Text("Rules") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Visibility, null) },
                    label = { Text("Monitor") }
                )
            }
        },
        floatingActionButton = {
            SortitFabMenu(
                onScan = { showScanLauncher = true },
                onAddRule = {
                    tab = 1
                    addRuleRequest = true
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> DashboardScreen(
                    vm = dashboardVm,
                    scanVm = scanVm,
                    onScan = { showScanLauncher = true },
                    onAddRule = {
                        tab = 1
                        addRuleRequest = true
                    }
                )
                1 -> RulesScreen(
                    vm = templateVm,
                    scanVm = scanVm,
                    addRuleRequest = addRuleRequest,
                    onAddRuleConsumed = { addRuleRequest = false },
                    onScanRules = { ids -> scanVm.requestScan(ids) }
                )
                else -> MonitorScreen(monitorVm)
            }
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
}

@Composable
private fun SortitFabMenu(onScan: () -> Unit, onAddRule: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (expanded) {
            ExtendedFloatingActionButton(
                onClick = {
                    expanded = false
                    onScan()
                },
                icon = { Icon(Icons.Default.PlayArrow, null) },
                text = { Text("Scan") }
            )
            ExtendedFloatingActionButton(
                onClick = {
                    expanded = false
                    onAddRule()
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Rule") }
            )
        }
        FloatingActionButton(onClick = { expanded = !expanded }) {
            Icon(if (expanded) Icons.Default.Close else Icons.Default.Add, contentDescription = "Aksi")
        }
    }
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
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(enabled, key = { it.id }) { t ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = selected.contains(t.id), onCheckedChange = {
                                    selected = if (it) selected + t.id else selected - t.id
                                })
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(t.name, fontWeight = FontWeight.SemiBold)
                                    Text(t.extensions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onScan(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text("Scan (${selected.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
