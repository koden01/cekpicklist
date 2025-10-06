@echo off
REM Git Debug Login Script untuk Cek Picklist
REM Script ini akan membantu debug masalah Git authentication

echo 🔍 Cek Picklist - Git Debug Login
echo ==================================

echo.
echo 🔍 Debugging Git authentication issues...
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
echo User Name: 
git config --global user.name
echo User Email: 
git config --global user.email
echo Credential Helper: 
git config --global credential.helper
echo ========================================

echo.
echo 🔍 Checking Git remote configuration...
git remote -v
if %errorlevel% neq 0 (
    echo ❌ No Git remote configured!
    echo 🔍 Please configure Git remote first:
    echo    git remote add origin <repository-url>
    echo.
    echo ⏸️  Press any key to exit...
    pause
    exit /b 1
)

echo.
echo 🔍 Testing Git authentication...
echo ========================================

REM Test authentication with verbose output
echo 🔐 Testing authentication with verbose output...
git ls-remote origin
if %errorlevel% neq 0 (
    echo ❌ Git authentication failed!
    echo.
    echo 🔧 Troubleshooting steps:
    echo ========================================
    echo.
    echo 1. Check your credentials:
    echo    • Username: 
    git config --global user.name
    echo    • Email: 
    git config --global user.email
    echo.
    echo 2. Check your remote URL:
    git remote get-url origin
    echo.
    echo 3. Try manual authentication:
    echo    git push --dry-run origin main
    echo    (Enter your username and password/token when prompted)
    echo.
    echo 4. Check credential helper:
    echo    git config --global credential.helper
    echo.
    echo 5. Clear stored credentials:
    echo    git config --global --unset credential.helper
    echo    git config --global credential.helper wincred
    echo.
    echo 6. Test with different authentication methods:
    echo    • Personal Access Token (HTTPS)
    echo    • SSH Key (SSH)
    echo    • Git Credential Manager
    echo.
    echo ⏸️  Press any key to continue...
    pause
    goto :troubleshoot
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

:troubleshoot
echo.
echo 🔧 Advanced Troubleshooting:
echo ========================================
echo.
echo 1. Clear all stored credentials:
echo    git config --global --unset credential.helper
echo    git config --global credential.helper wincred
echo.
echo 2. Test with fresh credentials:
echo    git push --dry-run origin main
echo    (Enter your username and password/token when prompted)
echo.
echo 3. Check if you have the right permissions:
echo    • For GitHub: Check repository access
echo    • For GitLab: Check project access
echo    • For Bitbucket: Check repository access
echo.
echo 4. Try different authentication methods:
echo    • Personal Access Token (HTTPS)
echo    • SSH Key (SSH)
echo    • Git Credential Manager
echo.
echo 5. Check your network connection:
echo    • Can you access the repository in browser?
echo    • Are you behind a corporate firewall?
echo.
echo 6. Try manual Git commands:
echo    git config --global user.name "Your Name"
echo    git config --global user.email "your.email@example.com"
echo    git config --global credential.helper wincred
echo    git push --dry-run origin main
echo.
echo ⏸️  Press any key to exit...
pause
