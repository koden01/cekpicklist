@echo off
REM Working Release Script untuk Cek Picklist
REM Script ini akan: Auto versioning, Update README, Build APK, dan Git operations

echo 🚀 Cek Picklist - Working Release Script
echo =======================================

REM Get version type from user
echo.
echo Select version type:
echo 1. Patch (4.0.0 → 4.0.1)
echo 2. Minor (4.0.0 → 4.1.0)
echo 3. Major (4.0.0 → 5.0.0)
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
echo 🚀 Starting working release process...
echo.

REM Step 1: Auto Versioning
echo 📋 Step 1: Auto Versioning...
for /f "tokens=*" %%i in ('powershell.exe -ExecutionPolicy Bypass -Command "& { $content = Get-Content 'app/build.gradle.kts' -Raw; $versionCodeLine = ($content -split \"`n\" | Where-Object { $_ -match 'versionCode' })[0]; $versionNameLine = ($content -split \"`n\" | Where-Object { $_ -match 'versionName' })[0]; $currentVersionCode = [int]($versionCodeLine -replace '[^0-9]', ''); $currentVersionName = ($versionNameLine -split '\"')[1]; Write-Host \"Current: $currentVersionName (build $currentVersionCode)\" -ForegroundColor Yellow; $newVersionCode = $currentVersionCode + 1; $versionParts = $currentVersionName.Split('.'); $major = [int]$versionParts[0]; $minor = [int]$versionParts[1]; $patch = [int]$versionParts[2]; if ('%version_type%' -eq 'major') { $major++; $minor = 0; $patch = 0 } elseif ('%version_type%' -eq 'minor') { $minor++; $patch = 0 } else { $patch++ }; $newVersionName = \"$major.$minor.$patch\"; Write-Host \"New: $newVersionName (build $newVersionCode)\" -ForegroundColor Green; $newContent = $content.Replace(\"versionCode = $currentVersionCode\", \"versionCode = $newVersionCode\"); $newContent = $newContent.Replace(\"versionName = `\"$currentVersionName`\"\", \"versionName = `\"$newVersionName`\"\"); Set-Content 'app/build.gradle.kts' $newContent -Encoding UTF8; Write-Host '✅ Updated build.gradle.kts' -ForegroundColor Green; $newVersionName }"') do set new_version=%%i

if %errorlevel% neq 0 (
    echo ❌ Versioning failed!
    pause
    exit /b 1
)

REM Step 2: Update README
echo 📝 Step 2: Update README...
powershell.exe -ExecutionPolicy Bypass -Command "& { $readmeContent = Get-Content 'README.md' -Raw; $currentDate = Get-Date -Format 'yyyy-MM-dd'; $readmeContent = $readmeContent.Replace('**Version**: 4.0.0 (Auto-updating)', \"**Version**: %new_version% (Auto-updating)\"); $readmeContent = $readmeContent.Replace('**Last Updated**: 2025-01-10', \"**Last Updated**: $currentDate\"); Set-Content 'README.md' $readmeContent -Encoding UTF8; Write-Host '✅ Updated README.md' -ForegroundColor Green }"

if %errorlevel% neq 0 (
    echo ❌ README update failed!
    pause
    exit /b 1
)

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
copy "app\build\outputs\apk\release\app-release.apk" "CekPicklist-v%new_version%-release.apk"

if %errorlevel% neq 0 (
    echo ❌ APK packaging failed!
    pause
    exit /b 1
)

echo ✅ APK packaged: CekPicklist-v%new_version%-release.apk

REM Step 5: Git Operations
echo 📝 Step 5: Git Operations...
git add .

if %errorlevel% neq 0 (
    echo ❌ Git add failed!
    pause
    exit /b 1
)

git commit -m "🚀 Release v%new_version% - %release_notes%"

if %errorlevel% neq 0 (
    echo ❌ Git commit failed!
    pause
    exit /b 1
)

git tag "v%new_version%"

if %errorlevel% neq 0 (
    echo ❌ Git tag failed!
    pause
    exit /b 1
)

echo ✅ Git operations completed

REM Step 6: Push to remote
echo 📤 Step 6: Pushing to remote...
git push origin master

if %errorlevel% neq 0 (
    echo ❌ Push to master failed!
    pause
    exit /b 1
)

git push origin --tags

if %errorlevel% neq 0 (
    echo ❌ Push tags failed!
    pause
    exit /b 1
)

echo ✅ Push completed

REM Step 7: Summary
echo.
echo 🎉 Release Summary:
echo =======================================
echo 📱 APK Information:
echo    • File: CekPicklist-v%new_version%-release.apk
echo    • Version: %new_version%
echo    • Date: %date% %time%
echo.
echo 📝 Git Information:
echo    • Commit: 🚀 Release v%new_version%
echo    • Tag: v%new_version%
echo    • Push: Pushed to origin/master and tags
echo    • README: Updated with version %new_version%
echo.
echo ✅ Complete release workflow finished successfully!
echo =======================================

pause
