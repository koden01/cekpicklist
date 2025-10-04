# Simple Build Script untuk Cek Picklist

Write-Host "🔨 Building Cek Picklist APK..." -ForegroundColor Green

# Build release APK
& .\gradlew assembleRelease -x test --no-daemon

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Build completed successfully!" -ForegroundColor Green
    
    # Get version info
    $gradleContent = Get-Content "app/build.gradle.kts" -Raw
    $versionNameMatch = [regex]::Match($gradleContent, 'versionName = "([^"]+)"')
    $versionCodeMatch = [regex]::Match($gradleContent, 'versionCode = (\d+)')
    
    $versionName = $versionNameMatch.Groups[1].Value
    $versionCode = $versionCodeMatch.Groups[1].Value
    
    # Copy APK with version name
    $apkName = "CekPicklist-v$versionName-release.apk"
    Copy-Item "app/build/outputs/apk/release/app-release.apk" $apkName
    
    # Get APK info
    $apkInfo = Get-Item $apkName
    $apkSize = [math]::Round($apkInfo.Length / 1MB, 2)
    
    Write-Host "📱 APK Information:" -ForegroundColor Yellow
    Write-Host "   File: $apkName" -ForegroundColor White
    Write-Host "   Version: $versionName (build $versionCode)" -ForegroundColor White
    Write-Host "   Size: $apkSize MB" -ForegroundColor White
    Write-Host "   Date: $($apkInfo.LastWriteTime)" -ForegroundColor White
    
    Write-Host "🎉 APK ready for distribution!" -ForegroundColor Green
} else {
    Write-Error "❌ Build failed!"
    exit 1
}
