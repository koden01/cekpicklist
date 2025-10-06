@echo off
REM Git Browser Login Script untuk Cek Picklist
REM Script ini akan membuka browser untuk login Git dengan mudah

echo 🌐 Cek Picklist - Git Browser Login
echo ====================================

echo.
echo 🔐 This script will help you login to Git using your browser
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
echo 🌐 Browser Login Options:
echo ========================================
echo.
echo 1. GitHub Login (Recommended)
echo 2. GitLab Login
echo 3. Bitbucket Login
echo 4. Custom Repository URL
echo 5. Test Current Authentication
echo 6. Exit
echo.
set /p choice="Enter your choice (1-6): "

if "%choice%"=="1" goto :github_login
if "%choice%"=="2" goto :gitlab_login
if "%choice%"=="3" goto :bitbucket_login
if "%choice%"=="4" goto :custom_login
if "%choice%"=="5" goto :test_auth
if "%choice%"=="6" goto :exit
goto :invalid_choice

:github_login
echo.
echo 🐙 GitHub Login Setup:
echo ========================================
echo.
echo 📝 Step 1: Generate Personal Access Token
echo.
echo 1. Go to: https://github.com/settings/tokens
echo 2. Click "Generate new token (classic)"
echo 3. Select scopes: repo, workflow, write:packages
echo 4. Copy the generated token
echo.
echo 📝 Step 2: Configure Git
echo.
echo 🔧 Quick setup commands:
echo    git config --global user.name "Your Name"
echo    git config --global user.email "your.email@example.com"
echo    git config --global credential.helper wincred
echo.
echo 📝 Step 3: Test authentication
echo    git push --dry-run origin main
echo    (Use your GitHub username and the token as password)
echo.
echo 🌐 Opening GitHub in browser...
start https://github.com/settings/tokens
echo.
echo ⏸️  Press any key after setting up your token...
pause
goto :test_auth

:gitlab_login
echo.
echo 🦊 GitLab Login Setup:
echo ========================================
echo.
echo 📝 Step 1: Generate Personal Access Token
echo.
echo 1. Go to: https://gitlab.com/-/profile/personal_access_tokens
echo 2. Click "Add new token"
echo 3. Select scopes: read_repository, write_repository, api
echo 4. Copy the generated token
echo.
echo 📝 Step 2: Configure Git
echo.
echo 🔧 Quick setup commands:
echo    git config --global user.name "Your Name"
echo    git config --global user.email "your.email@example.com"
echo    git config --global credential.helper wincred
echo.
echo 📝 Step 3: Test authentication
echo    git push --dry-run origin main
echo    (Use your GitLab username and the token as password)
echo.
echo 🌐 Opening GitLab in browser...
start https://gitlab.com/-/profile/personal_access_tokens
echo.
echo ⏸️  Press any key after setting up your token...
pause
goto :test_auth

:bitbucket_login
echo.
echo 🪣 Bitbucket Login Setup:
echo ========================================
echo.
echo 📝 Step 1: Generate App Password
echo.
echo 1. Go to: https://bitbucket.org/account/settings/app-passwords/
echo 2. Click "Create app password"
echo 3. Select permissions: Repositories (Read, Write)
echo 4. Copy the generated password
echo.
echo 📝 Step 2: Configure Git
echo.
echo 🔧 Quick setup commands:
echo    git config --global user.name "Your Name"
echo    git config --global user.email "your.email@example.com"
echo    git config --global credential.helper wincred
echo.
echo 📝 Step 3: Test authentication
echo    git push --dry-run origin main
echo    (Use your Bitbucket username and the app password as password)
echo.
echo 🌐 Opening Bitbucket in browser...
start https://bitbucket.org/account/settings/app-passwords/
echo.
echo ⏸️  Press any key after setting up your app password...
pause
goto :test_auth

:custom_login
echo.
echo 🔧 Custom Repository Login:
echo ========================================
echo.
echo 📝 Enter your repository URL:
set /p repo_url="Repository URL: "
echo.
echo 📝 Enter your username:
set /p username="Username: "
echo.
echo 📝 Enter your password/token:
set /p password="Password/Token: "
echo.
echo 🔧 Configuring Git...
git config --global user.name "%username%"
git config --global user.email "%username%@example.com"
git config --global credential.helper wincred
echo.
echo ✅ Git configured successfully!
echo.
echo 📝 Test authentication with:
echo    git push --dry-run origin main
echo    (Use your username and password/token)
echo.
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
    echo 🔧 Please try again:
    echo    • Run this script again and choose your platform
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
echo ❌ Invalid choice! Please enter 1-6.
goto :exit

:exit
echo.
echo 👋 Goodbye!
pause
