# Update Log — Sortit

## Unreleased (audit kode menyeluruh)
_Perbaikan bug, optimasi, dan konsistensi UI-UX hasil audit penuh_

### 🔴 Bug fix
- **Timestamp file tidak rusak saat Pindah** — `RealFileOps.move()` sebelumnya selalu
  `setLastModified(now)`, jadi file yang dipindah ke folder tujuan kehilangan tanggal
  aslinya (foto berubah "hari ini" di galeri). Sekarang mtime = waktu masuk **hanya untuk
  tujuan trash** (dibutuhkan retensi cleanup); pindah biasa/undo mempertahankan tanggal asal
- **Riwayat "Dipindahkan" tidak ikut terhapus retensi trash** — `TrashCleanupWorker`
  dulu menjalankan `deleteOlderThan(cutoff)` yang menghapus SEMUA log (termasuk log pindah),
  jejak Undo hilang setelah N hari. Sekarang hanya `deleteTrashedOlderThan()` (log trash)
  + `trimTo(5000)` agar tabel `sort_logs` tetap terbatas
- **Izin notifikasi Android 13+ diminta saat runtime** — `POST_NOTIFICATIONS` dideklarasikan
  di manifest tapi tidak pernah diminta, sehingga notifikasi hasil auto-sortir senyap
  selalu gagal di API 33+; kini diminta setelah izin storage granted

### 🟠 Main-thread I/O (jank/ANR)
- **`HistoryViewModel`** — `refresh/undoToSource/moveTo/deletePermanent` membaca disk
  (walk trash + `exists()` per log + copy file) di main thread → dipindah ke `Dispatchers.IO`
- **`AutoApplyUseCase.applyOnRoots()`** — loop `walkDeep` + `length()/lastModified()` per
  file berjalan di caller (FileObserver → main thread) → seluruh body kini `withContext(IO)`
- **Monitor "Pilih semua"** — inspect hingga `MOVE_ALL_MAX` (5000 file) di main thread →
  dipindah ke coroutine + IO

### 💡 UI/UX
- **Konfirmasi aksi destruktif** — Auto semua, Trash file terpilih per-monitor, dan
  Hapus permanen di Riwayat kini punya dialog konfirmasi (konsisten dengan konfirmasi
  Pindah/Trash di preview scan)
- **Tombol Back di alur scan** — Back menutup review / membatalkan scan, bukan keluar app;
  saat proses Pindah/Trash berjalan, Back diabaikan agar aksi selesai
- **Tab survive rotasi** — `MainTabs` & tab Riwayat pakai `rememberSaveable` (rotasi layar
  tidak reset ke tab Beranda)
- **Tombol auto monitor disabled saat busy** — cegah klik ganda menjalankan auto dua kali

### 🔴 Bug fix (laporan pemakaian)
- **Pindah tidak lagi membuat subfolder ekstensi** — dulu memilih `/Download` menghasilkan
  `/Download/txt/1.txt`; sekarang file masuk **langsung** ke folder tujuan (`/Download/1.txt`).
  Berlaku untuk Pindah manual (`SortFilesUseCase`) maupun auto rule MOVE (`AutoApplyUseCase`).
  Trash tetap dikelompokkan `.sortit-trash/<ekstensi>/` agar retensi per folder tetap rapi
- **"Hapus otomatis" kini benar-benar bekerja & terlihat** — tiga penyebab diperbaiki:
  (1) `freedBytes` dibaca **setelah** `delete()` sehingga selalu 0 — kini ukuran dibaca
  sebelum hapus; (2) cleanup hanya dijadwalkan harian sehingga terasa tidak jalan — kini
  ikut jalan **saat app start** dan **saat retensi diubah**; (3) tidak ada umpan balik —
  kini ada tombol **Bersihkan sekarang** di Pengaturan ("N file dihapus · X MB dibebaskan")

### ✨ Fitur baru
- **Widget home screen** — ringkasan `Hari ini: N dipindah · M ke sampah`, aktivitas
  terakhir, tombol segarkan, dan ketuk untuk membuka app. Ikut di-refresh setelah
  auto-apply selesai; query sekali-jalan (suspend) dari Room lewat `goAsync`
- **Quick Settings tile "Sortit"** — satu ketuk menjalankan auto rule via WorkManager
  one-time (`auto_apply_now`); subtitle menampilkan hasil terakhir; jika izin storage
  belum ada, ketukan membuka app untuk aktivasi

