@echo off
REM Git Simple Setup Script untuk Cek Picklist
REM Script ini akan membantu setup Git authentication step-by-step

echo 🔧 Cek Picklist - Git Simple Setup
echo ===================================

echo.
echo 🔧 This script will help you setup Git authentication step-by-step
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
echo 📝 Step 1: Configure Git user information
echo ========================================
echo.
echo Please enter your Git user information:
echo.
set /p git_name="Enter your name: "
set /p git_email="Enter your email: "

echo.
echo 🔧 Configuring Git...
git config --global user.name "%git_name%"
git config --global user.email "%git_email%"
git config --global credential.helper wincred

echo ✅ Git configured successfully!
echo.

echo 📝 Step 2: Configure Git remote
echo ========================================
echo.
echo Please enter your repository URL:
echo Examples:
echo   • GitHub: https://github.com/username/repository.git
echo   • GitLab: https://gitlab.com/username/repository.git
echo   • Bitbucket: https://bitbucket.org/username/repository.git
echo.
set /p repo_url="Enter repository URL: "

echo.
echo 🔧 Configuring Git remote...
git remote remove origin 2>nul
git remote add origin "%repo_url%"

echo ✅ Git remote configured successfully!
echo.

echo 📝 Step 3: Test authentication
echo ========================================
echo.
echo 🔐 Testing authentication...
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
echo ⏸️  Press any key to continue with authentication test...
pause

echo.
echo 🔐 Testing authentication...
git ls-remote origin
if %errorlevel% neq 0 (
    echo ❌ Authentication failed!
    echo.
    echo 🔧 Please try again:
    echo    1. Make sure your credentials are correct
    echo    2. Check if you have access to the repository
    echo    3. Try generating a new Personal Access Token
    echo.
    echo 📝 Test manually with:
    echo    git push --dry-run origin main
    echo.
    echo ⏸️  Press any key to exit...
    pause
    exit /b 1
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
    echo ✅ Git setup completed successfully!
    exit /b 0
)
