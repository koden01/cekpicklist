@echo off
REM Continue Release Script - Continue from where it left off
echo 🚀 Continue Release Process
echo ===========================

REM Get current version from version.txt
for /f "delims=" %%i in (version.txt) do set current_version=%%i
echo 📱 Current version: %current_version%

echo.
echo 🔨 Step 1: Building APK
echo ========================================

echo 🧹 Cleaning project...
.\gradlew clean

if %errorlevel% neq 0 (
    echo ⚠️ Clean failed, continuing with build...
) else (
    echo ✅ Clean completed successfully
)

echo.
echo 🔨 Building release APK...
.\gradlew assembleRelease -x test --no-daemon

if %errorlevel% neq 0 (
    echo ❌ Build failed!
    echo.
    echo 🔍 Debug information:
    echo    • Gradle wrapper exists: 
    if exist "gradlew.bat" (echo YES) else (echo NO)
    echo    • Build directory exists: 
    if exist "app\build" (echo YES) else (echo NO)
    echo    • Android SDK: 
    echo %ANDROID_HOME%
    pause
    exit /b 1
)

echo ✅ APK build completed

echo.
echo 📱 Step 2: Package APK
echo ========================================

REM Check if APK exists
if not exist "app\build\outputs\apk\release\app-release.apk" (
    echo ❌ APK file not found!
    echo 🔍 Debug information:
    echo    • Expected path: app\build\outputs\apk\release\app-release.apk
    echo    • Build outputs directory exists: 
    if exist "app\build\outputs" (echo YES) else (echo NO)
    echo    • APK directory exists: 
    if exist "app\build\outputs\apk" (echo YES) else (echo NO)
    echo    • Release directory exists: 
    if exist "app\build\outputs\apk\release" (echo YES) else (echo NO)
    echo    • Files in release directory:
    if exist "app\build\outputs\apk\release" dir "app\build\outputs\apk\release"
    pause
    exit /b 1
)

copy "app\build\outputs\apk\release\app-release.apk" "CekPicklist-v%current_version%-release.apk"

if %errorlevel% neq 0 (
    echo ❌ APK packaging failed!
    echo 🔍 Debug information:
    echo    • Source file exists: 
    if exist "app\build\outputs\apk\release\app-release.apk" (echo YES) else (echo NO)
    echo    • Target directory writable: 
    echo %CD%
    pause
    exit /b 1
)

echo ✅ APK packaged: CekPicklist-v%current_version%-release.apk

echo.
echo 📝 Step 3: Git Operations
echo ========================================

echo Adding all changes to git...
git add . >nul 2>&1

if %errorlevel% neq 0 (
    echo ❌ Git add failed!
    pause
    exit /b 1
)

echo ✅ Files added to staging

echo.
echo Committing changes...
git commit -m "🚀 Release v%current_version% - Fix release script cleaning project issue"

if %errorlevel% neq 0 (
    echo ❌ Git commit failed!
    echo.
    echo 💡 This might happen if there are no changes to commit.
    echo Check git status manually: git status
    pause
    exit /b 1
)

echo ✅ Changes committed

echo.
echo Creating tag v%current_version%...
git tag "v%current_version%"

if %errorlevel% neq 0 (
    echo ❌ Git tag failed!
    echo.
    echo 💡 This might happen if the tag already exists.
    echo Check existing tags: git tag --list
    pause
    exit /b 1
)

echo ✅ Tag v%current_version% created

echo.
echo 📤 Step 4: Push to GitHub
echo ========================================

REM Check current branch
for /f "tokens=*" %%i in ('git branch --show-current 2^>nul') do set current_branch=%%i
echo 🔍 Current branch: %current_branch%

REM Try to push to current branch first
echo 📤 Pushing commits to %current_branch%...
git push origin %current_branch%

if %errorlevel% neq 0 (
    echo ⚠️ Push to %current_branch% failed, trying master...
    git push origin master
    
    if %errorlevel% neq 0 (
        echo ⚠️ Push to master failed, trying main...
        git push origin main
        
        if %errorlevel% neq 0 (
            echo ❌ All push attempts failed!
            echo.
            echo 🔍 Debug information:
            echo    • Current branch: %current_branch%
            echo    • Remote branches:
            git branch -r
            echo.
            echo 💡 Possible solutions:
            echo    1. Check internet connection
            echo    2. Verify GitHub authentication
            echo    3. Run: git config --global credential.helper manager
            echo    4. Check repository permissions
            echo.
            pause
            exit /b 1
        ) else (
            echo ✅ Pushed to main branch
        )
    ) else (
        echo ✅ Pushed to master branch
    )
) else (
    echo ✅ Pushed to %current_branch% branch
)

echo.
echo 🏷️ Pushing tags...
git push origin --tags

if %errorlevel% neq 0 (
    echo ❌ Push tags failed!
    echo.
    echo 💡 Try manual push:
    echo    git push origin v%current_version%
    pause
    exit /b 1
)

echo ✅ Tags pushed successfully

echo.
echo 🎉 Release Summary
echo =======================================
echo 📱 APK Information:
echo    • File: CekPicklist-v%current_version%-release.apk
echo    • Version: %current_version%
echo    • Date: %date% %time%
echo.
echo 📝 Git Information:
echo    • Commit: 🚀 Release v%current_version%
echo    • Tag: v%current_version%
echo    • Push: Pushed to origin and tags
echo    • README: Updated with version %current_version%
echo.
echo ✅ Complete release workflow finished successfully!
echo =======================================

REM Cleanup temporary files
del version.txt 2>nul

echo.
echo 🚀 Release v%current_version% completed successfully!
echo Check your GitHub repository for the new release.
echo.

pause
