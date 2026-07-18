# Analisis & Temuan Sortit-v2

> Dibuat: 2025-07-18
> Scope: Bug, logic error, missing feature, UI/UX gap, optimasi performa

---

## Temuan 1 — Error "folder tujuan tidak boleh di path sistem" saat edit rule bawaan

### Gejala
User edit rule default (contoh: `sampah` atau `dokumen`), ubah ekstensi/rename, lalu simpan. Muncul error `"Folder tujuan tidak boleh di path sistem"` meskipun path default tidak diubah.

### Akar Masalah
**Bug logika validasi di `TemplateScreen.kt:239`.**

Rule default dibuat oleh `SeedUseCase.kt` dengan target:
- `/storage/emulated/0/Sortit/sampah`
- `/storage/emulated/0/Sortit/dokumen`

Kedua path ini **memang** di bawah `/storage/emulated/0/Sortit` yang terdaftar sebagai `APP_ROOT` di `SystemExcludes.kt:9,19`:

```kotlin
private const val APP_ROOT = "/storage/emulated/0/Sortit"
val PATHS: Set<String> = setOf(..., APP_ROOT, FileOps.TRASH_ROOT)
```

Fungsi `isSystemPath()` melakukan prefix match:
```kotlin
fun isSystemPath(path: String): Boolean {
    val p = path.trimEnd('/')
    return PATHS.any { p == it || p.startsWith("$it/") }
}
```

Jadi `/storage/emulated/0/Sortit/sampah` → `startsWith("/storage/emulated/0/Sortit/")` → **TRUE** → validasi gagal.

Ironinya, `APP_ROOT` dimasukkan ke `SystemExcludes` **hanya** agar rule "Scan Semua" tidak menyapu folder internal Sortit sendiri. Tapi `isSystemPath()` juga **dipanggil di validasi rule editor** (untuk menolak target path), yang mengakibatkan rule default tidak bisa disimpan ulang.

### Lokasi
- `SystemExcludes.kt:9,19` — `APP_ROOT` di PATHS
- `SystemExcludes.kt:23-26` — `isSystemPath()` prefix match
- `TemplateScreen.kt:239` — `validate()` memanggil `isSystemPath(cleanTarget)` pada folder tujuan

### Saran Perbaikan

**Opsi A (recommended):** Pisahkan concern. Buat fungsi terpisah untuk validasi rule target vs scan exclusion:

```kotlin
// SystemExcludes.kt — tambah fungsi khusus validasi target
fun isBlockedTarget(path: String): Boolean {
    val p = path.trimEnd('/')
    return PATHS.filter { it != APP_ROOT }.any { p == it || p.startsWith("$it/") }
}
```

Di `TemplateScreen.kt:239`, ganti:
```kotlin
// Sebelum (bug):
if (SystemExcludes.isSystemPath(cleanTarget)) return "Folder tujuan tidak boleh di path sistem."

// Sesudah (fix):
if (SystemExcludes.isBlockedTarget(cleanTarget)) return "Folder tujuan tidak boleh di path sistem."
```

**Opsi B (simpler):** Kecualikan `APP_ROOT` dari validasi target di `RuleEditorDialog.validate()` saja:
```kotlin
if (SystemExcludes.isSystemPath(cleanTarget) && !cleanTarget.startsWith(SystemExcludes.APP_ROOT)) {
    return "Folder tujuan tidak boleh di path sistem."
}
```

**Catatan:** `ScanPathUseCase.kt:22` juga memanggil `isSystemPath()` — ini tetap benar karena untuk scanning memang tidak boleh masuk `APP_ROOT`. Jadi opsi A lebih aman karena tidak mengubah perilaku scan.

---

## Temuan 2 — Linked file manager belum terimplementasi

### 2.1 Klik linked media pada monitoring tidak beralih ke media di file manager eksternal

**Akar masalah: Tidak ada click handler.**

`MonitorScreen.kt:152-160` — `MonitorFileRow` menampilkan file sebagai baris **read-only**:

```kotlin
@Composable
private fun MonitorFileRow(item: FileItem) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MediaThumb(item.name, item.path, item.mimeType, item.isMedia, Modifier.size(44.dp))
        // ... text only, TIDAK ADA onClick
    }
}
```

