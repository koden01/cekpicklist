# Full Automation Release Guide - Cek Picklist

## 🎯 Tujuan

Membuat proses release **FULL OTOMATIS** dari versioning sampai GitHub Release dengan APK, sehingga auto-update di aplikasi langsung bisa bekerja tanpa manual steps.

## ✅ Fitur Full Automation

### Apa yang Otomatis:
1. ✅ **Auto Versioning** - Increment version (patch/minor/major)
2. ✅ **Update README** - Version & date otomatis di-update
3. ✅ **Build APK** - Gradle assembleRelease dengan logging
4. ✅ **Package APK** - Copy ke root dengan nama yang benar
5. ✅ **Git Operations** - Add, commit, tag
6. ✅ **Push to GitHub** - Branch + tags
7. ✅ **GitHub Release** - Create release + upload APK (BARU!)
8. ✅ **Summary** - Comprehensive release summary

### Hasil:
🚀 **One Command = Complete Release** (Build + Git + GitHub + Auto-Update Ready!)

## 🔧 Setup Sekali Saja (One-Time Setup)

### Step 1: Install GitHub CLI

Jalankan script installer:

```batch
.\install_github_cli.bat
```

Script akan:
- ✅ Cek apakah `gh` sudah terinstall
- ✅ Install menggunakan `winget` jika belum ada
- ✅ Panduan authenticate dengan GitHub
- ✅ Verifikasi setup berhasil

**Atau manual**:

```powershell
# Install dengan winget
winget install --id GitHub.cli

# Atau download manual
# https://cli.github.com/
```

### Step 2: Authenticate dengan GitHub

```batch
gh auth login
```

**Pilih options**:
1. What account: `GitHub.com`
2. Preferred protocol: `HTTPS`
3. Authenticate: `Login with a web browser` (RECOMMENDED)
4. Copy one-time code yang muncul
5. Tekan Enter → browser akan terbuka
6. Paste code di browser
7. Authorize GitHub CLI
8. ✅ Done!

**Verify**:
```batch
gh auth status
```

Harus muncul: `✓ Logged in to github.com`

### Step 3: Test GitHub CLI

```batch
gh release list --limit 3
```

Harus menampilkan list releases dari repo.

## 🚀 Cara Menggunakan (Full Automation)

### One Command Release:

```batch
.\release_working.bat
```

**Interactive Prompts**:

1. **Version Type**:
   ```
   Select version type:
   1. Patch (5.1.5 → 5.1.6)
   2. Minor (5.1.5 → 5.2.0)
   3. Major (5.1.5 → 6.0.0)
   
   Enter choice (1-3): 1
   ```

2. **Release Notes** (optional):
   ```
   Enter release notes (optional, press Enter to skip):
   Release notes: Fix cache sync issues
   ```

3. **Git Authentication** (jika belum):
   ```
   Choice (S/F/D/B for login help, Enter to continue): [Enter]
   ```

4. **GitHub Release** (jika release sudah ada):
   ```
   Delete and recreate release? (y/N): n
   ```

### Otomatis Berjalan:

```
✅ Step 1: Auto Versioning...
   Current: 5.1.5 (build 20)
   New: 5.1.6 (build 21)

✅ Step 2: Update README...
   Version: 5.1.6
   Date: 2025-11-06

✅ Step 3: Building APK...
   🧹 Cleaning...
   🔨 Building... (2-5 minutes)
   BUILD SUCCESSFUL

✅ Step 4: Packaging APK...
   CekPicklist-v5.1.6-release.apk

✅ Step 5: Git Operations...
   📝 git add -A
   📝 git commit
   📝 git tag v5.1.6

✅ Step 6: Pushing to remote...
   📤 git push origin restore/9e95321
   📤 git push origin --tags

✅ Step 7: Create GitHub Release...
   ✅ GitHub CLI found
   ✅ Authenticated
   📦 Creating release...
   📤 Uploading APK...
   ✅ Release created!
   
   🔗 https://github.com/koden01/cekpicklist/releases/tag/v5.1.6

✅ Step 8: Summary
   🎉 RELEASE COMPLETED!
   
   📱 APK: CekPicklist-v5.1.6-release.apk
   📦 GitHub Release: Created ✅
   🔄 Auto-update: Ready ✅
```

### Total Time:
- ⏱️ **5-10 menit** (tergantung kecepatan build & upload)
- 🎯 **0 manual steps** setelah konfirmasi awal!

## 📊 Workflow Comparison

### Before (Manual):

