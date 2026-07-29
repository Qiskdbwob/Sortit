package com.sortit.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.sortit.repo.ExcludeRepository
import com.sortit.ui.components.InfoBanner
import com.sortit.ui.components.MonoText
import com.sortit.ui.components.PathInputField
import com.sortit.ui.components.SortitDialog
import com.sortit.ui.components.StampKind
import com.sortit.util.StoragePaths
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Dialog pengaturan global: mode tampilan, warna dinamis, retensi trash,
 * dan daftar path yang dikecualikan dari semua scan & aksi.
 */
@Composable
fun SettingsDialog(
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
  var excludeInput by remember { mutableStateOf("") }
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
      path == "/" -> excludeError = "Root storage tidak boleh dikecualikan."
      path.startsWith("content://") -> excludeError = "URI SAF non-primary belum didukung."
      globalExcludes.any { it.equals(path, ignoreCase = true) } -> {
        excludeError = "Path sudah ada di daftar."
        return
      }
      else -> scope.launch {
        excludeRepo.addGlobal(path)
        excludeInput = ""
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

  SortitDialog(
    title = "Pengaturan",
    onDismiss = onDismiss,
    confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
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
      PathInputField(
        value = excludeInput,
        onValueChange = { excludeInput = it; excludeError = null },
        label = "Tambah path manual",
        modifier = Modifier.fillMaxWidth(),
        onPickFolder = { excludeFolderLauncher.launch(null) },
        error = excludeError
      )
      OutlinedButton(
        onClick = { addExcludePath(excludeInput) },
        enabled = excludeInput.isNotBlank(),
        modifier = Modifier.align(Alignment.End)
      ) { Text("Tambah") }
      if (globalExcludes.isEmpty()) {
        Text(
          "Belum ada. Contoh: Download, folder kerja, backup.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        InfoBanner(
          text = "${globalExcludes.size} path dikecualikan dari semua scan.",
          kind = StampKind.PENDING,
          withIcon = false
        )
        globalExcludes.forEach { path ->
          Row(
            Modifier.fillMaxWidth().padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            MonoText(
              path,
              modifier = Modifier.weight(1f),
              color = MaterialTheme.colorScheme.onSurface
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
}
