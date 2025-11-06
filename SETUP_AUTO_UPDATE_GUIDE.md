# Setup Auto-Update di Aplikasi Cek Picklist

## 🎯 Tujuan

Membuat fitur auto-update di aplikasi bekerja dengan sempurna - user bisa langsung download & install APK dari dalam aplikasi, tidak perlu ke GitHub manual.

## ✅ Status Implementasi

### Sudah Ada ✅
- ✅ `UpdateChecker.kt` - Class untuk cek, download, dan install update
- ✅ FileProvider configuration di AndroidManifest.xml  
- ✅ `file_paths.xml` untuk sharing APK file
- ✅ Permission REQUEST_INSTALL_PACKAGES
- ✅ Integration di HalamanAwalActivity
- ✅ Notification progress saat download
- ✅ Dialog konfirmasi install
- ✅ Error handling lengkap

### Yang Perlu Dilakukan ⚠️
- ⚠️ **Create GitHub Release dengan APK sebagai asset**

## 🐛 Masalah Saat Ini

Ketika user membuka aplikasi v5.1.4:
1. ✅ Dialog "Update Tersedia" muncul (v5.1.5 detected)
2. ✅ User klik "📥 Download & Install"
3. ❌ **Download GAGAL** → Karena GitHub Release belum dibuat

### Root Cause:

`UpdateChecker` mencoba download APK dari:
```
https://api.github.com/repos/koden01/cekpicklist/releases/latest
```

Response yang diharapkan:
```json
{
  "tag_name": "v5.1.5",
  "assets": [
    {
      "name": "CekPicklist-v5.1.5-release.apk",
      "browser_download_url": "https://github.com/.../CekPicklist-v5.1.5-release.apk"
    }
  ]
}
```

**Tapi**: Release v5.1.5 belum dibuat, jadi `assets` array kosong → download gagal!

## 📝 Solusi: Create GitHub Release

### Metode 1: Manual (Recommended - Mudah & Cepat)

#### Step 1: Buka Halaman Create Release

Buka browser dan kunjungi:
```
https://github.com/koden01/cekpicklist/releases/new
```

#### Step 2: Pilih Tag

- **Choose a tag**: Pilih `v5.1.5` dari dropdown
- Atau ketik `v5.1.5` jika tidak ada di list

#### Step 3: Release Title

```
v5.1.5 - Fix Cache & Sisa Value Update
```

#### Step 4: Release Description

Copy-paste ini:

```markdown
## 🎉 Release v5.1.5

### ✨ What's New

- Fix cache invalidation dengan kolom `update_at` dari Supabase
- Fix nilai Sisa tidak update setelah scan
- Improve thread management untuk UI updates (Dispatchers.IO & Main)
- Add 100ms delay untuk sinkronisasi cache yang lebih baik
- Enhanced logging untuk debugging

### 📱 Download & Install

**📥 Direct Download (Recommended)**
Download APK file di bawah, lalu install langsung di device Anda.

**🔄 Auto Update**
Aplikasi akan otomatis detect update dan menawarkan download & install langsung dari dalam aplikasi!

### 🔧 Technical Changes

#### Cache System
- Add `update_at` field di `BarcodeSessionRecord`
- Implement cache validation menggunakan timestamp `update_at`
- Function `getLatestExpedisiUpdateAt()` untuk cek timestamp terbaru

#### UI Fixes
- Fix `BarcodeInputFragment` thread management
- Proper use of `withContext(Dispatchers.IO)` dan `withContext(Dispatchers.Main)`
- Add delay 100ms setelah scan untuk ensure cache sync

#### Documentation
- `EXPEDISI_CACHE_UPDATE_AT_IMPLEMENTATION.md`
- `FIX_SISA_UPDATE_ISSUE.md`
- `RELEASE_SCRIPT_IMPROVEMENTS.md`

### 📊 Build Information

- **Version Name**: 5.1.5
- **Version Code**: 20
- **Target SDK**: 35
- **Min SDK**: 30
- **APK Size**: ~11.7 MB

### 🐛 Bug Fixes

- ✅ Cache tidak update saat data di Supabase berubah → FIXED
- ✅ Nilai Sisa tidak update setelah scan → FIXED
- ✅ Thread management issues → FIXED

---

**Full Changelog**: https://github.com/koden01/cekpicklist/compare/v5.1.4...v5.1.5
```

