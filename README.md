# Sortit

Android file sorter + path monitor. Offline, local-only.

## Fitur (v1)
- Template sortir: nama + ekstensi + folder tujuan. Mode sumber "Scan Semua" (exclude path sistem) atau "Folder Pilihan".
- Deep scan + preview gate: sebelum pindah, tampil preview (thumbnail, path, size), user centang file dikecualikan.
- Pemantau path (monitor) realtime via FileObserver, thumbnail + metadata.
- Default: template sampah + dokumen, monitor WA Statuses (bisa dihapus/nonaktifkan).
- Safety: pindah ke .sortit-trash, bukan hapus langsung.
- Akses: MANAGE_EXTERNAL_STORAGE (all-files).

## Build
- CI: `.github/workflows/ci.yml` (build + unit test, ketat).
- CD: `.github/workflows/cd.yml` (build release APK, hanya jika CI hijau).

## Rilis
APK sideload. Target F-Droid bila matang.

## PRD
Lihat `PRD-Sortit.md` dan `Ringkasan-Sortit.md` di workspace induk.