`MonitorViewModel.kt` hanya punya `toggle()`, `delete()`, `add()` — tidak ada method `openFile()` atau `launchFileManager()`.

**Tidak ada** `Intent.ACTION_VIEW`, `Intent.ACTION_OPEN_DOCUMENT`, atau `FileProvider` di seluruh project.

### 2.2 Klik card "dipindahkan" → folder tujuan tidak buka di file manager

**Akar masalah: Tidak ada click handler pada `LogRow`.**

`DashboardScreen.kt:172-180` — `LogRow` menampilkan log sebagai teks:
```kotlin
@Composable
private fun LogRow(log: SortLogEntity) {
    // StatusBadge + Text only, TIDAK ADA onClick
    OneLinePath(if (ok) "${log.srcPath} → ${log.dstPath}" else log.srcPath)
}
```

`dstPath` sudah tersimpan di `SortLogEntity` dan `ScanItemEntity.dstPath` — data sudah ada, tinggal UI yang belum implement.

### 2.3 Klik card trash tidak buka folder trash di file manager

**Sama dengan 2.2.** `LogRow` tidak punya click handler untuk navigate ke path.

### Saran Perbaikan

**Langkah 1 — Tambah `FileProvider`:**

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

```xml
<!-- res/xml/file_paths.xml -->
<paths>
    <external-path name="root" path="." />
    <external-files-path name="app_root" path="Sortit/" />
</paths>
```

**Langkah 2 — Buat utility function:**

```kotlin
// util/LinkUtils.kt
object LinkUtils {
    fun openInFileManager(context: Context, path: String) {
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { context.startActivity(intent) } catch (_: Exception) {
            // Fallback: buka folder parent
            val parent = file.parentFile ?: return
            val parentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", parent)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(parentUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }
}
```

**Langkah 3 — Tambah onClick ke komponen:**

- `MonitorFileRow` → wrap dengan `Modifier.clickable { LinkUtils.openInFileManager(context, item.path) }`
- `LogRow` → wrap dengan `Modifier.clickable { LinkUtils.openInFileManager(context, log.dstPath) }` (untuk yang OK)
- `ScanItemRow` di `SortirScreen.kt` → tambah ikon/shortcut buka folder

---

## Temuan 3 — Thumbnail video masih berupa icon

### Gejala
Video menampilkan icon `Icons.Default.Videocam` (generic video camera) bukan actual frame/thumbnail dari video.

### Akar Masalah
**`VideoFrameDecoder` dari `coil-video` tidak diregistrasi ke Coil `ImageLoader`.**

Dependency sudah ada di `build.gradle.kts`:
```
io.coil-kt:coil-compose:2.7.0
io.coil-kt:coil-video:2.7.0
```

Dan `MediaThumb` di `SortitComponents.kt:144-151` sudah benar memanggil:
```kotlin
SubcomposeAsyncImage(model = File(path), ...)
```

**TAPI:** Coil secara default **tidak** meng-decode video frames. Perlu registrasi eksplisit `VideoFrameDecoder` via `ImageLoader`. Tanpa itu, Coil gagal load video → fallback ke slot `error` → tampil `FileTypeIcon`.

Pencarian di seluruh project untuk `VideoFrameDecoder`, `ImageLoader`, `ImageLoaderFactory` → **tidak ditemukan**.

`SortitApplication.kt` tidak implement `ImageLoaderFactory`.

### Saran Perbaikan

Implementasikan `ImageLoaderFactory` di `SortitApplication.kt`:

```kotlin
// SortitApplication.kt — implement ImageLoaderFactory
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

class SortitApplication : Application(), ImageLoaderFactory {
    // ... existing code ...

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
```

**Catatan:** `coil-video` menggunakan `MediaMetadataRetriever` di background thread. Pastikan file path valid dan file tidak corrupt — Coil akan otomatis fallback ke error slot jika gagal decode frame.

**Performa:** Tambah memory cache dan disk cache untuk menghindari re-decode frame yang sama berulang kali (terutama di list/LazyColumn).

---

## Temuan 4 — Penghapusan otomatis trash 14 hari belum diimplementasi

