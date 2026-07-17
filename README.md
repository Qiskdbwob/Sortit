# Sortit

Android file sorter + path monitor. Offline, local-only. UI v1.1 memakai Material 3 dashboard modern.

## Fitur (v1.1)
- Dashboard ringkasan: jumlah file dipindahkan, masuk trash, gagal, dan pending review.
- Rules: tambah/edit rule, mode sumber `Scan Semua` atau `Folder Pilihan`, path bisa input manual atau pilih via SAF (primary storage dipetakan ke path absolut).
- Scan multi-rule: FAB **Scan** membuka picker rule, user bisa memilih beberapa rule untuk discan bersamaan dalam satu review.
- Preview modern: thumbnail media, metadata, dan checkbox **Kecualikan**. Default semua file ikut ditindak; centang hanya untuk mengecualikan.
- Pending scan memory: hasil scan disimpan di Room. Jika user klik Scan saat masih ada review belum ditindak, app menawarkan **Lanjutkan**, **Scan ulang**, atau **Buang**.
- Aksi aman: `Pindah` ke folder target rule atau `Trash` ke `/storage/emulated/0/Sortit/.sortit-trash`. Tidak ada hapus permanen di v1.
- Monitor path: readable badge, jumlah file, thumbnail media, dan refresh realtime via FileObserver.
- Permission gate: All-files access untuk API 30+, izin legacy untuk Android di bawahnya.

## Teknis
- Kotlin + Jetpack Compose Material 3, Room, Coroutine/Flow, Coil.
- DB `sortit.db` version 2. Catatan: project masih memakai `fallbackToDestructiveMigration()` untuk fase dev.
- CI: `.github/workflows/ci.yml` (unit test + assemble debug).
- CD: `.github/workflows/cd.yml` (release APK setelah CI hijau).

## Build
```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Dokumen
- `PRD-Sortit.md` — requirement detail.
- `Ringkasan-Sortit.md` — ringkasan awam.
- `CHANGELOG-Sortit.md` — perubahan v1.1.