#### Step 5: Upload APK

1. Scroll ke bagian **"Attach binaries"**
2. Klik atau drag-and-drop file:
   ```
   CekPicklist-v5.1.5-release.apk
   ```
3. Tunggu sampai upload selesai (file ~11.7 MB)

#### Step 6: Publish

1. ✅ Check: **Set as the latest release**
2. Klik: **Publish release** (tombol hijau)

#### Step 7: Verifikasi

Buka:
```
https://github.com/koden01/cekpicklist/releases/tag/v5.1.5
```

Pastikan:
- ✅ Release v5.1.5 muncul
- ✅ APK file tersedia untuk download
- ✅ Ditandai sebagai "Latest"

### Metode 2: Menggunakan Script PowerShell (Advanced)

#### Prerequisite:

Install GitHub CLI terlebih dahulu:
```powershell
winget install --id GitHub.cli
```

Atau download dari: https://cli.github.com/

#### Authenticate:

```powershell
gh auth login
```

#### Run Script:

```powershell
.\create_github_release.ps1 -Version "5.1.5" -ReleaseNotes "Fix cache update_at & Sisa value"
```

Script akan otomatis:
- ✅ Create GitHub Release
- ✅ Upload APK file
- ✅ Set sebagai latest release
- ✅ Generate release notes

## 🧪 Testing Auto-Update

### Step 1: Install Aplikasi v5.1.4

Install versi lama di device untuk testing.

### Step 2: Buka Aplikasi

Buka aplikasi → masuk ke halaman utama (HalamanAwalActivity).

### Step 3: Wait for Update Check

Aplikasi otomatis cek update setiap:
- ⏰ Pertama kali buka app
- ⏰ Setiap 1 hari sekali

Atau force check dengan restart aplikasi.

### Step 4: Dialog Update Muncul

Dialog "🔄 Update Tersedia" akan muncul dengan info:
```
Versi terbaru 5.1.5 tersedia!

📱 Fitur baru dan perbaikan bug
🔧 Performa yang lebih baik
🛡️ Keamanan yang ditingkatkan

Apakah Anda ingin mengunduh dan menginstall update?
```

**Buttons:**
- ✅ **📥 Download & Install** → Langsung download APK
- ⏰ **Nanti** → Skip untuk sekarang
- ❌ **Jangan Tampilkan Lagi** → Nonaktifkan update check

### Step 5: Download Progress

Setelah klik "Download & Install":
1. ✅ Notification muncul: "🔄 Download Update"
2. ✅ Progress bar menunjukkan persentase download
3. ✅ "Download selesai!" ketika 100%

### Step 6: Konfirmasi Install

Dialog "✅ Download Selesai" muncul:
```
APK versi 5.1.5 berhasil diunduh!

Apakah Anda ingin menginstall update sekarang?

📱 Aplikasi akan restart setelah instalasi
🔄 Data akan tetap aman
```

**Buttons:**
- ✅ **🚀 Install Sekarang** → Buka installer
- ⏰ **Install Nanti** → Simpan APK untuk nanti
- 🗑️ **Hapus File** → Hapus downloaded APK

### Step 7: Install APK

1. ✅ Android installer terbuka otomatis
2. ✅ Klik "Install" atau "Update"
3. ⚠️ Jika muncul "Install from unknown sources" → aktifkan permission
4. ✅ Aplikasi ter-install
5. ✅ Buka aplikasi → versi 5.1.5 ✅

## 🔧 Troubleshooting

### Problem 1: "Download Gagal"

**Cause:** GitHub Release belum dibuat atau APK belum di-upload