### Status: **Belum diimplementasi sama sekali.**

Pencarian di seluruh codebase:
- `WorkManager` → tidak ada
- `BroadcastReceiver` → tidak ada
- `Service` / `JobService` → tidak ada
- `Timer` / `AlarmManager` → tidak ada
- `Prefs` → tidak ada setting untuk trash retention period
- `SettingsDialog` → hanya toggle "Warna dinamis"

**Data:** `SortLogEntity` mencatat `timestamp` setiap file yang di-trash. `dstPath` berisi path di `.sortit-trash`. Tapi **tidak ada mekanisme apapun** untuk menghapus file lama dari trash.

**Dampak:** Trash files akumulasi tanpa batas. Storage user terus terisi.

### Saran Perbaikan

**Bagian A — Data & Logic:**

1. Tambah setting retention period di `Prefs.kt`:
```kotlin
var trashRetentionDays: Int
    get() = sp.getInt("trash_retention_days", 14)
    set(v) = sp.edit { putInt("trash_retention_days", v) }
```

2. Buat `TrashCleanupUseCase.kt`:
```kotlin
class TrashCleanupUseCase(
    private val fileOps: FileOps,
    private val prefs: Prefs
) {
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (prefs.trashRetentionDays * 86_400_000L)
        val trashDir = File(FileOps.TRASH_ROOT)
        if (!trashDir.exists()) return@withContext
        trashDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }
}
```

**Bagian B — Scheduling:**

Gunakan `WorkManager` periodic work (1x sehari):

```kotlin
// TrashCleanupWorker.kt
class TrashCleanupWorker(context: Context, params: WorkerParameters)
    : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SortitApplication
        TrashCleanupUseCase(app.fileOps, app.prefs).cleanup()
        return Result.success()
    }
}

// SortitApplication.kt — schedule saat onCreate
WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "trash_cleanup",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS).build()
)
```

**Bagian C — UI/UX di Settings:**

Tambahkan pengaturan retention period di `SettingsDialog`:

```kotlin
// MainScreen.kt — SettingsDialog, tambah section "Sampah"
var retentionDays by remember { mutableIntStateOf(app.prefs.trashRetentionDays) }

SectionCard(title = "Sampah otomatis") {
    Text("Hapus file di trash setelah $retentionDays hari")
    Slider(
        value = retentionDays.toFloat(),
        onValueChange = { retentionDays = it.toInt() },
        valueRange = 1f..90f,
        steps = 0
    )
    Row(horizontalArrangement = Arrangement.SpaceBetween) {
        Text("1 hari")
        Text("90 hari")
    }
    // Tombol "Bersihkan sekarang" untuk manual cleanup
}
```

**Tambahan UX:** Saat scan result, tampilkan banner informatif: *"File yang di-trash akan dihapus otomatis setelah X hari"*.

---

## Temuan 5 — Animasi loading/progress scanning belum tampil sebagai pop-up

### Gejala
Ketika user mulai scan, **seluruh UI tabs (dashboard/rules/monitor) hilang** dan digantikan full-screen `ScanningScreen`. User kehilangan navigasi selama proses scan berlangsung.

### Akar Masalah
**State-driven full-screen swap, bukan dialog/overlay.**

Di `MainScreen.kt:97-101`:
```kotlin
val scanState by scanVm.state.collectAsState()
when (val s = scanState) {
    is ScanUiState.Idle -> MainTabs(dashboardVm, templateVm, monitorVm, scanVm)
    else -> ScanFlowScreen(state = s, scanVm = scanVm)
}
```

`ScanningScreen` dan `RunningScreen` di `SortirScreen.kt` menggunakan:
```kotlin
Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { ... }
```

Ini **full-screen replacement** — bukan `AlertDialog` atau overlay dialog.

**Masalah UX:**
- User tidak bisa cek rules/monitor selama scan
- Tidak ada cara cancel scan dari layar
- Progress indicator (`LinearProgressIndicator`) terlihat plain — tidak ada animasi menarik
- Tidak ada estimated time remaining

### Saran Perbaikan

**Opsi A (recommended) — Dialog overlay:**

