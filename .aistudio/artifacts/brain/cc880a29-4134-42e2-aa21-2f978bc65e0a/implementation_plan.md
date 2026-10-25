# Solusi Update & Penandatanganan APK Smart Keyboard Pro (v1.4.3)

Rencana aksi untuk mengatasi kendala kegagalan instalasi timpa (overwrite update) pada perangkat fisik, menjelaskan fungsi tombol panel AI Studio, serta menyiapkan rilis pembaruan v1.4.3 dengan tanda tangan digital identik.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Akar Penyebab Utama Ditemukan:**
> 1. **Tombol "Instal" di Kolom Kiri Preview AI Studio**: Tombol tersebut berfungsi untuk memasang/merestart aplikasi di dalam **Emulator Web Streaming (browser)**, bukan mengirim atau menginstal APK ke ponsel fisik pengguna.
> 2. **Aturan Overwrite Android OS (`versionCode`)**: Ponsel saat ini sudah terpasang versi **1.4.2** (`versionCode: 142`). Sistem Android Package Installer **menolak instalasi manual APK yang memiliki `versionCode` sama persis (142)** dengan pesan galat bentrok paket (*conflicts with existing package*), kecuali dinaikkan versinya menjadi `versionCode: 143`.
> 3. **Skema Penandatanganan APK (v1 JAR + v2 Scheme)**: Konfigurasi Gradle perlu memastikan `v1SigningEnabled = true` dan `v2SigningEnabled = true` agar kompatibel penuh di semua pengelola berkas Android.

- **Keputusan Terkonfirmasi**:
  - Menggunakan fingerprint resmi dari `my-upload-key.jks` (SHA-1: `C4:EB:DA:...`, SHA-256: `85:56:64:...`).
  - Menetapkan target pembaruan ke versi **1.4.3** (`versionCode: 143`) agar Android mengenali berkas sebagai pembaruan sah dan langsung menimpa tanpa harus uninstall.

---

### 1. Overview & Core Concept

- **Apa yang diselesaikan**:
  - Memperbaiki konfigurasi `signingConfigs` di Gradle agar mengaktifkan skema penandatanganan ganda (`v1SigningEnabled = true` dan `v2SigningEnabled = true`).
  - Menaikkan versi aplikasi menjadi **v1.4.3** (`versionCode: 143`) sehingga pengguna di HP manapun dapat langsung menginstal file APK baru dan menimpa versi 1.4.2 tanpa kehilangan riwayat pengaturan atau kamus.
  - Menghasilkan berkas APK rilis bertanda tangan `SmartKeyboardPro-v1.4.3-release-signed.apk` yang siap diunduh langsung atau diunggah ke GitHub Releases.
  - Memperbarui `CHANGELOG.md` dan metadata Fastlane untuk v1.4.3.
- **Audiens**: Pengguna Smart Keyboard Pro yang beralih perangkat laptop dan memperbarui aplikasi di HP.
- **Nilai Utama**: Pembaruan berjalan 100% mulus (seamless update) tanpa perlu uninstall aplikasi lama terlebih dahulu.

---

### 2. User Experience & Visual Design

- **Alur Pembaruan Pengguna (User Flow)**:
  1. Pengguna mengunduh berkas rilis `SmartKeyboardPro-v1.4.3-release-signed.apk` dari AI Studio atau GitHub Release.
  2. Pengguna membuka berkas di HP (yang saat ini sudah terpasang v1.4.2).
  3. Dialog sistem Android menampilkan: *"Apakah Anda ingin menginstal pembaruan untuk aplikasi ini? Data Anda tidak akan hilang."*
  4. Pengguna menekan tombol **Update / Instal**.
  5. Proses instalasi selesai dan seluruh keyboard, pengaturan, serta kamus tetap utuh tanpa reset.
- **Klarifikasi Tombol AI Studio**:
  - Tombol **"Install"** di samping emulator preview web adalah pengontrol internal emulator browser.
  - Untuk memasang di HP fisik, pengguna menggunakan file APK hasil build rilis (`app-release.apk` atau `SmartKeyboardPro-v1.4.3-release-signed.apk`).

---

### 3. Key Product Decisions & Trade-Offs

- **Keputusan 1: Bumping Version Code ke 143 (v1.4.3)**
  - *Pendekatan*: Mengubah `versionCode` dari 142 ke 143 dan `versionName` ke "1.4.3".
  - *Alasan*: Android Package Manager melarang instalasi APK mandiri jika `versionCode` sama dengan aplikasi yang sudah terpasang di perangkat. Kenaikan nomor versi adalah syarat wajib dari sistem operasi Android untuk memicu pembaruan bertimpa (*in-place update*).
- **Keputusan 2: Penandatanganan Lengkap v1 + v2 di Gradle**
  - *Pendekatan*: Menambahkan parameter eksplisit `enableV1Signing = true` dan `enableV2Signing = true` pada blok `signingConfigs.release`.
  - *Alasan*: Menjamin kompatibilitas backward di semua versi Android dan installer bawaan vendor OEM (Samsung, Xiaomi, Oppo, Vivo, dsb.).
- **Keputusan 3: Penyelarasan Debug & Release**
  - *Pendekatan*: Tetap menjaga build `debug` dan `release` menggunakan keystore yang sama persis (`my-upload-key.jks`), sehingga preview browser dan file unduhan selalu memiliki fingerprint identik.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                 my-upload-key.jks                           │
│  SHA-1:   C4:EB:DA:FF:7D:FC:90:07:6F:71:D3:95:BB:BE:F6...   │
│  SHA-256: 85:56:64:F7:2E:8C:F5:D5:C3:3C:5F:48:4F:51:83...   │
└──────────────────────────────┬──────────────────────────────┘
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
┌──────────────────────────────┐    ┌──────────────────────────────┐
│       app-release.apk        │    │        app-debug.apk         │
│  versionCode: 143            │    │  versionCode: 143            │
│  v1 (JAR) + v2 (Full APK)    │    │  v1 (JAR) + v2 (Full APK)    │
└──────────────┬───────────────┘    └──────────────┬───────────────┘
               │                                   │
               ▼                                   ▼
┌──────────────────────────────┐    ┌──────────────────────────────┐
│  Ponsel Fisik (Manual APK /  │    │   AI Studio Web Emulator     │
│       GitHub Releases)       │    │     (Tombol Install Web)     │
│                              │    │                              │
│   Update Mulus (v1.4.2 ➜ 143)│    │    Sinkronisasi Preview      │
└──────────────────────────────┘    └──────────────────────────────┘
```

- **Langkah-Langkah Implementasi**:
  1. Perbarui `app/build.gradle.kts`:
     - `versionCode = 143`
     - `versionName = "1.4.3"`
     - Tambahkan `enableV1Signing = true` dan `enableV2Signing = true` di `signingConfigs.release`.
  2. Perbarui `CHANGELOG.md` dan Fastlane metadata (`fastlane/metadata/android/en-US/changelogs/143.txt`).
  3. Jalankan build rilis `gradle assembleRelease` dan verifikasi integritas tanda tangan menggunakan `apksigner`.
  4. Salin hasil build rilis bertanda tangan ke root sebagai `SmartKeyboardPro-v1.4.3-release-signed.apk`.
