@echo off
REM Git Fix Login Script untuk Cek Picklist
REM Script ini akan membantu fix masalah Git authentication

echo 🔧 Cek Picklist - Git Fix Login
echo ================================

echo.
echo 🔧 This script will help you fix Git authentication issues
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
echo 🔍 Current Git configuration:
echo ========================================
echo User Name: 
git config --global user.name
echo User Email: 
git config --global user.email
echo Credential Helper: 
git config --global credential.helper
echo Remote URL: 
git remote get-url origin 2>nul
echo ========================================

echo.
echo 🔧 Fix Options:
echo ========================================
echo.
echo 1. Clear all stored credentials and reconfigure
echo 2. Change remote URL
echo 3. Test authentication with verbose output
echo 4. Manual authentication test
echo 5. Exit
echo.
set /p choice="Enter your choice (1-5): "

if "%choice%"=="1" goto :clear_credentials
if "%choice%"=="2" goto :change_remote
if "%choice%"=="3" goto :test_auth
if "%choice%"=="4" goto :manual_test
if "%choice%"=="5" goto :exit
goto :invalid_choice

:clear_credentials
echo.
echo 🔧 Clearing all stored credentials...
echo ========================================
echo.
echo Clearing credential helper...
git config --global --unset credential.helper
echo.
echo Setting new credential helper...
git config --global credential.helper wincred
echo.
echo Clearing stored credentials...
git config --global --unset credential.helper
git config --global credential.helper wincred
echo.
echo ✅ Credentials cleared!
echo.
echo 📝 Please reconfigure your Git user information:
set /p git_name="Enter your name: "
set /p git_email="Enter your email: "
git config --global user.name "%git_name%"
git config --global user.email "%git_email%"
echo.
echo ✅ Git reconfigured!
echo.
goto :test_auth

:change_remote
echo.
echo 🔧 Changing remote URL...
echo ========================================
echo.
echo Current remote URL: 
git remote get-url origin 2>nul
echo.
echo Please enter new repository URL:
echo Examples:
echo   • GitHub: https://github.com/username/repository.git
echo   • GitLab: https://gitlab.com/username/repository.git
echo   • Bitbucket: https://bitbucket.org/username/repository.git
echo.
set /p repo_url="Enter new repository URL: "
echo.
echo 🔧 Updating remote URL...
git remote remove origin 2>nul
git remote add origin "%repo_url%"
echo.
echo ✅ Remote URL updated!
echo.
goto :test_auth

:test_auth
echo.
echo 🔐 Testing authentication...
echo ========================================
echo.
echo 🔐 Testing authentication with verbose output...
git ls-remote origin
if %errorlevel% neq 0 (
    echo ❌ Authentication failed!
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
    echo ✅ Authentication successful!
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

:manual_test
echo.
echo 🔐 Manual authentication test...
echo ========================================
echo.
echo ⚠️  IMPORTANT: You will be prompted for your credentials
echo    • Username: Your Git username
echo    • Password: Your Personal Access Token or App Password
echo.
echo 📝 For different platforms:
echo    • GitHub: Use Personal Access Token as password
echo    • GitLab: Use Personal Access Token as password
echo    • Bitbucket: Use App Password as password
echo.
echo ⏸️  Press any key to continue with manual test...
pause
echo.
echo 🔐 Testing authentication...
git push --dry-run origin main
if %errorlevel% neq 0 (
    echo ❌ Manual authentication failed!
    echo.
    echo 🔧 Please try again:
    echo    1. Make sure your credentials are correct
    echo    2. Check if you have access to the repository
    echo    3. Try generating a new Personal Access Token
    echo.
    echo ⏸️  Press any key to exit...
    pause
    exit /b 1
) else (
    echo ✅ Manual authentication successful!
    echo.
    echo 🎉 You're ready to run release scripts!
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
goto :exit

:invalid_choice
echo ❌ Invalid choice! Please enter 1-5.
goto :exit

:exit
echo.
echo 👋 Goodbye!
pause
