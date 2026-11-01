# Changelog - Smart Keyboard Pro

All notable changes to this project will be documented in this file.

## [1.4.5] - 2026-10-25

### ⌨️ UI/UX Keyboard Layout & Feature Improvements
- **Perbaikan Tata Letak & Tombol Khusus**:
  - Menghapus tombol smiley (`😊`) dari bilah kontrol bawah.
  - Memindahkan akses menu Emoji/Smiley ke **tekan lama (long-press) pada tombol Enter**.
  - Memperkecil ukuran/lebar tombol Enter agar lebih ergonomis.
  - Menggabungkan koma ke dalam tombol titik (`.`): tekan lama tombol titik untuk memasukkan koma (`,`).
- **Pengaturan Ukuran Font Tombol**:
  - Menambahkan fitur pengaturan ukuran font teks tombol keyboard (*Key Font Size*) di Theme Studio / Pengaturan (rentang 12sp–24sp).
- **Umpan Balik Audio/Haptic Berbeda untuk Tombol Hapus**:
  - Tombol Backspace `⌫` kini memiliki efek suara dan getar haptic yang berbeda dan khas dibandingkan tombol huruf standar (`FX_KEYPRESS_DELETE`).
- **Split Keyboard Mode Landscape (Gaya Samsung Keyboard)**:
  - Mode lanskap (*landscape*) kini secara otomatis mendukung layout **Split Keyboard** (terbelah menjadi bagian kiri dan kanan dengan jeda di tengah) untuk kenyamanan mengetik dua jempol di layar lebar/tablet.
- **Optimasi Ketikan & Stabilitas**:
  - Penyempurnaan manajemen state pengetikan, komposisi kata, dan peredaman recomposition untuk performa IME yang jauh lebih stabil dan responsif.

### 🔐 Build & Keystore Fingerprint
- Menaikkan `versionCode` ke `145` dan `versionName` ke `"1.4.5"`.
- Ditandatangani menggunakan kunci rilis resmi `my-upload-key.jks` (SHA-1: `C4:EB:DA:FF:7D:FC:90:07:6F:71:D3:95:BB:BE:F6:60:F9:88:D1:F3`).

---

## [1.4.4] - 2026-10-25

### ⚡ Fitur Auto-Text Shortcut & Spacebar Expansion
- **Ekspansi Cepat dengan Tombol Spasi (Spacebar Auto-Expand)**:
  - Pengguna cukup mengetik keyword/singkatan (contoh: `thx`, `halo`, `alamat`, `rek`) lalu menekan tombol **SPASI**.
  - Keyboard secara cerdas mendeteksi singkatan kata sebelum kursor, menghapus singkatan tersebut, dan langsung menggantinya (*auto-expand*) menjadi kalimat lengkap yang telah ditentukan pengguna.
  - Berfungsi mulus di seluruh aplikasi Android (IME InputConnection) dan juga di keyboard pratinjau aplikasi.
- **Manajemen Auto-Text Lengkap (CRUD UI & Real-Time Preview)**:
  - Antarmuka baru untuk menambah, mengedit, mencari, dan menghapus daftar pintasan Auto-Text.
  - Dialog tambah/edit dilengkapi kartu **Pratinjau Ekspan Spasi** interaktif secara real-time (`[keyword] + [Spasi] ➔ [Teks Lengkap]`).
  - Badge indikator status dan salin cepat ke clipboard.
- **Sinkronisasi Cloud Database Firestore**:
  - Auto-Text kini tersimpan dan tersinkronisasi langsung ke Firebase Firestore pada koleksi `/users/{userId}/auto_text_shortcuts/{shortcutId}`.
  - Tombol **Sync Cloud** di bilah Auto-Text untuk sinkronisasi dua arah instan (merge cloud & lokal).
  - Snapshot backup cloud dan live Cloud Database Inspector kini otomatis mencakup pintasan Auto-Text.
  - Keamanan Firestore diperbarui dengan aturan otorisasi berbasis UID pengguna.

### 🔐 Build & Keystore Fingerprint
- Menaikkan `versionCode` ke `144` dan `versionName` ke `"1.4.4"`.
- Ditandatangani menggunakan kunci rilis resmi `my-upload-key.jks` dengan skema v1 (JAR) & v2 (APK Signature):
  - **SHA-1**: `C4:EB:DA:FF:7D:FC:90:07:6F:71:D3:95:BB:BE:F6:60:F9:88:D1:F3`
  - **SHA-256**: `85:56:64:F7:2E:8C:F5:D5:C3:3C:5F:48:4F:51:83:4A:6A:FF:30:80:6B:F9:9F:69:DF:54:B1:7C:A0:EB:3D:B9`
- Berkas rilis siap pakai mandiri disediakan: `SmartKeyboardPro-v1.4.4-release-signed.apk`.

---

## [1.4.3] - 2026-10-25

### 🔐 Multi-Device & APK Update Optimization
- **Dukungan Seamless In-Place Update (Tanpa Uninstall)**:
  - Menaikkan `versionCode` ke `143` sehingga Android Package Manager mengenali APK sebagai pembaruan sah dan langsung menimpa (*in-place update*) versi 1.4.2 tanpa memicu pesan galat bentrok paket.
  - Mengaktifkan skema penandatanganan ganda secara eksplisit: **v1 Scheme (JAR signing)** dan **v2 Scheme (APK Signature Scheme)** untuk memastikan kompatibilitas penuh di seluruh Android OS dan installer pihak ketiga/vendor OEM.
