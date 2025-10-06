@echo off
REM Quick Git Setup Script untuk Cek Picklist
REM Script ini akan membantu setup Git authentication dengan cepat

echo 🔐 Cek Picklist - Quick Git Setup
echo ==================================

echo.
echo 📋 This script will help you setup Git authentication quickly
echo.

REM Check if Git is installed
git --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Git is not installed!
    echo 🔍 Please install Git first: https://git-scm.com/downloads
    pause
    exit /b 1
)

echo ✅ Git is installed
git --version

echo.
echo 📋 Current Git configuration:
echo ========================================
git config --global user.name
git config --global user.email
echo ========================================

echo.
echo 🔧 Quick Setup Options:
echo ========================================
echo.
echo 1. Personal Access Token (HTTPS) - Recommended
echo 2. SSH Key (More Secure)
echo 3. Test Current Authentication
echo 4. Exit
echo.
set /p choice="Enter your choice (1-4): "

if "%choice%"=="1" goto :setup_token
if "%choice%"=="2" goto :setup_ssh
if "%choice%"=="3" goto :test_auth
if "%choice%"=="4" goto :exit
goto :invalid_choice

:setup_token
echo.
echo 📝 Personal Access Token Setup:
echo ========================================
echo.
echo 1. Go to GitHub/GitLab → Settings → Developer settings
echo 2. Generate new Personal Access Token
echo 3. Copy the token
echo 4. When prompted for password, use the token
echo.
echo 🔧 Quick commands:
echo    git config --global credential.helper wincred
echo    git push --dry-run origin main
echo.
echo ⏸️  Press any key after setting up your token...
pause
goto :test_auth

:setup_ssh
echo.
echo 📝 SSH Key Setup:
echo ========================================
echo.
echo 1. Generate SSH key:
echo    ssh-keygen -t rsa -b 4096 -C "your.email@example.com"
echo.
echo 2. Add to SSH agent:
echo    ssh-add ~/.ssh/id_rsa
echo.
echo 3. Add public key to GitHub/GitLab:
echo    cat ~/.ssh/id_rsa.pub
echo.
echo ⏸️  Press any key after setting up your SSH key...
pause
goto :test_auth

:test_auth
echo.
echo 🔐 Testing Git authentication...
echo ========================================

REM Check remote configuration
git remote -v >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Git remote not configured!
    echo 🔍 Please configure Git remote first:
    echo    git remote add origin <repository-url>
    echo.
    echo ⏸️  Press any key to exit...
    pause
    exit /b 1
)

echo ✅ Git remote configured
git remote -v

REM Test authentication
echo 🔐 Testing authentication...
git ls-remote origin >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Git authentication failed!
    echo.
    echo 🔧 Please setup authentication:
    echo    • Run this script again and choose option 1 or 2
    echo    • Or configure manually with Personal Access Token/SSH
    echo.
    echo 📝 Test authentication with:
    echo    git push --dry-run origin main
    echo.
    echo ⏸️  Press any key to exit...
    pause
    exit /b 1
) else (
    echo ✅ Git authentication successful!
    echo.
    echo 🎉 You're ready to run release scripts!
    echo.
    echo 📋 Available scripts:
    echo    • release_working.bat  - Full release with versioning
    echo    • release_simple.bat   - Quick release without versioning
    echo.
    echo ⏸️  Press any key to exit...
    pause
    exit /b 0
)

:invalid_choice
echo ❌ Invalid choice! Please enter 1-4.
goto :exit

:exit
echo.
echo 👋 Goodbye!
pause
