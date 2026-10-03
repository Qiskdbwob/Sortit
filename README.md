# Sortit

Android **file sorter** + **path monitor**. Offline, local-only. Material 3.

**Rilis terbaru: [v1.4.0](https://github.com/Qiskdbwob/Sortit/releases/tag/v1.4.0)**

## Fitur

### Rules
- Ekstensi + folder tujuan + sumber `Scan Semua` / `Folder Pilihan`
- **Pindah = flat** — file masuk langsung ke folder tujuan (`/Download/1.txt`),
  bukan subfolder ekstensi; Trash tetap dikelompokkan `.sortit-trash/<ekstensi>/`
- **Filter size** (min/max MB) dan **umur** (file lebih tua dari N hari)
- **Auto rule**: file di folder sumber langsung **Pindah** atau **Trash** tanpa scan/review (wajib Folder Pilihan)
- **Exclude path per-rule** + **exclude global** (Pengaturan)
- Scan multi-rule → satu review (centang kecualikan → Pindah / Trash)

### Riwayat
- Tab **Sampah**: file di `.sortit-trash`, filter ekstensi, search, multi-select
- Tab **Dipindahkan**: by folder tujuan + ekstensi, search, multi-select
- **Undo** ke path asal, **pindah ke folder lain**, hapus permanen (sampah)

### Monitor
- Multi-folder path, badge readable, thumbnail
- **Edit** path monitor
- **Pilih semua** sampai cap pindah (5000) — file di luar preview ikut
- Tombol **Auto** jalankan rule otomatis terkait path

### Lainnya
- Preview scan + **X / batalkan** review & scan
- Pending scan memory (Lanjutkan / Scan ulang / Buang)
- Global exclude di Pengaturan
- Tema siang/malam/sistem + dynamic color
- Trash auto-cleanup (retensi 1–90 hari) + tombol **Bersihkan sekarang**
- **Widget home screen** — ringkasan "hari ini" (dipindah/trash) + aktivitas terakhir
  + tombol segarkan
- **Quick Settings tile** — ketuk "Sortit" untuk menjalankan auto rule tanpa buka app
- Animasi: transisi tab, angka dashboard, progress pindah/trash

## Alur singkat

```
Rule (ext + size/umur + exclude)
        │
   ┌────┴────┐
   │ Scan    │ Auto (folder sumber)
   ▼         ▼
 Review    langsung MOVE/TRASH
   │
 Pindah / Trash
   │
 Riwayat → Undo / pindah lagi
```

Filter scan: **System → Global exclude → Per-rule exclude → ekstensi → size/umur**

## Arsitektur

```
ui/       Compose + ViewModels (Beranda, Rules, Monitor, Riwayat, Scan flow)
domain/   FileScanner, ScanSort, SortFiles, AutoApply, ScanPath
repo/     FileOps, Template/Monitor/Exclude/SortLog/ScanSession
data/     Room sortit.db v3
util/     SystemExcludes, Prefs, StoragePaths, TrashCleanup
widget/   Widget home screen + Quick Settings tile
```

## Teknis
- Kotlin, Jetpack Compose Material 3, Room, Coroutines/Flow, Coil, WorkManager
- DB version **3** dengan **migrasi eksplisit** (`MIGRATION_1_2`, `MIGRATION_2_3`, defensif via
  `PRAGMA table_info`) — data user (rule, monitor, log) tetap aman saat upgrade; tanpa
  `fallbackToDestructiveMigration`
- minSdk 24, target/compile 34
- CI: unit test + lint + assembleDebug · CD: signed release APK (keystore secrets)

## Build

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease   # butuh signing di local.properties atau env CI
```

`local.properties` (jangan commit):

```properties
sdk.dir=...
RELEASE_STORE_FILE=path/to/sortit-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

## Unduh
- GitHub Releases: https://github.com/Qiskdbwob/Sortit/releases
- APK signed: update dari 1.2+ keystore sama tanpa uninstall

## Lisensi
MIT — [LICENSE](LICENSE)

## Catatan keamanan
- Butuh **All files access** (API 30+)
- Auto rule **tidak** diizinkan mode “semua storage”
- Folder sistem + trash app selalu di-block scan

## CI & Testing (GitHub Actions)

Workflow `.github/workflows/ci.yml` otomatis jalan pada push/PR ke `main`:

1. **Unit test** — `./gradlew testDebugUnitTest`
2. **Lint / static analysis** — `./gradlew lintDebug` (harus 0 error)
3. **Build debug APK** — `./gradlew assembleDebug`

CD (`.github/workflows/cd.yml`) merilis APK signed hanya setelah CI sukses.

Jalankan lokal (butuh JDK 17 + Android SDK):

```bash
./gradlew testDebugUnitTest   # unit test
./gradlew lintDebug           # static analysis
./gradlew assembleDebug       # APK debug
```

> CI adalah jalur verifikasi utama: unit test, lint, dan build debug APK dijalankan di GitHub Actions. Artefak APK debug bisa diunduh dari Actions.

## Panduan Izin Storage (All Files Access)

Aplikasi butuh akses seluruh storage untuk scan & sortir file.

- **Android 11+ (API 30+)**: buka **Pengaturan → Aplikasi → Sortit → Izin → Semua akses file** (All files access), aktifkan.
- **Android 10 ke bawah**: izin **Penyimpanan** (Storage) akan diminta saat pertama kali membuka aplikasi.
- Jika izin ditolak, aplikasi menampilkan layar "Izin storage dibutuhkan" — tekan **Buka pengaturan** untuk mengaktifkan manual.
- Auto rule **tidak** diizinkan mode "semua storage" (hanya folder pilihan) demi keamanan.
