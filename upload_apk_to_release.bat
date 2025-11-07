@echo off
REM Script untuk mengupload APK ke GitHub Release yang sudah ada
REM Berguna jika release tag sudah dibuat tapi APK belum terupload

echo 📦 Upload APK to Existing GitHub Release
echo =========================================
echo.

REM Check if GitHub CLI is installed
where gh >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ GitHub CLI not found!
    echo.
    echo 💡 Please install GitHub CLI first:
    echo    winget install --id GitHub.cli
    echo.
    pause
    exit /b 1
)

echo ✅ GitHub CLI found
echo.

REM Check authentication
gh auth status >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Not authenticated with GitHub!
    echo.
    echo 💡 Please authenticate first:
    echo    gh auth login
    echo.
    pause
    exit /b 1
)

echo ✅ Authenticated with GitHub
echo.

REM Get version tag from user
set /p version_tag="Enter version tag (e.g., v5.1.14): "

if "%version_tag%"=="" (
    echo ❌ Version tag is required!
    pause
    exit /b 1
)

REM Check if release exists
echo.
echo 🔍 Checking if release %version_tag% exists...
gh release view "%version_tag%" >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Release %version_tag% not found!
    echo.
    echo 💡 Available releases:
    gh release list --limit 10
    echo.
    pause
    exit /b 1
)

echo ✅ Release %version_tag% found
echo.

REM Extract version number (remove 'v' prefix)
set version_number=%version_tag:~1%

REM Check if APK file exists
set apk_filename=CekPicklist-v%version_number%-release.apk

echo 🔍 Looking for APK file: %apk_filename%

if not exist "%apk_filename%" (
    echo ❌ APK file not found: %apk_filename%
    echo.
    echo 🔍 Available APK files in current directory:
    dir /b *.apk 2>nul
    if %errorlevel% neq 0 (
        echo    (No APK files found)
    )
    echo.
    set /p custom_apk="Enter APK filename (or press Enter to exit): "
    if "%custom_apk%"=="" (
        echo Exiting...
        pause
        exit /b 1
    )
    set apk_filename=%custom_apk%
    
    if not exist "%apk_filename%" (
        echo ❌ File not found: %apk_filename%
        pause
        exit /b 1
    )
)

for %%A in ("%apk_filename%") do set apk_size=%%~zA
echo ✅ APK file found: %apk_filename% (%apk_size% bytes)
echo.

REM Check if APK is already uploaded
echo 🔍 Checking if APK is already in release assets...
gh release view "%version_tag%" --json assets --jq ".assets[].name" | findstr "%apk_filename%" >nul 2>&1
if %errorlevel% equ 0 (
    echo ⚠️ APK already exists in release assets!
    echo.
    set /p overwrite="Delete and re-upload? (y/N): "
    if /i not "%overwrite%"=="Y" (
        echo Cancelled.
        pause
        exit /b 0
    )
    echo.
    echo 🗑️ Deleting existing asset...
    gh release delete-asset "%version_tag%" "%apk_filename%" --yes
    if %errorlevel% neq 0 (
        echo ⚠️ Failed to delete existing asset, continuing anyway...
    ) else (
        echo ✅ Existing asset deleted
    )
    echo.
)

REM Upload APK
echo 📤 Uploading APK to release %version_tag%...
echo    File: %apk_filename%
echo    Size: %apk_size% bytes
echo.

gh release upload "%version_tag%" "%apk_filename%"

if %errorlevel% equ 0 (
    echo.
    echo ✅ APK uploaded successfully!
    echo.
    
    REM Verify upload
    echo 🔍 Verifying upload...
    gh release view "%version_tag%" --json assets --jq ".assets[].name" | findstr "%apk_filename%" >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✅ Verification successful!
        echo.
        echo 🔗 Release URL: https://github.com/koden01/cekpicklist/releases/tag/%version_tag%
        echo 📥 Download URL: https://github.com/koden01/cekpicklist/releases/download/%version_tag%/%apk_filename%
        echo.
        echo 🔄 Auto-update in app will now work!
    ) else (
        echo ⚠️ Upload might have failed - asset not found in release
        echo.
        echo 💡 Check manually at:
        echo    https://github.com/koden01/cekpicklist/releases/tag/%version_tag%
    )
) else (
    echo ❌ Failed to upload APK!
    echo.
    echo 💡 Error code: %errorlevel%
    echo.
    echo 💡 Possible solutions:
    echo    1. Check file exists: %CD%\%apk_filename%
    echo    2. Check GitHub authentication: gh auth status
    echo    3. Check release exists: gh release view %version_tag%
    echo    4. Try manually at: https://github.com/koden01/cekpicklist/releases/edit/%version_tag%
    echo.
)

echo.
echo 📊 Current release assets:
gh release view "%version_tag%" --json assets --jq ".assets[] | .name + \" (\" + (.size | tostring) + \" bytes)\""

echo.
echo ========================================
echo Done!
echo ========================================
echo.
pause

