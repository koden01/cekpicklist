# Simple Auto Versioning Script untuk Cek Picklist

param(
    [string]$BuildType = "patch"
)

Write-Host "🚀 Simple Auto Versioning - Cek Picklist" -ForegroundColor Green

# Read current version from build.gradle.kts
$gradleFile = "app/build.gradle.kts"
$content = Get-Content $gradleFile -Raw

# Extract version info
$versionCodeMatch = [regex]::Match($content, 'versionCode = (\d+)')
$versionNameMatch = [regex]::Match($content, 'versionName = "([^"]+)"')

$currentVersionCode = [int]$versionCodeMatch.Groups[1].Value
$currentVersionName = $versionNameMatch.Groups[1].Value

Write-Host "Current: $currentVersionName (build $currentVersionCode)" -ForegroundColor Yellow

# Calculate new version
$newVersionCode = $currentVersionCode + 1
$versionParts = $currentVersionName.Split('.')
$major = [int]$versionParts[0]
$minor = [int]$versionParts[1]
$patch = [int]$versionParts[2]

if ($BuildType -eq "major") {
    $major++
    $minor = 0
    $patch = 0
} elseif ($BuildType -eq "minor") {
    $minor++
    $patch = 0
} else {
    $patch++
}

$newVersionName = "$major.$minor.$patch"

Write-Host "New: $newVersionName (build $newVersionCode)" -ForegroundColor Green

# Update file using simple string replacement
$newContent = $content.Replace("versionCode = $currentVersionCode", "versionCode = $newVersionCode")
$newContent = $newContent.Replace("versionName = `"$currentVersionName`"", "versionName = `"$newVersionName`"")

Set-Content $gradleFile $newContent -Encoding UTF8

Write-Host "✅ Updated build.gradle.kts" -ForegroundColor Green

# Git operations
git add $gradleFile
$commitMessage = "🚀 Auto version bump: v$newVersionName (build $newVersionCode)"
git commit -m $commitMessage
git tag "v$newVersionName"

Write-Host "✅ Git operations completed" -ForegroundColor Green
Write-Host "🎉 Version updated to $newVersionName!" -ForegroundColor Green
