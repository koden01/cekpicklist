@echo off
REM Check Git Remote Script untuk mengecek konfigurasi remote repository

echo 🔍 Cek Picklist - Check Git Remote Script
echo =======================================

echo.
echo 🔍 Step 1: Checking if this is a Git repository...
if not exist ".git" (
    echo ❌ This is not a Git repository!
    echo.
    echo 💡 Solutions:
    echo    1. Initialize Git repository:
    echo       git init
    echo       git remote add origin https://github.com/koden01/cekpicklist.git
    echo       git add .
    echo       git commit -m "Initial commit"
    echo       git push -u origin master
    echo.
    pause
    exit /b 1
) else (
    echo ✅ This is a Git repository
)

echo.
echo 🔍 Step 2: Checking Git remote configuration...
git remote -v

if %errorlevel% neq 0 (
    echo ❌ No remote configured!
    echo.
    echo 💡 Add remote repository:
    echo    git remote add origin https://github.com/koden01/cekpicklist.git
    echo.
    pause
    exit /b 1
)

echo.
echo 🔍 Step 3: Checking remote repository access...
git ls-remote origin

if %errorlevel% neq 0 (
    echo ❌ Cannot access remote repository!
    echo.
    echo 💡 Possible issues:
    echo    1. Internet connection problem
    echo    2. GitHub authentication issue
    echo    3. Repository permissions
    echo    4. Repository URL incorrect
    echo.
    echo 💡 Solutions:
    echo    1. Check internet connection
    echo    2. Run: .\git_simple_setup.bat
    echo    3. Verify repository URL
    echo.
    pause
    exit /b 1
) else (
    echo ✅ Remote repository accessible
)

echo.
echo 🔍 Step 4: Checking local branches...
git branch -a

echo.
echo 🔍 Step 5: Checking current branch status...
git status

echo.
echo 🔍 Step 6: Checking commit history...
git log --oneline -10

echo.
echo 🔍 Step 7: Checking tags...
git tag --list

echo.
echo 📋 Git Remote Status Summary:
echo =======================================
echo ✅ Git repository initialized
echo ✅ Remote configured
echo ✅ Remote accessible
echo.
echo 💡 Ready for Git operations!
echo =======================================

pause