### 💡 UI/UX (animasi & micro-interaction)
- **Transisi antar tab** — `AnimatedContent` slide pendek + fade dengan arah sesuai
  urutan tab (Beranda → Rules → Monitor → Riwayat)
- **Angka dashboard beranimasi** — `ManifestTally` kini menganimasikan nilai
  (0 → 12) alih-alih teks statis
- **Progress Pindah/Trash halus** — `animateFloatAsState` pada `LinearProgressIndicator`
- **Empty state fade-in** — tidak lagi muncul mendadak

### 🧹 Bersih-bersih & CI
- **CI GitHub Actions kini menjalankan lint** — `./gradlew lintDebug` ditambahkan sebagai
  step eksplisit di `ci.yml` (sebelumnya hanya unit test + assembleDebug, padahal README/
  Update sudah mengklaim lint); hasil lokal: **0 error, 28 warning**
- **Dead code dihapus** — `PreviewSortUseCase` (0 pemakai) + engine `FileScanner.scanTemplate`
  yang hanya dipakai use case itu; `FileOps.childCount`, `SortLogDao.listOk` + wrapper
  repository, `SortLogDao.deleteOlderThan`, `ScanItemDao.deleteForSession`,
  `ScanSessionDao.updateTotal` — semuanya tanpa pemanggil di produksi
- **File nyasar dihapus** — folder duplikat `gradle/gradle/wrapper/` (wrapper kedua yang
  ter-commit) dan `res/xml/backup_rules.xml` kosong yang tidak direferensikan
- **`.gitignore`** — `app/build/` diabaikan agar artefak build tidak ikut ter-commit
- **Berbagai warning lint dibereskan**: label activity duplikat, `ObsoleteSdkInt` di
  `StorageAccess`, parameter `modifier` `SectionCard` dipindah ke posisi konvensi Compose,
  ikon launcher monokrom (ikon bertema Android 13+) — sisa warning hanya versi dependency,
  bentuk ikon lama, dan kebijakan Selected Photos (didokumentasikan)

### 📄 Dokumentasi
- README: klaim `fallbackToDestructiveMigration` diganti (kini ada migrasi eksplisit 1→2→3);
  langkah CI disinkronkan dengan `ci.yml` (unit test + lint + assembleDebug)

---

## v1.5.0 (patch commit: 24629cb, 164ba49)
_Semua perbaikan bug + improvisasi dari analisis kode penuh_

### 🔴 Bug fix P0 (kritis)
- **Batalkan scan benar-benar berhenti** — `ScanViewModel` simpan `scanJob`;
  `reset()/closePreview()` cancel coroutine supaya scan tidak jalan terus di background
- **Trash cleanup benar-benar bekerja** — `TrashCleanupUseCase` sekarang rekursif
  ke subfolder `<ext>/` di `.sortit-trash`; slider retensi di Settings kini fungsional
- **Move src==dst → SKIP, bukan OK** — `RealFileOps.move()` return `null` jika
  source sama dengan target (EC-02); angka sukses tidak lagi misleading

### 🟠 Bug fix P1
- **Label "Manifest hari ini" akurat** — Dashboard pakai `observeTodayCounts()`
  (filter sejak 00:00) + footnote total akumulasi
- **Undo fallback log hilang** — `HistoryViewModel.undoToSource()` tetap bisa
  restore walau log sudah dibersihkan worker; pesan per-file (nama + alasan gagal)
- **Riwayat sinkron setelah Pindah** — `moveTo()` update log ke dstPath baru
  (bukan hapus) supaya jejak tetap ada
- **Seed default selalu ada** — `SeedUseCase` v4 `ensureDefaultTemplates()` idempotent;
  `BootReceiver` jalankan seed ulang setelah reboot; `SortitApplication` bungkus seed
  dalam try/catch; tombol **Reset default** di Settings mengembalikan rule+monitor bawaan
- **Log FAIL muncul di Riwayat** — `listTrashed/listMoved` tidak filter `status=OK` lagi

### 🟡 Fitur baru P1
- **Konfirmasi sebelum Pindah/Trash** — dialog ringkas tampilkan jumlah file +
  peringatan sebelum aksi destruktif di preview scan
