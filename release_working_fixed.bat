@echo off
REM Fixed Release Script untuk Cek Picklist
REM Script ini akan: Auto versioning, Update README, Build APK, dan Git operations
REM Perbaikan: Menghindari pager issues, credential manager problems, dan error handling yang lebih baik

echo 🚀 Cek Picklist - Fixed Release Script
echo =======================================

REM Set environment variables to avoid pager issues
set GIT_PAGER=
set PAGER=
set GIT_CONFIG_GLOBAL=~/.gitconfig

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
echo 🔍 Step 1: Pre-flight Checks
echo ========================================

REM Check if we're in the right directory
if not exist "app\build.gradle.kts" (
    echo ❌ Error: app\build.gradle.kts not found!
    echo Please run this script from the project root directory.
    pause
    exit /b 1
)

REM Check if gradlew exists
if not exist "gradlew.bat" (
    echo ❌ Error: gradlew.bat not found!
    echo Please ensure you're in the Android project root.
    pause
    exit /b 1
)

REM Check if version script exists
if not exist "simple_version.ps1" (
    echo ❌ Error: simple_version.ps1 not found!
    echo Please ensure versioning script is available.
    pause
    exit /b 1
)

echo ✅ Pre-flight checks passed

echo.
echo 🔍 Step 2: Git Authentication Check
echo ========================================

REM Test Git authentication without pager
echo Testing Git authentication...
git ls-remote origin >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Git authentication failed!
    echo.
    echo 💡 Quick fixes:
    echo    1. Run: git config --global credential.helper manager
    echo    2. Run: git config --global user.name "Your Name"
    echo    3. Run: git config --global user.email "your.email@example.com"
    echo    4. Test: git push --dry-run origin master
    echo.
    echo Press any key to exit and fix authentication...
    pause
    exit /b 1
) else (
    echo ✅ Git authentication successful
)

echo.
echo 🚀 Step 3: Auto Versioning
echo ========================================

REM Ensure version_type has a default when running non-interactively
if "%version_type%"=="" set "version_type=patch"

echo Running versioning script...
powershell.exe -ExecutionPolicy Bypass -NoProfile -Command ".\simple_version.ps1 -VersionType '%version_type%'"

if %errorlevel% neq 0 (
    echo ❌ Versioning failed!
    echo.
    echo 🔍 Debug information:
    echo    • PowerShell execution policy: 
    powershell.exe -Command "Get-ExecutionPolicy"
    echo    • Current directory: %CD%
    echo    • Script exists: 
    if exist "simple_version.ps1" (echo YES) else (echo NO)
    echo    • Build.gradle.kts exists: 
    if exist "app\build.gradle.kts" (echo YES) else (echo NO)
    pause
    exit /b 1
)

REM Get new version from file
for /f "delims=" %%i in (version.txt) do set new_version=%%i
echo ✅ New version: %new_version%

if "%new_version%"=="" (
    echo ❌ Version not found in version.txt!
    pause
    exit /b 1
)

echo.
echo 📝 Step 4: Update README
echo ========================================

echo Updating README with new version...
powershell.exe -ExecutionPolicy Bypass -NoProfile -Command ".\update_readme.ps1 -Version '%new_version%'"

if %errorlevel% neq 0 (
    echo ❌ README update failed!
    echo.
    echo 🔍 Debug information:
    echo    • README.md exists: 
    if exist "README.md" (echo YES) else (echo NO)
    echo    • Update script exists: 
    if exist "update_readme.ps1" (echo YES) else (echo NO)
    echo    • Version parameter: %new_version%
    pause
    exit /b 1
)

echo ✅ README updated successfully

echo.
echo 🔨 Step 5: Build APK
echo ========================================

echo 🧹 Cleaning project...
.\gradlew clean >nul 2>&1

if %errorlevel% neq 0 (
    echo ⚠️ Clean failed, continuing with build...
)

echo 🔨 Building release APK...
.\gradlew assembleRelease -x test --no-daemon

if %errorlevel% neq 0 (
    echo ❌ Build failed!
    echo.
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

echo.
echo 📱 Step 6: Package APK
echo ========================================

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

copy "app\build\outputs\apk\release\app-release.apk" "CekPicklist-v%new_version%-release.apk"

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

echo ✅ APK packaged: CekPicklist-v%new_version%-release.apk

echo.
echo 📝 Step 7: Git Operations
echo ========================================

echo Adding all changes to git...
git add . >nul 2>&1

if %errorlevel% neq 0 (
    echo ❌ Git add failed!
    pause
    exit /b 1
)

echo ✅ Files added to staging

echo.
echo Committing changes...
git commit -m "🚀 Release v%new_version% - %release_notes%"

if %errorlevel% neq 0 (
    echo ❌ Git commit failed!
    echo.
    echo 💡 This might happen if there are no changes to commit.
    echo Check git status manually: git status
    pause
    exit /b 1
)

echo ✅ Changes committed

echo.
echo Creating tag v%new_version%...
git tag "v%new_version%"

if %errorlevel% neq 0 (
    echo ❌ Git tag failed!
    echo.
    echo 💡 This might happen if the tag already exists.
    echo Check existing tags: git tag --list
    pause
    exit /b 1
)

echo ✅ Tag v%new_version% created

echo.
echo 📤 Step 8: Push to GitHub
echo ========================================

REM Check current branch
for /f "tokens=*" %%i in ('git branch --show-current 2^>nul') do set current_branch=%%i
echo 🔍 Current branch: %current_branch%

REM Try to push to current branch first
echo 📤 Pushing commits to %current_branch%...
git push origin %current_branch%

if %errorlevel% neq 0 (
    echo ⚠️ Push to %current_branch% failed, trying master...
    git push origin master
    
    if %errorlevel% neq 0 (
        echo ⚠️ Push to master failed, trying main...
        git push origin main
        
        if %errorlevel% neq 0 (
            echo ❌ All push attempts failed!
            echo.
            echo 🔍 Debug information:
            echo    • Current branch: %current_branch%
            echo    • Remote branches:
            git branch -r
            echo.
            echo 💡 Possible solutions:
            echo    1. Check internet connection
            echo    2. Verify GitHub authentication
            echo    3. Run: git config --global credential.helper manager
            echo    4. Check repository permissions
            echo.
            pause
            exit /b 1
        ) else (
            echo ✅ Pushed to main branch
        )
    ) else (
        echo ✅ Pushed to master branch
    )
) else (
    echo ✅ Pushed to %current_branch% branch
)

echo.
echo 🏷️ Pushing tags...
git push origin --tags

if %errorlevel% neq 0 (
    echo ❌ Push tags failed!
    echo.
    echo 💡 Try manual push:
    echo    git push origin v%new_version%
    pause
    exit /b 1
)

echo ✅ Tags pushed successfully

echo.
echo 🎉 Release Summary
echo =======================================
echo 📱 APK Information:
echo    • File: CekPicklist-v%new_version%-release.apk
echo    • Version: %new_version%
echo    • Date: %date% %time%
echo.
echo 📝 Git Information:
echo    • Commit: 🚀 Release v%new_version%
echo    • Tag: v%new_version%
echo    • Push: Pushed to origin and tags
echo    • README: Updated with version %new_version%
echo.
echo ✅ Complete release workflow finished successfully!
echo =======================================

REM Cleanup temporary files
del version.txt 2>nul

echo.
echo 🚀 Release v%new_version% completed successfully!
echo Check your GitHub repository for the new release.
echo.

pause
