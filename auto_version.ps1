# Auto Versioning Script untuk Cek Picklist
# Script ini akan otomatis increment version code dan version name

param(
    [string]$BuildType = "patch",  # patch, minor, major
    [switch]$BuildApk = $false
)

Write-Host "🚀 Auto Versioning Script - Cek Picklist" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green

# Path ke build.gradle.kts
$gradleFile = "app/build.gradle.kts"

# Baca file build.gradle.kts
$content = Get-Content $gradleFile -Raw

# Extract current version
$versionCodeMatch = [regex]::Match($content, 'versionCode = (\d+)')
$versionNameMatch = [regex]::Match($content, 'versionName = "([^"]+)"')

if (-not $versionCodeMatch.Success -or -not $versionNameMatch.Success) {
    Write-Error "❌ Tidak dapat menemukan versionCode atau versionName di build.gradle.kts"
    exit 1
}

$currentVersionCode = [int]$versionCodeMatch.Groups[1].Value
$currentVersionName = $versionNameMatch.Groups[1].Value

Write-Host "📋 Current Version:" -ForegroundColor Yellow
Write-Host "   Version Code: $currentVersionCode" -ForegroundColor White
Write-Host "   Version Name: $currentVersionName" -ForegroundColor White

# Increment version
$newVersionCode = $currentVersionCode + 1

# Parse version name (format: x.y.z)
$versionParts = $currentVersionName.Split('.')
$major = [int]$versionParts[0]
$minor = [int]$versionParts[1]
$patch = [int]$versionParts[2]

switch ($BuildType.ToLower()) {
    "major" {
        $major++
        $minor = 0
        $patch = 0
    }
    "minor" {
        $minor++
        $patch = 0
    }
    "patch" {
        $patch++
    }
    default {
        $patch++
    }
}

$newVersionName = "$major.$minor.$patch"

Write-Host "🔄 New Version:" -ForegroundColor Yellow
Write-Host "   Version Code: $newVersionCode" -ForegroundColor White
Write-Host "   Version Name: $newVersionName" -ForegroundColor White

# Update build.gradle.kts
$newContent = $content -replace 'versionCode = \d+', "versionCode = $newVersionCode"
$newContent = $newContent -replace 'versionName = "[^"]+"', "versionName = `"$newVersionName`""

Set-Content $gradleFile $newContent -Encoding UTF8

Write-Host "✅ build.gradle.kts updated successfully!" -ForegroundColor Green

# Git operations
Write-Host "📝 Git Operations:" -ForegroundColor Yellow

# Add changes to git
git add $gradleFile
Write-Host "   ✅ Added build.gradle.kts to git" -ForegroundColor White

# Commit with version info
$commitMessage = "🚀 Auto version bump: v$newVersionName (build $newVersionCode)"
git commit -m $commitMessage
Write-Host "   ✅ Committed: $commitMessage" -ForegroundColor White

# Create git tag
$tagName = "v$newVersionName"
git tag $tagName
Write-Host "   ✅ Created tag: $tagName" -ForegroundColor White

# Build APK if requested
if ($BuildApk) {
    Write-Host "🔨 Building APK..." -ForegroundColor Yellow
    
    # Build release APK
    & .\gradlew assembleRelease -x test --no-daemon
    
    if ($LASTEXITCODE -eq 0) {
        # Copy APK with version name
        $apkName = "CekPicklist-v$newVersionName-release.apk"
        Copy-Item "app/build/outputs/apk/release/app-release.apk" $apkName
        
        Write-Host "✅ APK built successfully: $apkName" -ForegroundColor Green
        
        # Get APK info
        $apkInfo = Get-Item $apkName
        $apkSize = [math]::Round($apkInfo.Length / 1MB, 2)
        Write-Host "📱 APK Info:" -ForegroundColor Yellow
        Write-Host "   File: $apkName" -ForegroundColor White
        Write-Host "   Size: $apkSize MB" -ForegroundColor White
        Write-Host "   Date: $($apkInfo.LastWriteTime)" -ForegroundColor White
    } else {
        Write-Error "❌ Build failed!"
        exit 1
    }
}

Write-Host "🎉 Auto versioning completed successfully!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
