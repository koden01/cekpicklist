# Final Release Script untuk Cek Picklist
# Script ini akan: Auto versioning, Update README, Build APK, dan Git operations

param(
    [string]$VersionType = "patch",
    [string]$ReleaseNotes = ""
)

Write-Host "🚀 Cek Picklist - Final Release Script" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green

# Step 1: Auto Versioning
Write-Host "📋 Step 1: Auto Versioning..." -ForegroundColor Yellow

$gradleFile = "app/build.gradle.kts"
$content = Get-Content $gradleFile -Raw

# Extract current version using simple string operations
$versionCodeLine = ($content -split "`n" | Where-Object { $_ -match "versionCode" })[0]
$versionNameLine = ($content -split "`n" | Where-Object { $_ -match "versionName" })[0]

$currentVersionCode = [int]($versionCodeLine -replace "[^0-9]", "")
$currentVersionName = ($versionNameLine -split '"')[1]

Write-Host "   Current: $currentVersionName (build $currentVersionCode)" -ForegroundColor White

# Calculate new version
$newVersionCode = $currentVersionCode + 1
$versionParts = $currentVersionName.Split(".")
$major = [int]$versionParts[0]
$minor = [int]$versionParts[1]
$patch = [int]$versionParts[2]

if ($VersionType -eq "major") {
    $major++
    $minor = 0
    $patch = 0
} elseif ($VersionType -eq "minor") {
    $minor++
    $patch = 0
} else {
    $patch++
}

$newVersionName = "$major.$minor.$patch"

Write-Host "   New: $newVersionName (build $newVersionCode)" -ForegroundColor Green

# Update build.gradle.kts
$newContent = $content.Replace("versionCode = $currentVersionCode", "versionCode = $newVersionCode")
$newContent = $newContent.Replace("versionName = `"$currentVersionName`"", "versionName = `"$newVersionName`"")
Set-Content $gradleFile $newContent -Encoding UTF8

Write-Host "   ✅ Updated build.gradle.kts" -ForegroundColor Green

# Step 2: Update README
Write-Host "📝 Step 2: Update README..." -ForegroundColor Yellow

$readmeFile = "README.md"
$readmeContent = Get-Content $readmeFile -Raw

# Update version info in README using simple replacement
$currentDate = Get-Date -Format "yyyy-MM-dd"
$readmeContent = $readmeContent.Replace("**Version**: 1.0.3 (Auto-updating)", "**Version**: $newVersionName (Auto-updating)")
$readmeContent = $readmeContent.Replace("**Last Updated**: 2025-01-10", "**Last Updated**: $currentDate")

Set-Content $readmeFile $readmeContent -Encoding UTF8

Write-Host "   ✅ Updated README.md" -ForegroundColor Green

# Step 3: Build APK
Write-Host "🔨 Step 3: Building APK..." -ForegroundColor Yellow

& .\gradlew assembleRelease -x test --no-daemon

if ($LASTEXITCODE -ne 0) {
    Write-Error "❌ Build failed!"
    exit 1
}

Write-Host "   ✅ APK build completed" -ForegroundColor Green

# Step 4: Copy APK with version name
Write-Host "📱 Step 4: Packaging APK..." -ForegroundColor Yellow

$apkName = "CekPicklist-v$newVersionName-release.apk"
Copy-Item "app/build/outputs/apk/release/app-release.apk" $apkName

# Get APK info
$apkInfo = Get-Item $apkName
$apkSize = [math]::Round($apkInfo.Length / 1MB, 2)

Write-Host "   ✅ APK packaged: $apkName" -ForegroundColor Green

# Step 5: Git Operations
Write-Host "📝 Step 5: Git Operations..." -ForegroundColor Yellow

# Add all changes
git add .
Write-Host "   ✅ Added all changes to git" -ForegroundColor White

# Commit with version info
$commitMessage = "🚀 Release v$newVersionName (build $newVersionCode)"
if ($ReleaseNotes -ne "") {
    $commitMessage += " - $ReleaseNotes"
}
git commit -m $commitMessage
Write-Host "   ✅ Committed: $commitMessage" -ForegroundColor White

# Create git tag
$tagName = "v$newVersionName"
git tag $tagName
Write-Host "   ✅ Created tag: $tagName" -ForegroundColor White

# Step 6: Summary
Write-Host "🎉 Release Summary:" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green
Write-Host "📱 APK Information:" -ForegroundColor Yellow
Write-Host "   • File: $apkName" -ForegroundColor White
Write-Host "   • Version: $newVersionName (build $newVersionCode)" -ForegroundColor White
Write-Host "   • Size: $apkSize MB" -ForegroundColor White
Write-Host "   • Date: $($apkInfo.LastWriteTime)" -ForegroundColor White
Write-Host "" -ForegroundColor White
Write-Host "📝 Git Information:" -ForegroundColor Yellow
Write-Host "   • Commit: $commitMessage" -ForegroundColor White
Write-Host "   • Tag: $tagName" -ForegroundColor White
Write-Host "   • README: Updated with version $newVersionName" -ForegroundColor White
Write-Host "" -ForegroundColor White
Write-Host "✅ Complete release workflow finished successfully!" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green