Ubah `ScanningScreen` dan `RunningScreen` menjadi dialog yang overlay di atas `MainTabs`:

```kotlin
// MainScreen.kt — ubah state rendering
val scanState by scanVm.state.collectAsState()
Box(Modifier.fillMaxSize()) {
    MainTabs(dashboardVm, templateVm, monitorVm, scanVm)

    when (val s = scanState) {
        is ScanUiState.Scanning -> ScanProgressDialog(s, scanVm)
        is ScanUiState.Running -> SortProgressDialog(s, scanVm)
        is ScanUiState.ExistingPending -> ExistingPendingDialog(s, scanVm)
        is ScanUiState.Preview -> ScanPreviewScreen(s, scanVm)
        is ScanUiState.Done -> DoneDialog(s) { scanVm.reset() }
        is ScanUiState.Error -> ErrorDialog(s) { scanVm.reset() }
        is ScanUiState.Empty -> EmptyDialog(s) { scanVm.reset() }
        ScanUiState.Idle -> Unit
    }
}
```

```kotlin
// SortirScreen.kt — ubah ScanningScreen jadi dialog
@Composable
fun ScanProgressDialog(state: ScanUiState.Scanning, scanVm: ScanViewModel) {
    AlertDialog(
        onDismissRequest = { /* scan tidak bisa di-cancel tanpa stop coroutine,
                               tapi setidaknya user bisa kembali ke tab */ },
        title = { Text("Scanning...", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("${state.scanned} file dicek • ${state.found} cocok")
                if (state.current.isNotBlank()) OneLinePath(state.current)
            }
        },
        confirmButton = {
            TextButton(onClick = { scanVm.reset() }) { Text("Batal") }
        }
    )
}
```

**Opsi B (lebih elegan) — Scanning overlay dengan backdrop:**

```kotlin
@Composable
fun ScanOverlay(state: ScanUiState.Scanning) {
    // Semi-transparent backdrop
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    ) {
        // Centered card
        Card(
            modifier = Modifier.padding(32.dp).align(Alignment.Center),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Scanning...", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("${state.scanned} dicek • ${state.found} cocok")
                if (state.current.isNotBlank()) OneLinePath(state.current)
            }
        }
    }
}
```

**Poin UX tambahan:**
- Tambah `circularProgressIndicator` (indeterminate) saat scanning dimulai
- Tambah estimated file count atau time indicator jika memungkinkan
- Tambah tombol "Batal" pada dialog untuk stop scan (perlu add cancellation support ke `ScanSortUseCase` via `Job.cancel()`)
- Pada `RunningScreen` (proses pemindahan), tampilkan file yang sedang diproses dengan progress bar determinate
- Animasi transisi masuk/keluar dialog (material motion)

---

## Temuan Tambahan — Optimasi & Kebersihan Kode

### T-1 — `POST_NOTIFICATIONS` permission di-declare tapi tidak dipakai

`AndroidManifest.xml` mendeklarasikan `android.permission.POST_NOTIFICATIONS` tapi tidak ada kode yang membuat notifikasi. Permission ini akan trigger runtime prompt di Android 13+ tanpa manfaat.

**Saran:** Hapus dari manifest sampai fitur notifikasi benar-benar diimplement (misal: notifikasi saat trash cleanup selesai).

### T-2 — `RealFileOps.mimeOf()` hardcode — tidak extensible

`FileOps.kt:54-72` — mime type ditentukan manual dari extension. Jika ada file `.heic`, `.avif`, `.opus`, dll → return `null`.

**Saran:** Gunakan `MimeTypeMap.getSingleton().getMimeTypeFromExtension()` sebagai fallback:
```kotlin
override fun mimeOf(file: File): String? {
    val name = file.name.lowercase()
    // ... existing hardcode ...
    val ext = name.substringAfterLast('.', "")
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
}
```

### T-3 — `ScanSortUseCase.executeMany()` sequential per template

`ScanSortUseCase.kt:34` — scan dilakukan **sequential per template** dalam satu `for` loop. Jika ada 10 rules dengan scan path overlap, waktu scan = 10x scan pertama.

**Saran:** Parallel scan dengan `parallel {}` atau `coroutineScope { templates.map { launch { ... } } }`, tapi perlu `ConcurrentHashMap` untuk `seen` set. Hanya worth it jika ada >3 rules aktif.

