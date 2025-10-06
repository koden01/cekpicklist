@echo off
REM Git Auto Login Script untuk Cek Picklist
REM Script ini akan auto-detect repository dan membuka browser untuk login

echo 🚀 Cek Picklist - Git Auto Login
echo =================================

echo.
echo 🔍 Auto-detecting Git repository...
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
echo 🔍 Checking repository configuration...
echo.

REM Get remote URL
for /f "tokens=*" %%i in ('git remote get-url origin 2^>nul') do set remote_url=%%i

if "%remote_url%"=="" (
    echo ❌ No Git remote configured!
    echo 🔍 Please configure Git remote first:
    echo    git remote add origin <repository-url>
    echo.
    echo ⏸️  Press any key to exit...
    pause
    exit /b 1
)

echo ✅ Git remote configured: %remote_url%
echo.

REM Auto-detect platform
echo 🔍 Auto-detecting platform...
if "%remote_url:github.com=%" neq "%remote_url%" (
    set platform=GitHub
    set token_url=https://github.com/settings/tokens
    set platform_name=GitHub
) else if "%remote_url:gitlab.com=%" neq "%remote_url%" (
    set platform=GitLab
    set token_url=https://gitlab.com/-/profile/personal_access_tokens
    set platform_name=GitLab
) else if "%remote_url:bitbucket.org=%" neq "%remote_url%" (
    set platform=Bitbucket
    set token_url=https://bitbucket.org/account/settings/app-passwords/
    set platform_name=Bitbucket
) else (
    set platform=Custom
    set token_url=
    set platform_name=Custom
)

echo ✅ Detected platform: %platform_name%
echo.

if "%platform%"=="Custom" (
    echo 🔧 Custom repository detected
    echo 📝 Please configure authentication manually
    echo.
    echo 🔧 Quick setup commands:
    echo    git config --global user.name "Your Name"
    echo    git config --global user.email "your.email@example.com"
    echo    git config --global credential.helper wincred
    echo.
    echo 📝 Test authentication with:
    echo    git push --dry-run origin main
    echo.
    goto :test_auth
)

echo 🌐 Opening %platform_name% in browser for authentication...
echo.
echo 📝 Instructions:
echo ========================================
echo.
if "%platform%"=="GitHub" (
    echo 1. Go to: %token_url%
    echo 2. Click "Generate new token (classic)"
    echo 3. Select scopes: repo, workflow, write:packages
    echo 4. Copy the generated token
    echo 5. Use your GitHub username and token as password
) else if "%platform%"=="GitLab" (
    echo 1. Go to: %token_url%
    echo 2. Click "Add new token"
    echo 3. Select scopes: read_repository, write_repository, api
    echo 4. Copy the generated token
    echo 5. Use your GitLab username and token as password
) else if "%platform%"=="Bitbucket" (
    echo 1. Go to: %token_url%
    echo 2. Click "Create app password"
    echo 3. Select permissions: Repositories (Read, Write)
    echo 4. Copy the generated password
    echo 5. Use your Bitbucket username and app password as password
)
echo.
echo 🔧 Quick setup commands:
echo    git config --global user.name "Your Name"
echo    git config --global user.email "your.email@example.com"
echo    git config --global credential.helper wincred
echo.
echo 📝 Test authentication with:
echo    git push --dry-run origin main
echo.

REM Open browser
start %token_url%

echo ⏸️  Press any key after setting up your authentication...
pause

:test_auth
echo.
echo 🔐 Testing Git authentication...
echo ========================================

REM Test authentication
echo 🔐 Testing authentication...
git ls-remote origin >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Git authentication failed!
    echo.
    echo 🔧 Please try again:
    echo    • Run this script again
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
