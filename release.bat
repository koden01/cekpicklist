@echo off
REM Complete Release Script untuk Cek Picklist
REM Script ini akan: Auto versioning, Update README, Build APK, dan Git operations

echo 🚀 Cek Picklist - Complete Release
echo ==================================

REM Check if PowerShell is available
powershell.exe -Command "Get-Host" >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ PowerShell not available!
    pause
    exit /b 1
)

REM Get version type from user
echo.
echo Select version type:
echo 1. Patch (1.0.2 → 1.0.3)
echo 2. Minor (1.0.2 → 1.1.0)
echo 3. Major (1.0.2 → 2.0.0)
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

REM Run complete release script
if "%release_notes%"=="" (
    powershell.exe -ExecutionPolicy Bypass -File "complete_release.ps1" -VersionType %version_type%
) else (
    powershell.exe -ExecutionPolicy Bypass -File "complete_release.ps1" -VersionType %version_type% -ReleaseNotes "%release_notes%"
)

if %errorlevel% equ 0 (
    echo.
    echo ✅ Complete release finished successfully!
    echo 📱 Check the generated APK file in the current directory
    echo 📝 README has been updated with new version
    echo 🏷️ Git tag has been created
) else (
    echo.
    echo ❌ Release failed!
)

echo.
pause
