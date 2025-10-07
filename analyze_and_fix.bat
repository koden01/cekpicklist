@echo off
REM Analyze and Fix Release Script
echo 🔍 Analyzing Git Status and Fixing Release Issues
echo ================================================

REM Disable git pager
set GIT_PAGER=cat

echo.
echo 📋 Step 1: Checking Git Configuration
echo ----------------------------------------
git config --global user.name
git config --global user.email
git remote -v

echo.
echo 📋 Step 2: Checking Current Status
echo ----------------------------------------
git status --short

echo.
echo 📋 Step 3: Checking Version
echo ----------------------------------------
if exist version.txt (
    for /f "delims=" %%i in (version.txt) do echo Current version: %%i
) else (
    echo version.txt not found!
)

echo.
echo 📋 Step 4: Checking Recent Commits
echo ----------------------------------------
git log --oneline -3

echo.
echo 📋 Step 5: Checking Tags
echo ----------------------------------------
git tag --list

echo.
echo 📋 Step 6: Testing Git Authentication
echo ----------------------------------------
git ls-remote origin >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Git authentication successful
) else (
    echo ❌ Git authentication failed
    echo.
    echo 💡 Fix authentication:
    echo    git config --global credential.helper manager-core
    echo    git config --global user.name "Your Name"
    echo    git config --global user.email "your.email@example.com"
)

echo.
echo 📋 Step 7: Checking for Uncommitted Changes
echo ----------------------------------------
git diff --name-only

echo.
echo 🚀 Step 8: Attempting to Complete Release
echo ----------------------------------------

REM Get version from version.txt
for /f "delims=" %%i in (version.txt) do set new_version=%%i

if "%new_version%"=="" (
    echo ❌ Version not found in version.txt!
    goto :end
)

echo 📱 Target version: %new_version%

REM Add all changes
echo 📝 Adding all changes...
git add .

REM Commit changes
echo 📝 Committing changes...
git commit -m "🚀 Release v%new_version% - Optimasi kecepatan update data modal picklist

- Kurangi cache TTL untuk data sensitif (2 menit untuk status, 30 menit untuk items)
- Implementasi force refresh untuk bypass cache
- Tambahkan auto-refresh periodic setiap 30 detik
- Tambahkan manual refresh button di modal
- Optimasi batch queries untuk performa lebih baik
- Perbaiki UpdateChecker agar tidak meminta update jika versi sama"

if %errorlevel% neq 0 (
    echo ❌ Commit failed! Changes may already be committed.
) else (
    echo ✅ Changes committed successfully
)

REM Create tag
echo 🏷️ Creating tag v%new_version%...
git tag "v%new_version%"

if %errorlevel% neq 0 (
    echo ❌ Tag creation failed! Tag may already exist.
) else (
    echo ✅ Tag v%new_version% created successfully
)

REM Push to remote
echo 📤 Pushing to remote...
git push origin master

if %errorlevel% neq 0 (
    echo ⚠️ Push to master failed, trying main...
    git push origin main
    
    if %errorlevel% neq 0 (
        echo ❌ Push failed! Check authentication and network.
        echo.
        echo 💡 Manual push commands:
        echo    git push origin master
        echo    git push origin --tags
    ) else (
        echo ✅ Pushed to main branch successfully
    )
) else (
    echo ✅ Pushed to master branch successfully
)

REM Push tags
echo 🏷️ Pushing tags...
git push origin --tags

if %errorlevel% neq 0 (
    echo ❌ Push tags failed!
) else (
    echo ✅ Tags pushed successfully
)

echo.
echo 🎉 Release Analysis and Fix Complete!
echo ================================================
echo 📱 Version: %new_version%
echo 📝 Status: Check output above for results
echo ================================================

:end
pause
