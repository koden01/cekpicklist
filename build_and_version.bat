@echo off
REM Build and Version Script untuk Cek Picklist
REM Script ini akan otomatis increment version dan build APK

echo 🚀 Cek Picklist - Build and Version Script
echo =========================================

REM Check if PowerShell is available
powershell.exe -Command "Get-Host" >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ PowerShell not available!
    pause
    exit /b 1
)

REM Run auto versioning and build
powershell.exe -ExecutionPolicy Bypass -File "auto_version.ps1" -BuildType patch -BuildApk

if %errorlevel% equ 0 (
    echo.
    echo ✅ Build and versioning completed successfully!
    echo 📱 Check the generated APK file in the current directory
) else (
    echo.
    echo ❌ Build failed!
)

echo.
pause
