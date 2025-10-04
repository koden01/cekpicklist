@echo off
REM Complete Release Script untuk Cek Picklist
REM Script ini akan: Auto versioning, Update README, Build APK, dan Git operations

echo 🚀 Cek Picklist - Complete Release
echo ==================================

REM Get version type from user
echo.
echo Select version type:
echo 1. Patch (1.0.3 → 1.0.4)
echo 2. Minor (1.0.3 → 1.1.0)
echo 3. Major (1.0.3 → 2.0.0)
echo.
set /p choice="Enter choice (1-3): "

if "%choice%"=="1" set version_type=patch
if "%choice%"=="2" set version_type=minor
if "%choice%"=="3" set version_type=major
if "%choice%"=="" set version_type=patch

echo.
echo 📝 Enter release notes (optional, press Enter to skip):
set /p release_notes="Release notes: "

echo.
echo 🚀 Starting complete release process...
echo.

REM Step 1: Auto Versioning
echo 📋 Step 1: Auto Versioning...
powershell.exe -ExecutionPolicy Bypass -Command "& { $content = Get-Content 'app/build.gradle.kts' -Raw; $versionCodeLine = ($content -split \"`n\" | Where-Object { $_ -match 'versionCode' })[0]; $versionNameLine = ($content -split \"`n\" | Where-Object { $_ -match 'versionName' })[0]; $currentVersionCode = [int]($versionCodeLine -replace '[^0-9]', ''); $currentVersionName = ($versionNameLine -split '\"')[1]; Write-Host \"Current: $currentVersionName (build $currentVersionCode)\" -ForegroundColor Yellow; $newVersionCode = $currentVersionCode + 1; $versionParts = $currentVersionName.Split('.'); $major = [int]$versionParts[0]; $minor = [int]$versionParts[1]; $patch = [int]$versionParts[2]; if ('%version_type%' -eq 'major') { $major++; $minor = 0; $patch = 0 } elseif ('%version_type%' -eq 'minor') { $minor++; $patch = 0 } else { $patch++ }; $newVersionName = \"$major.$minor.$patch\"; Write-Host \"New: $newVersionName (build $newVersionCode)\" -ForegroundColor Green; $newContent = $content.Replace(\"versionCode = $currentVersionCode\", \"versionCode = $newVersionCode\"); $newContent = $newContent.Replace(\"versionName = `\"$currentVersionName`\"\", \"versionName = `\"$newVersionName`\"\"); Set-Content 'app/build.gradle.kts' $newContent -Encoding UTF8; Write-Host '✅ Updated build.gradle.kts' -ForegroundColor Green }"

if %errorlevel% neq 0 (
    echo ❌ Versioning failed!
    pause
    exit /b 1
)

REM Step 2: Update README
echo 📝 Step 2: Update README...
powershell.exe -ExecutionPolicy Bypass -Command "& { $gradleContent = Get-Content 'app/build.gradle.kts' -Raw; $versionNameLine = ($gradleContent -split \"`n\" | Where-Object { $_ -match 'versionName' })[0]; $newVersionName = ($versionNameLine -split '\"')[1]; $readmeContent = Get-Content 'README.md' -Raw; $currentDate = Get-Date -Format 'yyyy-MM-dd'; $readmeContent = $readmeContent.Replace('**Version**: 2.0.1 (Auto-updating)', \"**Version**: $newVersionName (Auto-updating)\"); $readmeContent = $readmeContent.Replace('**Last Updated**: 2025-01-10', \"**Last Updated**: $currentDate\"); Set-Content 'README.md' $readmeContent -Encoding UTF8; Write-Host \"✅ Updated README.md with version $newVersionName\" -ForegroundColor Green }"

REM Step 3: Build APK
echo 🔨 Step 3: Building APK...
.\gradlew assembleRelease -x test --no-daemon

if %errorlevel% neq 0 (
    echo ❌ Build failed!
    pause
    exit /b 1
)

echo ✅ APK build completed

REM Step 4: Copy APK
echo 📱 Step 4: Packaging APK...
powershell.exe -ExecutionPolicy Bypass -Command "& { $gradleContent = Get-Content 'app/build.gradle.kts' -Raw; $versionNameLine = ($gradleContent -split \"`n\" | Where-Object { $_ -match 'versionName' })[0]; $versionCodeLine = ($gradleContent -split \"`n\" | Where-Object { $_ -match 'versionCode' })[0]; $newVersionName = ($versionNameLine -split '\"')[1]; $newVersionCode = [int]($versionCodeLine -replace '[^0-9]', ''); $apkName = \"CekPicklist-v$newVersionName-release.apk\"; Copy-Item 'app\build\outputs\apk\release\app-release.apk' $apkName; Write-Host \"✅ APK packaged: $apkName\" -ForegroundColor Green; $apkName }" > temp_apk_name.txt
set /p apk_name=<temp_apk_name.txt
del temp_apk_name.txt

REM Step 5: Git Operations
echo 📝 Step 5: Git Operations...
powershell.exe -ExecutionPolicy Bypass -Command "& { $gradleContent = Get-Content 'app/build.gradle.kts' -Raw; $versionNameLine = ($gradleContent -split \"`n\" | Where-Object { $_ -match 'versionName' })[0]; $versionCodeLine = ($gradleContent -split \"`n\" | Where-Object { $_ -match 'versionCode' })[0]; $newVersionName = ($versionNameLine -split '\"')[1]; $newVersionCode = [int]($versionCodeLine -replace '[^0-9]', ''); git add .; git commit -m \"🚀 Release v$newVersionName (build $newVersionCode) - %release_notes%\"; git tag \"v$newVersionName\"; git push origin master; git push origin --tags; Write-Host \"✅ Git operations completed (committed, tagged, and pushed)\" -ForegroundColor Green }"

echo ✅ Git operations completed

REM Step 6: Summary
echo.
echo 🎉 Release Summary:
echo ==================================
powershell.exe -ExecutionPolicy Bypass -Command "& { $gradleContent = Get-Content 'app/build.gradle.kts' -Raw; $versionNameLine = ($gradleContent -split \"`n\" | Where-Object { $_ -match 'versionName' })[0]; $versionCodeLine = ($gradleContent -split \"`n\" | Where-Object { $_ -match 'versionCode' })[0]; $newVersionName = ($versionNameLine -split '\"')[1]; $newVersionCode = [int]($versionCodeLine -replace '[^0-9]', ''); $apkName = \"CekPicklist-v$newVersionName-release.apk\"; Write-Host \"📱 APK Information:\" -ForegroundColor Yellow; Write-Host \"   • File: $apkName\" -ForegroundColor White; Write-Host \"   • Version: $newVersionName (build $newVersionCode)\" -ForegroundColor White; Write-Host \"   • Date: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')\" -ForegroundColor White; Write-Host \"\" -ForegroundColor White; Write-Host \"📝 Git Information:\" -ForegroundColor Yellow; Write-Host \"   • Commit: 🚀 Release v$newVersionName (build $newVersionCode)\" -ForegroundColor White; Write-Host \"   • Tag: v$newVersionName\" -ForegroundColor White; Write-Host \"   • Push: Pushed to origin/master and tags\" -ForegroundColor White; Write-Host \"   • README: Updated with version $newVersionName\" -ForegroundColor White; Write-Host \"\" -ForegroundColor White; Write-Host \"✅ Complete release workflow finished successfully!\" -ForegroundColor Green; Write-Host \"==================================\" -ForegroundColor Green }"

pause
