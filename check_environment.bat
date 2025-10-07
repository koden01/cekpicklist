@echo off
REM Environment Check Script - Check terminal and environment requirements
echo 🔍 Environment Check Script
echo ============================

echo.
echo 📋 Step 1: Terminal Information
echo --------------------------------
echo Terminal Type: %COMSPEC%
echo Current Directory: %CD%
echo User: %USERNAME%
echo Computer: %COMPUTERNAME%

echo.
echo 📋 Step 2: Environment Variables
echo --------------------------------
echo ANDROID_HOME: %ANDROID_HOME%
if "%ANDROID_HOME%"=="" (
    echo ❌ ANDROID_HOME not set!
) else (
    echo ✅ ANDROID_HOME is set
)

echo.
echo JAVA_HOME: %JAVA_HOME%
if "%JAVA_HOME%"=="" (
    echo ❌ JAVA_HOME not set!
) else (
    echo ✅ JAVA_HOME is set
)

echo.
echo PATH (first 200 chars): %PATH:~0,200%...

echo.
echo 📋 Step 3: Tools Availability
echo ------------------------------
echo Checking Git...
git --version >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Git is available
    git --version
) else (
    echo ❌ Git not found in PATH!
)

echo.
echo Checking Java...
java -version >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Java is available
    java -version 2>&1 | findstr "version"
) else (
    echo ❌ Java not found in PATH!
)

echo.
echo Checking Gradle Wrapper...
if exist "gradlew.bat" (
    echo ✅ Gradle wrapper found
    .\gradlew --version >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✅ Gradle wrapper is working
    ) else (
        echo ❌ Gradle wrapper not working
    )
) else (
    echo ❌ Gradle wrapper not found!
)

echo.
echo 📋 Step 4: PowerShell Configuration
echo ------------------------------------
echo Checking PowerShell execution policy...
powershell -Command "Get-ExecutionPolicy" >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ PowerShell is available
    powershell -Command "Get-ExecutionPolicy"
) else (
    echo ❌ PowerShell not available or blocked
)

echo.
echo Testing PowerShell script execution...
powershell -Command "Write-Host 'PowerShell test successful'" >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ PowerShell script execution works
) else (
    echo ❌ PowerShell script execution blocked
    echo 💡 Fix: Run as Administrator and set execution policy
)

echo.
echo 📋 Step 5: Project Structure
echo -----------------------------
echo Checking project files...
if exist "app\build.gradle.kts" (
    echo ✅ app\build.gradle.kts found
) else (
    echo ❌ app\build.gradle.kts not found!
)

if exist "gradlew.bat" (
    echo ✅ gradlew.bat found
) else (
    echo ❌ gradlew.bat not found!
)

if exist "simple_version.ps1" (
    echo ✅ simple_version.ps1 found
) else (
    echo ❌ simple_version.ps1 not found!
)

if exist "update_readme.ps1" (
    echo ✅ update_readme.ps1 found
) else (
    echo ❌ update_readme.ps1 not found!
)

if exist "release_working.bat" (
    echo ✅ release_working.bat found
) else (
    echo ❌ release_working.bat not found!
)

echo.
echo 📋 Step 6: Git Configuration
echo -----------------------------
echo Checking Git configuration...
git config --global user.name >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Git user.name is configured
    git config --global user.name
) else (
    echo ❌ Git user.name not configured
)

git config --global user.email >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Git user.email is configured
    git config --global user.email
) else (
    echo ❌ Git user.email not configured
)

echo.
echo Testing Git remote access...
git ls-remote origin >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Git remote access works
) else (
    echo ❌ Git remote access failed
    echo 💡 Check authentication and network
)

echo.
echo 🎉 Environment Check Complete!
echo ===============================
echo Check the results above for any issues.
echo Fix any ❌ items before running release script.
echo ===============================

pause
