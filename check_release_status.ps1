# Check Release Status
Write-Host "`n🔍 Checking Release Status" -ForegroundColor Cyan
Write-Host "========================" -ForegroundColor Cyan

# Check version in build.gradle.kts
Write-Host "`n📱 Current Version in build.gradle.kts:" -ForegroundColor Yellow
$versionLine = Get-Content "app\build.gradle.kts" | Select-String "versionName"
Write-Host "  $versionLine" -ForegroundColor White

# Check local tags
Write-Host "`n🏷️  Local Tags (v5.1.x):" -ForegroundColor Yellow
git tag -l "v5.1.*" | ForEach-Object { Write-Host "  $_" -ForegroundColor White }

# Check remote tags
Write-Host "`n🌐 Remote Tags (v5.1.x on GitHub):" -ForegroundColor Yellow
git ls-remote --tags origin 2>&1 | Select-String "v5.1" | ForEach-Object {
    if ($_ -match "refs/tags/(v5\.1\.\d+)") {
        Write-Host "  $($matches[1])" -ForegroundColor White
    }
}

# Check APK files
Write-Host "`n📦 APK Files:" -ForegroundColor Yellow
Get-ChildItem -Filter "CekPicklist-v5.1.*-release.apk" | ForEach-Object {
    $sizeMB = [math]::Round($_.Length/1MB, 2)
    Write-Host "  $($_.Name) - $sizeMB MB - $($_.LastWriteTime)" -ForegroundColor White
}

# Check GitHub Releases
Write-Host "`n🚀 GitHub Releases:" -ForegroundColor Yellow
$ghAvailable = Get-Command gh -ErrorAction SilentlyContinue
if ($ghAvailable) {
    gh release list --limit 5 2>&1 | Select-Object -First 5
} else {
    Write-Host "  ⚠️ GitHub CLI not available in PATH" -ForegroundColor Yellow
    Write-Host "  Refresh PATH with: refreshenv or restart terminal" -ForegroundColor Gray
}

# Summary
Write-Host "`n📊 Summary:" -ForegroundColor Cyan
Write-Host "============" -ForegroundColor Cyan

$latestApk = Get-ChildItem -Filter "CekPicklist-v5.1.*-release.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($latestApk) {
    $apkVersion = $latestApk.Name -replace "CekPicklist-v(\d+\.\d+\.\d+)-release\.apk", '$1'
    Write-Host "Latest APK: v$apkVersion" -ForegroundColor Green
    
    # Check if tag exists
    $tagExists = git tag -l "v$apkVersion"
    if ($tagExists) {
        Write-Host "Tag v$apkVersion: ✅ Created locally" -ForegroundColor Green
        
        # Check if pushed
        $remoteTag = git ls-remote --tags origin "refs/tags/v$apkVersion" 2>&1
        if ($remoteTag) {
            Write-Host "Tag v$apkVersion: ✅ Pushed to GitHub" -ForegroundColor Green
        } else {
            Write-Host "Tag v$apkVersion: ⚠️ NOT pushed to GitHub" -ForegroundColor Yellow
            Write-Host "  Fix: git push origin v$apkVersion" -ForegroundColor Gray
        }
    } else {
        Write-Host "Tag v$apkVersion: ❌ NOT created" -ForegroundColor Red
        Write-Host "  Fix: git tag -a v$apkVersion -m 'Release v$apkVersion'" -ForegroundColor Gray
    }
}

Write-Host ""

