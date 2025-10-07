@echo off
REM Simple Fix Script - No Git Pager Issues
echo 🔧 Simple Release Fix Script
echo =============================

REM Set environment to avoid pager
set GIT_PAGER=
set PAGER=

echo.
echo 📋 Step 1: Basic Git Info
echo -------------------------
echo Current directory: %CD%
echo Git repository: 
if exist .git (echo YES) else (echo NO)

echo.
echo 📋 Step 2: Version Check
echo -------------------------
if exist version.txt (
    for /f "delims=" %%i in (version.txt) do echo Version: %%i
) else (
    echo version.txt not found!
)

echo.
echo 📋 Step 3: File Changes Check
echo ------------------------------
echo Modified files:
dir /b *.md *.txt *.bat *.ps1 2>nul
dir /b app\build.gradle.kts 2>nul

echo.
echo 📋 Step 4: Git Operations (Simple)
echo -----------------------------------
echo Adding all files...
git add . >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Files added successfully
) else (
    echo ❌ Git add failed
)

echo.
echo Committing changes...
git commit -m "🚀 Release v4.4.0 - Optimasi kecepatan update data modal picklist" >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Commit successful
) else (
    echo ⚠️ Commit failed (may already be committed)
)

echo.
echo Creating tag...
git tag v4.4.0 >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Tag v4.4.0 created
) else (
    echo ⚠️ Tag creation failed (may already exist)
)

echo.
echo Pushing to remote...
git push origin master >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Push to master successful
) else (
    echo ⚠️ Push to master failed, trying main...
    git push origin main >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✅ Push to main successful
    ) else (
        echo ❌ Push failed - check authentication
    )
)

echo.
echo Pushing tags...
git push origin --tags >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Tags pushed successfully
) else (
    echo ❌ Push tags failed
)

echo.
echo 🎉 Simple Fix Complete!
echo ======================
echo Check the results above.
echo If push failed, run: .\git_simple_setup.bat
echo ======================

pause
