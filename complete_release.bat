@echo off
REM Complete Release Script untuk menyelesaikan release yang gagal
REM Script ini akan: Commit changes, Create tag, dan Push ke GitHub

echo 🚀 Cek Picklist - Complete Release Script
echo =======================================

REM Get version from version.txt
for /f "delims=" %%i in (version.txt) do set new_version=%%i
echo 📱 Target version: %new_version%

if "%new_version%"=="" (
    echo ❌ Version not found in version.txt!
    pause
    exit /b 1
)

echo.
echo 🔍 Checking Git status...
git status

echo.
echo 📝 Step 1: Adding all changes...
git add .

if %errorlevel% neq 0 (
    echo ❌ Git add failed!
    pause
    exit /b 1
)

echo ✅ Files added to staging

echo.
echo 📝 Step 2: Committing changes...
git commit -m "🚀 Release v%new_version% - Optimasi kecepatan update data modal picklist

- Kurangi cache TTL untuk data sensitif (2 menit untuk status, 30 menit untuk items)
- Implementasi force refresh untuk bypass cache
- Tambahkan auto-refresh periodic setiap 30 detik
- Tambahkan manual refresh button di modal
- Optimasi batch queries untuk performa lebih baik
- Perbaiki UpdateChecker agar tidak meminta update jika versi sama"

if %errorlevel% neq 0 (
    echo ❌ Git commit failed!
    pause
    exit /b 1
)

echo ✅ Changes committed

echo.
echo 🏷️ Step 3: Creating tag v%new_version%...
git tag "v%new_version%"

if %errorlevel% neq 0 (
    echo ❌ Git tag failed!
    pause
    exit /b 1
)

echo ✅ Tag v%new_version% created

echo.
echo 🔍 Step 4: Checking remote configuration...
git remote -v

echo.
echo 📤 Step 5: Pushing to remote...

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
            echo    3. Run: .\git_simple_setup.bat
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
echo 🏷️ Step 6: Pushing tags...
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
echo 🎉 Release Completed Successfully!
echo =======================================
echo 📱 Version: %new_version%
echo 📝 Commit: 🚀 Release v%new_version%
echo 🏷️ Tag: v%new_version%
echo 📤 Push: Pushed to origin and tags
echo 📄 README: Updated with version %new_version%
echo.
echo ✅ All changes have been pushed to GitHub!
echo =======================================

REM Cleanup
del version.txt 2>nul

pause
