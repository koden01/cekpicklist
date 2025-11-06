# GitHub Release Creator Script
# Creates GitHub release dan upload APK

param(
    [string]$Version = "5.1.5",
    [string]$ReleaseNotes = "Fix cache update_at for tbl_expedisi & fix Sisa value not updating"
)

Write-Host "🚀 GitHub Release Creator" -ForegroundColor Cyan
Write-Host "=========================" -ForegroundColor Cyan
Write-Host ""

# Check if gh CLI is installed
Write-Host "Checking GitHub CLI..." -ForegroundColor Yellow
$ghInstalled = Get-Command gh -ErrorAction SilentlyContinue
if (-not $ghInstalled) {
    Write-Host "❌ GitHub CLI (gh) not installed!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install GitHub CLI:" -ForegroundColor Yellow
    Write-Host "  winget install --id GitHub.cli" -ForegroundColor White
    Write-Host "  or download from: https://cli.github.com/" -ForegroundColor White
    Write-Host ""
    exit 1
}

Write-Host "✅ GitHub CLI installed" -ForegroundColor Green

# Check authentication
Write-Host "Checking GitHub authentication..." -ForegroundColor Yellow
$authStatus = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Not authenticated with GitHub!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please authenticate:" -ForegroundColor Yellow
    Write-Host "  gh auth login" -ForegroundColor White
    Write-Host ""
    exit 1
}

Write-Host "✅ Authenticated with GitHub" -ForegroundColor Green
Write-Host ""

# Check if APK file exists
$apkFile = "CekPicklist-v$Version-release.apk"
if (-not (Test-Path $apkFile)) {
    Write-Host "❌ APK file not found: $apkFile" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please build APK first:" -ForegroundColor Yellow
    Write-Host "  .\gradlew.bat assembleRelease" -ForegroundColor White
    Write-Host ""
    exit 1
}

$apkSize = (Get-Item $apkFile).Length / 1MB
Write-Host "✅ APK file found: $apkFile ($([math]::Round($apkSize, 2)) MB)" -ForegroundColor Green
Write-Host ""

# Check if release already exists
Write-Host "Checking if release v$Version already exists..." -ForegroundColor Yellow
$releaseExists = gh release view "v$Version" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "⚠️ Release v$Version already exists!" -ForegroundColor Yellow
    Write-Host ""
    $overwrite = Read-Host "Do you want to delete and recreate? (y/N)"
    if ($overwrite -eq 'y' -or $overwrite -eq 'Y') {
        Write-Host "Deleting existing release..." -ForegroundColor Yellow
        gh release delete "v$Version" --yes
        Write-Host "✅ Deleted existing release" -ForegroundColor Green
    } else {
        Write-Host "Updating existing release..." -ForegroundColor Yellow
        gh release upload "v$Version" $apkFile --clobber
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ APK uploaded to existing release" -ForegroundColor Green
            Write-Host ""
            Write-Host "🔗 Release URL: https://github.com/koden01/cekpicklist/releases/tag/v$Version" -ForegroundColor Cyan
        } else {
            Write-Host "❌ Failed to upload APK" -ForegroundColor Red
        }
        exit $LASTEXITCODE
    }
}

# Create release notes
$notes = @"
## 🎉 Release v$Version

### ✨ What's New

$ReleaseNotes

### 📱 Download & Install

1. Download APK file di bawah
2. Buka file APK
3. Aktifkan "Install from unknown sources" jika diminta
4. Install aplikasi

### 🔄 Auto Update

Aplikasi akan otomatis detect update dan menawarkan download & install langsung dari dalam aplikasi.

### 📊 Build Information

- **Version Code**: $(Get-Content version.txt -ErrorAction SilentlyContinue)
- **Build Date**: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
- **APK Size**: $([math]::Round($apkSize, 2)) MB

### 🐛 Bug Fixes & Improvements

- Fix cache invalidation dengan kolom update_at
- Fix nilai Sisa tidak update setelah scan
- Improve thread management untuk UI updates
- Add 100ms delay untuk sinkronisasi cache

---

**Full Changelog**: https://github.com/koden01/cekpicklist/compare/v5.1.4...v$Version
"@

# Create release
Write-Host "Creating GitHub release v$Version..." -ForegroundColor Yellow
Write-Host ""

gh release create "v$Version" $apkFile `
    --title "v$Version" `
    --notes $notes `
    --latest

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "✅ GitHub Release Created Successfully!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "📱 Release Information:" -ForegroundColor Cyan
    Write-Host "  • Version: v$Version" -ForegroundColor White
    Write-Host "  • APK: $apkFile" -ForegroundColor White
    Write-Host "  • Size: $([math]::Round($apkSize, 2)) MB" -ForegroundColor White
    Write-Host ""
    Write-Host "🔗 View Release:" -ForegroundColor Cyan
    Write-Host "  https://github.com/koden01/cekpicklist/releases/tag/v$Version" -ForegroundColor White
    Write-Host ""
    Write-Host "📥 Download URL:" -ForegroundColor Cyan
    Write-Host "  https://github.com/koden01/cekpicklist/releases/download/v$Version/$apkFile" -ForegroundColor White
    Write-Host ""
    Write-Host "🔄 Auto-Update in app will now work!" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "❌ Failed to create GitHub release" -ForegroundColor Red
    Write-Host ""
    Write-Host "💡 Try manual method:" -ForegroundColor Yellow
    Write-Host "  1. Go to: https://github.com/koden01/cekpicklist/releases/new" -ForegroundColor White
    Write-Host "  2. Select tag: v$Version" -ForegroundColor White
    Write-Host "  3. Upload: $apkFile" -ForegroundColor White
    Write-Host "  4. Publish release" -ForegroundColor White
    Write-Host ""
    exit 1
}

