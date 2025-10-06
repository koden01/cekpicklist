@echo off
REM Quick Release Working Script untuk Cek Picklist
REM Script ini akan: Build APK, versioning, dan Git operations tanpa authentication check

echo 🚀 Cek Picklist - Quick Release Working Script
echo =======================================

echo.
echo 📝 Enter release notes (optional, press Enter to skip):
set /p release_notes="Release notes: "

echo.
echo 🚀 Starting quick release working process...
echo.

REM Step 1: Auto Versioning
echo 📈 Step 1: Auto Versioning...
powershell.exe -ExecutionPolicy Bypass -NoProfile -Command ".\simple_version.ps1"

if %errorlevel% neq 0 (
    echo ❌ Versioning failed!
    pause
    exit /b 1
)

echo ✅ Versioning completed

REM Step 2: Update README
echo 📝 Step 2: Updating README...
powershell.exe -ExecutionPolicy Bypass -NoProfile -Command ".\update_readme.ps1"

if %errorlevel% neq 0 (
    echo ❌ README update failed!
    pause
    exit /b 1
)

echo ✅ README updated

REM Step 3: Build APK
echo 🔨 Step 3: Building APK...

REM Check if gradlew exists
if not exist "gradlew.bat" (
    echo ❌ gradlew.bat not found!
    echo 🔍 Debug information:
    echo    • Current directory: %CD%
    echo    • Files in directory:
    dir /b
    pause
    exit /b 1
)

REM Clean before build
echo 🧹 Cleaning project...
.\gradlew clean

if %errorlevel% neq 0 (
    echo ⚠️ Clean failed, continuing with build...
)

echo 🔨 Building release APK...
.\gradlew assembleRelease -x test --no-daemon

if %errorlevel% neq 0 (
    echo ❌ Build failed!
    echo 🔍 Debug information:
    echo    • Gradle wrapper exists: 
    if exist "gradlew.bat" (echo YES) else (echo NO)
    echo    • Build directory exists: 
    if exist "app\build" (echo YES) else (echo NO)
    echo    • Android SDK: 
    echo %ANDROID_HOME%
    pause
    exit /b 1
)

echo ✅ APK build completed

REM Step 4: Copy APK
echo 📱 Step 4: Packaging APK...

REM Check if APK exists
if not exist "app\build\outputs\apk\release\app-release.apk" (
    echo ❌ APK file not found!
    echo 🔍 Debug information:
    echo    • Expected path: app\build\outputs\apk\release\app-release.apk
    echo    • Build outputs directory exists: 
    if exist "app\build\outputs" (echo YES) else (echo NO)
    echo    • APK directory exists: 
    if exist "app\build\outputs\apk" (echo YES) else (echo NO)
    echo    • Release directory exists: 
    if exist "app\build\outputs\apk\release" (echo YES) else (echo NO)
    echo    • Files in release directory:
    if exist "app\build\outputs\apk\release" dir "app\build\outputs\apk\release"
    pause
    exit /b 1
)

REM Get current version from build.gradle.kts
for /f "tokens=*" %%i in ('findstr "versionName" app\build.gradle.kts') do (
    set version_line=%%i
)

REM Extract version number
for /f "tokens=2 delims=^"" %%i in ("%version_line%") do set current_version=%%i

echo 📱 Current version: %current_version%

copy "app\build\outputs\apk\release\app-release.apk" "CekPicklist-v%current_version%-release.apk"

if %errorlevel% neq 0 (
    echo ❌ APK packaging failed!
    echo 🔍 Debug information:
    echo    • Source file exists: 
    if exist "app\build\outputs\apk\release\app-release.apk" (echo YES) else (echo NO)
    echo    • Target directory writable: 
    echo %CD%
    pause
    exit /b 1
)

echo ✅ APK packaged: CekPicklist-v%current_version%-release.apk

REM Step 5: Git Operations
echo 📝 Step 5: Git Operations...
git add .

if %errorlevel% neq 0 (
    echo ❌ Git add failed!
    pause
    exit /b 1
)

git commit -m "🚀 Release v%current_version% - %release_notes%"

if %errorlevel% neq 0 (
    echo ❌ Git commit failed!
    pause
    exit /b 1
)

git tag "v%current_version%"

if %errorlevel% neq 0 (
    echo ❌ Git tag failed!
    pause
    exit /b 1
)

echo ✅ Git operations completed

REM Step 6: Push to remote
echo 📤 Step 6: Pushing to remote...

REM Check current branch
for /f "tokens=*" %%i in ('git branch --show-current 2^>nul') do set current_branch=%%i
echo 🔍 Current branch: %current_branch%

REM Try to push to current branch first, then fallback to master/main
git push origin %current_branch%

if %errorlevel% neq 0 (
    echo ⚠️ Push to %current_branch% failed, trying master...
    git push origin master
    
    if %errorlevel% neq 0 (
        echo ⚠️ Push to master failed, trying main...
        git push origin main
        
        if %errorlevel% neq 0 (
            echo ❌ All push attempts failed!
            echo 🔍 Debug information:
            echo    • Current branch: %current_branch%
            echo    • Remote branches:
            git branch -r
            pause
            exit /b 1
        )
    )
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
echo    • File: CekPicklist-v%current_version%-release.apk
echo    • Version: %current_version%
echo    • Date: %date% %time%
echo.
echo 📝 Git Information:
echo    • Commit: 🚀 Release v%current_version%
echo    • Tag: v%current_version%
echo    • Push: Pushed to origin/%current_branch% and tags
echo.
echo ✅ Quick release working workflow finished successfully!
echo =======================================

pause
