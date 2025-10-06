@echo off
REM Git Authentication Setup Script untuk Cek Picklist
REM Script ini akan membantu setup Git authentication

echo 🔐 Cek Picklist - Git Authentication Setup
echo ==========================================

echo.
echo 📋 Checking current Git configuration...
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
echo 🔐 Checking Git remote configuration...
git remote -v

echo.
echo 🔐 Testing Git authentication...
git ls-remote origin >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Git authentication failed!
    echo.
    echo 🔧 Setup Instructions:
    echo ========================================
    echo.
    echo 📝 Option 1: Personal Access Token (HTTPS)
    echo ----------------------------------------
    echo 1. Go to GitHub/GitLab → Settings → Developer settings
    echo 2. Generate new Personal Access Token
    echo 3. Copy the token
    echo 4. When prompted for password, use the token
    echo.
    echo 📝 Option 2: SSH Key (More Secure)
    echo ----------------------------------
    echo 1. Generate SSH key:
    echo    ssh-keygen -t rsa -b 4096 -C "your.email@example.com"
    echo.
    echo 2. Add to SSH agent:
    echo    ssh-add ~/.ssh/id_rsa
    echo.
    echo 3. Add public key to GitHub/GitLab:
    echo    cat ~/.ssh/id_rsa.pub
    echo.
    echo 📝 Option 3: Git Credential Manager
    echo -----------------------------------
    echo 1. Install Git Credential Manager
    echo 2. Configure credential helper:
    echo    git config --global credential.helper manager-core
    echo.
    echo 🔧 Quick Setup Commands:
    echo ========================================
    echo.
    echo # Set user information
    echo git config --global user.name "Your Name"
    echo git config --global user.email "your.email@example.com"
    echo.
    echo # Set credential helper (Windows)
    echo git config --global credential.helper wincred
    echo.
    echo # Test authentication
    echo git push --dry-run origin main
    echo.
    echo ========================================
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
)

echo.
echo 🔧 Additional Git Commands:
echo ========================================
echo.
echo # Check current branch
echo git branch --show-current
echo.
echo # Check remote branches
echo git branch -r
echo.
echo # Test push (dry run)
echo git push --dry-run origin main
echo.
echo # Check Git status
echo git status
echo.
echo ========================================

pause
