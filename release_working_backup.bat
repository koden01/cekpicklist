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
echo GIT AUTHENTICATION CHECK
echo ========================================
echo.
echo IMPORTANT: Git authentication is required for release process
echo.
echo Git Login Options:
echo    - .\git_simple_setup.bat   - Step-by-step setup (Recommended)
echo    - .\git_fix_login.bat      - Fix authentication issues
echo    - .\git_debug_login.bat    - Debug authentication problems
echo    - .\git_auto_login.bat     - Auto-detect platform and open browser
echo    - .\git_browser_login.bat  - Manual platform selection
echo.
echo Quick setup commands:
echo    git config --global user.name "Your Name"
echo    git config --global user.email "your.email@example.com"
echo    git config --global credential.helper wincred
echo.
echo Test your authentication with:
echo    git push --dry-run origin main
echo.
echo Press 'S' for simple setup, 'F' for fix login, 'D' for debug, 'B' for browser login, or any other key to continue...
set /p login_choice="Choice (S/F/D/B for login help, Enter to continue): "
if /i "%login_choice%"=="S" (
    echo Opening simple setup...
    .\git_simple_setup.bat
    if %errorlevel% neq 0 (
        echo Simple setup failed!
        pause
        exit /b 1
    )
) else (
    if /i "%login_choice%"=="F" (
        echo Opening fix login...
        .\git_fix_login.bat
        if %errorlevel% neq 0 (
            echo Fix login failed!
            pause
            exit /b 1
        )
    ) else (
        if /i "%login_choice%"=="D" (
            echo Opening debug login...
            .\git_debug_login.bat
            if %errorlevel% neq 0 (
                echo Debug login failed!
                pause
                exit /b 1
            )
        ) else (
            if /i "%login_choice%"=="B" (
                echo Opening browser login...
                rem Ensure Git Credential Manager is configured to enable browser-based OAuth
                git config --global credential.helper manager-core >nul 2>&1
                rem Invoke PowerShell helper to open the correct platform page and guide login
                powershell.exe -ExecutionPolicy Bypass -NoProfile -File "git_browser_helper.ps1"
                if %errorlevel% neq 0 (
                    echo Browser login helper failed!
                    pause
                    exit /b 1
                )
            ) else (
                echo Continuing with current authentication...
            )
        )
    )
)
echo.

REM Check Git authentication before proceeding (simplified)
echo Verifying Git authentication...
git remote -v >nul 2>&1 || goto auth_fail

REM Prefer Git Credential Manager (browser OAuth) on Windows
git config --global credential.helper manager-core >nul 2>&1

REM Test Git authentication
echo Testing Git authentication...
git ls-remote origin >nul 2>&1 || goto auth_fail

echo Git authentication verified successfully!
goto auth_ok

:auth_fail
echo Git authentication failed!
echo.
echo Please setup Git authentication:
echo    - Run: .\git_simple_setup.bat
echo    - Or configure manually with Personal Access Token/SSH
echo    - Or choose Browser Login (option B) to sign in via OAuth
echo.
echo Test authentication with:
echo    git push --dry-run origin main
echo.
echo Press any key to exit and setup Git authentication...
pause
exit /b 1

:auth_ok
echo.
echo 🚀 Starting working release process...
echo.

REM Step 1: Auto Versioning
echo 📋 Step 1: Auto Versioning...
REM Ensure version_type has a default when running non-interactively
if "%version_type%"=="" set "version_type=patch"
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

if %errorlevel% neq 0 (
    echo ❌ Versioning failed!
    pause
    exit /b 1
)

REM Step 2: Update README
echo 📝 Step 2: Update README...
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

REM Cleanup temporary files
del version.txt 2>nul

pause