**Solution:**
1. Cek https://github.com/koden01/cekpicklist/releases/tag/v5.1.5
2. Pastikan release ada dan APK tersedia
3. Follow "Create GitHub Release" di atas

### Problem 2: "Install from unknown sources" diminta

**Cause:** Android security setting

**Solution:**
1. Dialog akan muncul dengan tombol "⚙️ Buka Settings"
2. Klik tombol tersebut
3. Enable "Install from this source"
4. Kembali ke installer dan install

### Problem 3: Update check tidak jalan

**Cause:** Update check disabled atau interval belum tercapai

**Solution:**
```kotlin
// Di HalamanAwalActivity, uncomment atau pastikan ada:
updateChecker.checkForUpdates(forceCheck = true)
```

### Problem 4: APK download URL tidak valid

**Cause:** APK filename di GitHub berbeda dengan yang dicari

**Solution:**
Pastikan APK filename di GitHub Release **persis**:
```
CekPicklist-v5.1.5-release.apk
```

Bukan:
- ❌ `cekpicklist-v5.1.5.apk`
- ❌ `app-release.apk`
- ❌ `CekPicklist-v5.1.5.apk`

## 📊 Flow Diagram

```
┌─────────────────────────────────────────┐
│  User membuka aplikasi v5.1.4          │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  UpdateChecker.checkForUpdates()       │
│  Query: GitHub API /releases/latest    │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  Response: tag_name = "v5.1.5"         │
│  Current: 5.1.4 < Latest: 5.1.5        │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  Dialog: "Update Tersedia v5.1.5"     │
│  Button: [Download & Install]          │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  downloadAndInstallUpdate()            │
│  1. Get download URL from assets       │
│  2. Download APK ke external storage   │
│  3. Show progress notification         │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  Dialog: "Download Selesai"            │
│  Button: [Install Sekarang]            │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  installApk()                          │
│  1. Create URI with FileProvider       │
│  2. Start ACTION_VIEW intent           │
│  3. Android installer opens            │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  Android Package Installer            │
│  User klik "Install/Update"            │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  ✅ Aplikasi v5.1.5 ter-install!       │
└─────────────────────────────────────────┘
```

## 🚀 Next Release (v5.1.6+)

Untuk release berikutnya, jalankan:

```powershell
# Build APK dengan versi baru
.\release_working.bat

# Create GitHub Release dengan APK
.\create_github_release.ps1 -Version "5.1.6" -ReleaseNotes "Your release notes here"
```

Atau manual:
1. Tag sudah di-push → `git push origin v5.1.6`
2. Create GitHub Release → Upload APK
3. Auto-update akan langsung kerja! ✅

## 📝 Catatan Penting

1. **APK Filename Harus Konsisten**
   - Format: `CekPicklist-vX.X.X-release.apk`
   - UpdateChecker mencari file dengan pattern ini

2. **Release Harus "Latest"**
   - GitHub API `/releases/latest` mengembalikan release terbaru
   - Pastikan release baru selalu ditandai sebagai "latest"

3. **FileProvider Authority**
   - Sudah dikonfigurasi: `${applicationId}.fileprovider`
   - Jangan diubah kecuali tahu konsekuensinya

4. **Permission**
   - `REQUEST_INSTALL_PACKAGES` sudah ada di manifest
   - User akan diminta enable "Install from unknown sources" saat install pertama kali

## ✅ Checklist

Sebelum release berikutnya:
- [ ] Build APK dengan `.\release_working.bat`
- [ ] Verify APK file exists: `CekPicklist-vX.X.X-release.apk`
- [ ] Tag di-push ke GitHub: `git push origin vX.X.X`
- [ ] **Create GitHub Release dengan APK sebagai asset**
- [ ] Verify release muncul sebagai "Latest"
- [ ] Test auto-update dari versi sebelumnya

---

**Dokumentasi ini dibuat untuk memastikan auto-update bekerja sempurna di setiap release! 🚀**

