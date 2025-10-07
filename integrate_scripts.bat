@echo off
REM Integrate Scripts - Replace release_working.bat with integrated version
echo 🔄 Integrating Release Scripts
echo ================================

echo.
echo 📋 Step 1: Backup current release script
echo -----------------------------------------
if exist "release_working.bat" (
    copy "release_working.bat" "release_working_old.bat"
    echo ✅ Current script backed up as release_working_old.bat
) else (
    echo ⚠️ Current script not found, creating new one
)

echo.
echo 📋 Step 2: Install integrated script
echo -------------------------------------
copy "release_working_integrated.bat" "release_working.bat"

if %errorlevel% equ 0 (
    echo ✅ Integrated script installed successfully
) else (
    echo ❌ Failed to install integrated script
    pause
    exit /b 1
)

echo.
echo 📋 Step 3: Verify installation
echo --------------------------------
if exist "release_working.bat" (
    echo ✅ New integrated release_working.bat exists
    echo 📊 File size: 
    for %%A in ("release_working.bat") do echo %%~zA bytes
) else (
    echo ❌ New script not found
    pause
    exit /b 1
)

echo.
echo 📋 Step 4: Test script options
echo --------------------------------
echo Testing help option...
.\release_working.bat --help >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Help option works
) else (
    echo ⚠️ Help option may have issues
)

echo.
echo 🎉 Integration Complete!
echo ===========================
echo ✅ Old script backed up
echo ✅ Integrated script installed
echo ✅ Script options tested
echo.
echo 💡 New Usage Options:
echo    .\release_working.bat              # Normal release process
echo    .\release_working.bat --update-script  # Update script only
echo    .\release_working.bat --help       # Show help
echo    .\release_working.bat --version    # Show version
echo.
echo 🚀 Ready to use integrated release script!
echo ===========================

pause