- **Preview scan lengkap** — search, filter ekstensi, filter per rule, sort
  (nama/ukuran/tanggal/arah), "Kecualikan/Sertakan semua terfilter"
- **Auto-apply realtime** — `MonitorViewModel.poke()` (FileObserver) trigger
  `AutoApplyUseCase` setelah debounce 400ms; file baru di folder monitor langsung diproses
- **Notifikasi auto-apply** — `NotifHelper` channel silent "Auto Sortir";
  `AutoApplyWorker` tampilkan notif ringkas setelah selesai
- **Reset default di Settings** — kembalikan rule sampah/dokumen + monitor WA
  tanpa uninstall app; fix tombol mode tema selected tidak bisa diklik

### 💡 UI/UX polish
- **Bottom nav ikon semantik** — Home (Beranda), List (Rules), Folder (Monitor),
  History (Riwayat); bukan duplikat Folder + Delete
- **Thumbnail media di Riwayat** — baris Sampah/Dipindahkan tampilkan preview gambar/video
- **Dashboard polling dihapus** — `MonitorStatusLine` tidak lagi `while(true)` 30 detik;
  refresh on-demand saat navigasi
- **Warning truncated lebih jelas** — Monitor tampilkan jumlah file sebenarnya +
  hint "Pilih semua" untuk proses semua file melewati cap UI
- **ManifestTally footnote** — strip angka dashboard tampilkan "total N diproses" di bawah

### 🔧 Perbaikan teknis
- **AutoApply rekursif** — `AutoApplyUseCase.applyOnRoots()` ganti `listFiles()` →
  `walkDeep()` supaya konsisten dengan scan manual (subfolder ikut diproses)
- **`sourceDirs` separator** — CSV koma → newline; `FileScanner.splitSourceDirs()`
  support format lama+baru (aman migrasi)
- **Dead code** — `PreviewSortUseCase.collect()` dihapus; `extOf()` di HistoryViewModel
  → `extensionFolder()` dari util (dedupe)
- **Permission** — `RECEIVE_BOOT_COMPLETED` + `POST_NOTIFICATIONS` ditambah di manifest

---
# Backlog Update.md — status v1.4.0

- [x] Rule kondisi size
- [x] Rule kondisi umur
- [x] Rule otomatis (folder sumber → MOVE/TRASH)
- [x] Screen Sampah (ext, search, multi, undo, pindah, hapus)
- [x] Screen Dipindahkan (tujuan, ext, search, multi, undo)
- [x] Exclude path global (Settings)
- [x] Exclude path per-rule (Rule editor)
- [x] Edit pemantau path
- [x] Batalkan review / scan (X)
- [x] Monitor pindah semua (cap MOVE_ALL_MAX, bukan cuma preview UI)
- [x] Konsistensi dialog UI/UX — semua aksi destruktif (Auto semua, Pindah, Trash,
  Hapus permanen) kini pakai konfirmasi; pola AlertDialog sama

## v1.6.0 (release: stabilitas + fitur preview)

### ✨ Fitur baru
- **Total size di preview scan** — header review tampilkan total ukuran seluruh file
  hasil scan (`3 rule · 248 file · 1.2 GB`); bottom bar tampilkan ukuran file yang
  siap ditindak (`210 siap · 980 MB siap`) — tahu besar data sebelum Pindah/Trash

### 🔧 Perbaikan
- **Migrasi monitor WA Statuses untuk user lama** — upgrade dari versi sebelumnya
  sekarang otomatis mendapat monitor `WA Statuses` (path `.Statuses/`), tidak lagi
  hanya untuk fresh install; idempotent (tidak duplikat), seed v4 → v5

### 🧪 Test & kualitas
- **Test unit migrasi seed** (`SeedUseCaseTest`) — 3 kasus: fresh install dapat
  monitor Statuses, upgrade v4 → v5 menambah Statuses tanpa duplikat, monitor
  custom user tetap dipertahankan
- **Smoke test UI** (`MainScreenSmokeTest`) — verifikasi layar izin storage
  (PermissionGate) tampil dan tombol berfungsi
- **CI diperketat** — lint (`lintDebug`) + unit test + assemble debug
- `Prefs.seeded` / `Prefs.seedVersion` dibuat `open` agar dapat di-override di
  unit test tanpa SharedPreferences