- **Konsistensi Fingerprint Lintas Perangkat**:
  - Semua build (`release` dan `debug`) dijamin ditandatangani menggunakan kunci yang sama (`my-upload-key.jks`):
    - **SHA-1**: `C4:EB:DA:FF:7D:FC:90:07:6F:71:D3:95:BB:BE:F6:60:F9:88:D1:F3`
    - **SHA-256**: `85:56:64:F7:2E:8C:F5:D5:C3:3C:5F:48:4F:51:83:4A:6A:FF:30:80:6B:F9:9F:69:DF:54:B1:7C:A0:EB:3D:B9`
  - Berkas rilis siap pakai mandiri disediakan di root repository: `SmartKeyboardPro-v1.4.3-release-signed.apk`.

---

## [1.4.2] - 2026-10-25

### 🚀 Highlights & New Features
- **Peningkatan Drawer Emoji Lengkap (230+ Emoji)**:
  - Tampilan drawer emoji baru berbasis `LazyVerticalGrid` (7 kolom) yang dapat digulir (*smooth scrolling*).
  - Dilengkapi 5 tab kategori emoji populer:
    - 😀 **Wajah & Emosi** (60 ekspresi lengkap: senyum, tertawa, menangis, kaget, jahil, dll.)
    - 👋 **Tangan & Gestur** (40 pilihan: jempol, tepuk tangan, tos, doa, telunjuk, dll.)
    - ❤️ **Hati & Cinta** (40 varian warna hati, mawar, bintang berkilau)
    - 🎉 **Seru & Makanan** (40 pilihan: roket, piala, pizza, minuman, pesta, hadiah)
    - 🐱 **Hewan & Alam** (40 pilihan: anjing, kucing, panda, kupu-kupu, dll.)
  - Tombol **Backspace `⌫`** terintegrasi langsung di bilah atas emoji drawer untuk menghapus secara instan tanpa harus berpindah mode.

### 🛠️ Bug Fixes & UI Enhancements
- **Menghilangkan Tombol `[ABC]` Ganda**:
  - Memperbaiki logika pergantian mode keyboard pada baris navigasi bawah:
    - Dalam mode **ALPHA**: Tombol 1 = `?123` | Tombol 2 = `😊`
    - Dalam mode **NUMBERS**: Tombol 1 = `ABC` | Tombol 2 = `😊`
    - Dalam mode **EMOJI**: Tombol 1 = `ABC` | Tombol 2 = `?123`
  - Tidak ada lagi tombol `[ABC] [ABC]` yang muncul berdampingan saat membuka mode emoji.
- **Ikon Tombol Enter Diperbesar & Diperjelas**:
  - Menggantikan karakter teks unicode enter yang kecil dengan ikon vektor *return arrow* kustom berukuran **24dp** dengan ketebalan garis **2.8dp**, berujung lengkung (*StrokeCap.Round*), kontras tinggi putih terang, dan tidak terpotong.
- **Akses Pengaturan Keyboard**:
  - Tombol pengaturan keyboard tetap jelas terlihat dan mudah diakses saat tema tekstur latar belakang (*pattern/custom photo background*) diaktifkan.

### 🔐 Build & Security
- **Integritas Keystore & Tanda Tangan Konsisten**:
  - Ditandatangani menggunakan `my-upload-key.jks` yang sama dengan fingerprint identik:
    - **SHA-1**: `C4:EB:DA:FF:7D:FC:90:07:6F:71:D3:95:BB:BE:F6:60:F9:88:D1:F3`
    - **SHA-256**: `85:56:64:F7:2E:8C:F5:D5:C3:3C:5F:48:4F:51:83:4A:6A:FF:30:80:6B:F9:9F:69:DF:54:B1:7C:A0:EB:3D:B9`
  - Update versi aplikasi dapat langsung diinstal tanpa terjadi konflik tanda tangan (*signature mismatch*).

---

## [1.4.1] - 2026-10-25
- Perbaikan layout baris kontrol bawah dan ukuran tombol enter.
- Optimasi ikon pengaturan pada mode background berpola dan kustom bitmap.
- Pembersihan dependensi dan perapihan keystore signing.

---

## [1.4.0] - 2026-10-24
- Integrasi Cloud Backup & Restore berbasis Firebase Firestore dengan Google Sign-In.
- Sinkronisasi kamus pribadi, shortcut auto-text, dan kustomisasi tema ke akun cloud pengguna.
- Mode offline fallback penuh saat tidak ada koneksi internet.

---

## [1.3.2] - 2026-10-23
- Penambahan pengaturan bentuk tombol keyboard (Rounded, Pill, Squircle, Sharp, Cut Corner).
- Pengaturan transparansi tombol (*Key Opacity*) dan tinggi tombol keyboard (*Key Height*).

---

## [1.2.0] - 2026-10-22
- Dukungan tema foto kustom dari galeri perangkat.
- 7 palet warna tema bawaan (Midnight, Cyberpunk, Emerald, Sunset, Neon, dll.).
- Sistem pembaruan aplikasi langsung (*in-app updater*) via GitHub Releases.
