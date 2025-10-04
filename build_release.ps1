# Build Release Script untuk Cek Picklist
# Script ini akan build APK release dengan auto versioning

param(
    [string]$VersionType = "patch",  # patch, minor, major
    [switch]$SkipVersion = $false
)

Write-Host "🚀 Cek Picklist - Build Release Script" -ForegroundColor Green
Write-Host "=====================================" -ForegroundColor Green

# Check if we're in git repository
if (-not (Test-Path ".git")) {
    Write-Error "❌ Not in a git repository!"
    exit 1
}

# Auto versioning (unless skipped)
if (-not $SkipVersion) {
    Write-Host "🔄 Running auto versioning..." -ForegroundColor Yellow
    & .\auto_version.ps1 -BuildType $VersionType
    if ($LASTEXITCODE -ne 0) {
        Write-Error "❌ Auto versioning failed!"
        exit 1
    }
}

Write-Host "🔨 Building release APK..." -ForegroundColor Yellow

# Clean previous builds
Write-Host "🧹 Cleaning previous builds..." -ForegroundColor Yellow
& .\gradlew clean --no-daemon

# Build release APK
Write-Host "📦 Building release APK..." -ForegroundColor Yellow
& .\gradlew assembleRelease -x test --no-daemon

if ($LASTEXITCODE -ne 0) {
    Write-Error "❌ Build failed!"
    exit 1
}

Write-Host "✅ Build completed successfully!" -ForegroundColor Green

# Get version info from build.gradle.kts
$gradleContent = Get-Content "app/build.gradle.kts" -Raw
$versionNameMatch = [regex]::Match($gradleContent, 'versionName = "([^"]+)"')
$versionCodeMatch = [regex]::Match($gradleContent, 'versionCode = (\d+)')

$versionName = $versionNameMatch.Groups[1].Value
$versionCode = $versionCodeMatch.Groups[1].Value

# Copy APK with version name
$apkName = "CekPicklist-v$versionName-release.apk"
Copy-Item "app/build/outputs/apk/release/app-release.apk" $apkName

Write-Host "📱 APK Information:" -ForegroundColor Yellow
Write-Host "   File: $apkName" -ForegroundColor White
Write-Host "   Version: $versionName (build $versionCode)" -ForegroundColor White

# Get APK file info
$apkInfo = Get-Item $apkName
$apkSize = [math]::Round($apkInfo.Length / 1MB, 2)
Write-Host "   Size: $apkSize MB" -ForegroundColor White
Write-Host "   Date: $($apkInfo.LastWriteTime)" -ForegroundColor White

# Verify APK signature
Write-Host "🔐 Verifying APK signature..." -ForegroundColor Yellow
$sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
$buildTools = Get-ChildItem "$sdkPath\build-tools" | Sort-Object Name -Descending | Select-Object -First 1
$apksigner = "$($buildTools.FullName)\apksigner"

if (Test-Path $apksigner) {
    & $apksigner verify --print-certs $apkName
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ APK signature verified successfully!" -ForegroundColor Green
    } else {
        Write-Warning "⚠️ APK signature verification failed!"
    }
} else {
    Write-Warning "⚠️ apksigner not found, skipping signature verification"
}

Write-Host "🎉 Release build completed successfully!" -ForegroundColor Green
Write-Host "=====================================" -ForegroundColor Green
Write-Host "📋 Summary:" -ForegroundColor Yellow
Write-Host "   • Version: $versionName (build $versionCode)" -ForegroundColor White
Write-Host "   • APK: $apkName" -ForegroundColor White
Write-Host "   • Size: $apkSize MB" -ForegroundColor White
Write-Host "   • Git tag: v$versionName" -ForegroundColor White
