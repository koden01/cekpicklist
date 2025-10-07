@echo off
REM Fix Git Push Script untuk mengatasi masalah push ke GitHub

echo 🔧 Cek Picklist - Fix Git Push Script
echo =======================================

echo.
echo 🔍 Step 1: Checking Git configuration...
echo.

echo 📋 Git User Configuration:
git config --global user.name
git config --global user.email

echo.
echo 📋 Git Remote Configuration:
git remote -v

echo.
echo 📋 Current Branch:
git branch --show-current

echo.
echo 📋 Git Status:
git status --short

echo.
echo 🔍 Step 2: Testing Git authentication...
echo.

echo 🧪 Testing connection to GitHub...
git ls-remote origin

if %errorlevel% neq 0 (
    echo ❌ Git authentication failed!
    echo.
    echo 💡 Solutions:
    echo    1. Run: .\git_simple_setup.bat
    echo    2. Or configure manually:
    echo       git config --global user.name "Your Name"
    echo       git config --global user.email "your.email@example.com"
    echo       git config --global credential.helper manager-core
    echo.
    echo    3. For Personal Access Token:
    echo       - Go to GitHub → Settings → Developer settings
    echo       - Generate new token with repo permissions
    echo       - Use token as password when prompted
    echo.
    pause
    exit /b 1
) else (
    echo ✅ Git authentication successful!
)

echo.
echo 🔍 Step 3: Checking for uncommitted changes...
git status --porcelain

if %errorlevel% neq 0 (
    echo ❌ Error checking git status
    pause
    exit /b 1
)

echo.
echo 🔍 Step 4: Checking commit history...
git log --oneline -5

echo.
echo 🔍 Step 5: Checking tags...
git tag --list

echo.
echo 🔍 Step 6: Checking remote branches...
git branch -r

echo.
echo 📋 Summary:
echo =======================================
echo ✅ Git configuration looks good
echo ✅ Authentication successful
echo ✅ Ready for push operations
echo.
echo 💡 Next steps:
echo    1. Run: .\complete_release.bat
echo    2. Or manually:
echo       git add .
echo       git commit -m "Release v4.4.0"
echo       git tag v4.4.0
echo       git push origin master
echo       git push origin --tags
echo =======================================

pause