### T-4 — `ScanSessionRepository.setAllExcluded()` N+1 query

`ScanSessionRepository.kt:59-63` — loop setiap item, panggil `updateStatus` satu per satu.

```kotlin
suspend fun setAllExcluded(sessionId: Long, excluded: Boolean) {
    db.scanItemDao().forSession(sessionId).forEach {
        // N+1: satu query select + N queries update
        db.scanItemDao().updateStatus(it.id, if (excluded) "EXCLUDED" else "PENDING")
    }
}
```

**Saran:** Tambah bulk update query:
```kotlin
@Query("UPDATE scan_items SET status = :status WHERE sessionId = :sessionId AND (status = 'PENDING' OR status = 'EXCLUDED')")
suspend fun bulkUpdateStatus(sessionId: Long, status: String)
```

### T-5 — SortLogEntity tidak punya auto-cleanup

Log entries di `sort_logs` table akumulasi tanpa batas. Tidak ada `DELETE` query atau retention policy.

**Saran:** Tambah periodic cleanup (misal: hapus log > 90 hari) atau tambah limit di `observeRecent()`.

### T-6 — `SeedUseCase` race condition potential

`SortitApplication.kt:29` — seed dijalankan di `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. Jika user buka app dan langsung navigate ke rules sebelum seed selesai, rules list kosong.

**Saran:** Seed synchronously di `onCreate` (file operations-nya ringan) atau tambah loading state di UI.

---

## Prioritas Perbaikan

| # | Temuan | Impact | Effort | Prioritas |
|---|---|---|---|---|
| 1 | Path sistem bug saat edit rule | 🔴 Blocker — rule default tidak bisa diedit | S | **P0** |
| 3 | Video thumbnail tidak muncul | 🔴 Visual bug jelas | S | **P0** |
| 5 | Scanning full-screen bukan pop-up | 🟠 UX buruk — user kehilangan navigasi | M | **P1** |
| 4 | Auto-delete trash belum ada | 🟠 Storage leak | M | **P1** |
| 2 | Linked file manager belum ada | 🟡 Missing feature | M | **P2** |
| T-1 | Unused POST_NOTIFICATIONS | 🟢 Cleanup | XS | **P3** |
| T-2 | Hardcoded mime types | 🟢 Extensibility | S | **P3** |
| T-3 | Sequential scan | 🟡 Performa saat multi-rule | M | **P3** |
| T-4 | N+1 query setAllExcluded | 🟡 DB performance | XS | **P3** |
| T-5 | Log unbounded | 🟢 Storage | S | **P3** |
| T-6 | Seed race condition | 🟢 Edge case | XS | **P3** |

> S = Small (< 1 file, < 100 LOC), M = Medium (2-4 files, 100-500 LOC), XS = Trivial

---

## Status Perbaikan (2025-07-18)

### Temuan 1 — Path sistem bug → FIXED
- `SystemExcludes.kt`: Ditambah `isBlockedTarget()` yang exclude `APP_ROOT` dari validasi target
- `TemplateScreen.kt:239`: Diganti dari `isSystemPath()` ke `isBlockedTarget()`
- Rule default (`/storage/emulated/0/Sortit/sampah`) sekarang bisa disimpan ulang tanpa error

### Temuan 2 — Linked file manager → FIXED
- `LinkUtils.kt`: Utility baru — buka file/folder via FileProvider dengan 3-level fallback
- `file_paths.xml`: FileProvider path configuration
- `AndroidManifest.xml`: Ditambah `<provider>` declaration untuk FileProvider
- `MonitorScreen.kt`: `MonitorFileRow` sekarang punya `clickable` → `LinkUtils.openInFileManager()`
- `DashboardScreen.kt`: `LogRow` sekarang punya `clickable` → buka destination folder

### Temuan 3 — Video thumbnail → FIXED
- `SortitApplication.kt`: Implement `ImageLoaderFactory` dengan `VideoFrameDecoder.Factory()`
- Ditambah memory cache (25%) dan disk cache (2%)
- `coil-video:2.7.0` dependency sudah ada, sekarang aktif

### Temuan 4 — Auto-delete trash → FIXED
- `TrashCleanupUseCase.kt`: Cleanup file berdasarkan `trashRetentionDays` (default 14 hari)
- `TrashCleanupWorker.kt`: WorkManager periodic (1x/hari), juga cleanup log >90 hari
- `Prefs.kt`: Ditambah `trashRetentionDays` property (1-90 hari)
- `SortitApplication.kt`: Schedule WorkManager saat onCreate
- `build.gradle.kts`: Ditambah `work-runtime-ktx:2.9.1`
- `SettingsDialog`: Ditambah slider retensi trash (1-90 hari)
- `SortLogDao.kt`: Ditambah `deleteOlderThan()` untuk cleanup log lama

### Temuan 5 — Scanning overlay → FIXED
- `SortirScreen.kt`: `ScanningScreen`/`RunningScreen`/`DoneScreen` diubah dari full-screen replacement ke `AlertDialog` overlay
- `MainScreen.kt`: Scan state rendering diubah — `MainTabs` tetap visible, scan states muncul sebagai dialog di atasnya
- User bisa melihat tabs selama scan berlangsung
- Tombol "Batal" ditambahkan pada scanning dialog

### Minor Optimizations → DONE
- `FileOps.mimeOf()`: Ditambah 15+ mime type (heic, avif, opus, flac, zip, rar, 7z, txt, csv, json, xml, html) + fallback ke `MimeTypeMap`
- `ScanSessionDao.kt`: Ditambah `bulkUpdateStatus()` — single query untuk update semua item
- `ScanSessionRepository.kt`: `setAllExcluded()` sekarang pakai bulk update (N+1 → 1 query)
- `SortLogDao.kt`: Ditambah `deleteOlderThan()` untuk periodic cleanup
- `TrashCleanupWorker.kt`: Cleanup log >90 hari sekalian dengan trash cleanup
- `AndroidManifest.xml`: `POST_NOTIFICATIONS` permission dihapus (tidak dipakai)

### App Icon → DONE
- `ic_launcher_foreground.xml`: Vector drawable — S geometris stroke (#6D5BD0) + folder terbuka (#FFB74D/#FFA726) + panah melengkung (#FF8F00)
- `ic_launcher.xml` + `ic_launcher_round.xml`: Adaptive icon config
- `colors.xml`: Background color (#FFFFFF)

### File yang Dimodifikasi
| File | Perubahan |
|---|---|
| `SystemExcludes.kt` | + `isBlockedTarget()` |
| `TemplateScreen.kt` | validate() pakai `isBlockedTarget`, + modifier param |
| `SortitApplication.kt` | + `ImageLoaderFactory`, + WorkManager setup |
| `Prefs.kt` | + `trashRetentionDays` |
| `MainScreen.kt` | Scan overlay, Settings dialog, modifier params |
| `SortirScreen.kt` | Scanning/Running/Done → AlertDialog overlay |
| `MonitorScreen.kt` | + click handler via LinkUtils, + modifier param |
| `DashboardScreen.kt` | + click handler via LinkUtils, + modifier param |
| `AndroidManifest.xml` | + FileProvider, - POST_NOTIFICATIONS |
| `build.gradle.kts` | + work-runtime-ktx |
| `FileOps.kt` | + mimeOf fallback + 15 mime types |
| `ScanItemDao.kt` | + `bulkUpdateStatus()` |
| `ScanSessionRepository.kt` | `setAllExcluded()` pakai bulk update |
| `SortLogDao.kt` | + `deleteOlderThan()` |

### File yang Dibuat Baru
| File | Deskripsi |
|---|---|
| `util/TrashCleanupUseCase.kt` | Logic cleanup trash files |
| `util/TrashCleanupWorker.kt` | WorkManager worker |
| `util/LinkUtils.kt` | Buka file/folder di file manager |
| `res/drawable/ic_launcher_foreground.xml` | Icon foreground vector |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon |
| `res/mipmap-anydpi-v26/ic_launcher_round.xml` | Adaptive round icon |
| `res/values/colors.xml` | Icon background color |
| `res/xml/file_paths.xml` | FileProvider paths |