```
1. Edit build.gradle.kts (manual)          ⏱️ 2 min
2. Edit README.md (manual)                 ⏱️ 1 min
3. gradlew assembleRelease                 ⏱️ 5 min
4. Copy APK to root (manual)               ⏱️ 1 min
5. git add, commit, tag (manual)           ⏱️ 2 min
6. git push (manual)                       ⏱️ 1 min
7. Go to GitHub web (manual)               ⏱️ 1 min
8. Create release (manual)                 ⏱️ 3 min
9. Upload APK (manual)                     ⏱️ 2 min
10. Publish release (manual)               ⏱️ 1 min

Total: ~19 minutes, 10 manual steps ❌
```

### After (Full Automation):

```
1. Run: .\release_working.bat              ⏱️ 1 min setup
   → Auto versioning                       ⏱️ 5 sec
   → Auto README update                    ⏱️ 5 sec
   → Auto build APK                        ⏱️ 5 min
   → Auto git operations                   ⏱️ 30 sec
   → Auto push                             ⏱️ 30 sec
   → Auto GitHub Release                   ⏱️ 2 min
   → Summary                               ⏱️ instant

Total: ~9 minutes, 1 command ✅
```

**Saving**: 10 minutes + 0 manual errors! 🎉

## 🧪 Testing Auto-Update End-to-End

### Scenario: User dengan app v5.1.5 mendapat update v5.1.6

1. **Release v5.1.6**:
   ```batch
   .\release_working.bat
   # Pilih: 1 (patch)
   # Notes: Bug fixes
   # [Wait 9 minutes]
   # ✅ Done! GitHub Release created with APK
   ```

2. **User Opens App v5.1.5**:
   ```
   App starts → UpdateChecker runs
   → Query GitHub API: /releases/latest
   → Response: v5.1.6 available
   → Dialog muncul: "Update Tersedia v5.1.6"
   ```

3. **User Clicks "Download & Install"**:
   ```
   → Download APK from GitHub
   → Progress notification: 0% → 100%
   → Dialog: "Download Selesai"
   ```

4. **User Clicks "Install Sekarang"**:
   ```
   → Android installer opens
   → User clicks "Install"
   → App updated to v5.1.6 ✅
   ```

5. **No GitHub Website Visit Needed!** ✅

## 📝 Command Cheat Sheet

### First-Time Setup:
```batch
# Install GitHub CLI
.\install_github_cli.bat

# Verify installation
gh --version
gh auth status
```

### Every Release:
```batch
# One command for everything!
.\release_working.bat

# Choose version type (patch/minor/major)
# Enter release notes (optional)
# Wait ~9 minutes
# ✅ Done!
```

### Manual Override (if needed):
```batch
# If gh CLI not available, release still works but:
# - GitHub Release creation is skipped
# - You'll need to create release manually
# - Everything else still automated
```

## 🎯 Benefits

### For Developers:
- ✅ **Save 10+ minutes per release**
- ✅ **Zero manual errors** (versioning, naming, etc)
- ✅ **Consistent release process**
- ✅ **Better documentation** (auto-generated notes)

### For Users:
- ✅ **Easy updates** - Download & install from within app
- ✅ **No GitHub knowledge needed**
- ✅ **Progress tracking** - Notification shows download progress
- ✅ **Safe updates** - Verified APK from official source

## 🔍 Troubleshooting

### Problem: "GitHub CLI not found"

**Solution**:
```batch
# Run installer
.\install_github_cli.bat

# Or manual
winget install --id GitHub.cli
```

### Problem: "Not authenticated with GitHub"

**Solution**:
```batch
gh auth login
# Follow prompts - choose browser login
```

### Problem: "Release already exists"

**Options**:
- Delete & recreate: `Y` (overwrite dengan data baru)
- Skip: `N` (keep existing release)

### Problem: "Failed to create GitHub Release"

**Check**:
1. Internet connection
2. GitHub authentication: `gh auth status`
3. Repository access permissions
4. APK file exists

**Fallback**:
Script tetap selesai sampai git push. Buat release manual:
```
https://github.com/koden01/cekpicklist/releases/new
```

## 📋 File Changes Summary

### Modified:
- ✅ `release_working.bat` - Added Step 7 for GitHub Release

### Created:
- ✅ `install_github_cli.bat` - Easy gh CLI setup
- ✅ `create_github_release.ps1` - Standalone release creator
- ✅ `FULL_AUTOMATION_RELEASE_GUIDE.md` - This guide
- ✅ `SETUP_AUTO_UPDATE_GUIDE.md` - Auto-update documentation

## 🎉 Conclusion

Dengan setup ini, proses release Cek Picklist sekarang **FULL OTOMATIS**:

```
One Command → Complete Release → Auto-Update Ready!
```

**Total effort**: 
- ⏱️ Setup (one-time): ~5 minutes
- ⏱️ Per release: ~1 minute interaction + 9 minutes waiting
- 💰 ROI: Massive time savings + zero errors!

---

**Happy Releasing! 🚀**

