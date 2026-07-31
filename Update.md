# Update Log — Sortit

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
- [~] Konsistensi dialog UI/UX penuh (sebagian; dialog scroll + pola AlertDialog sama)
