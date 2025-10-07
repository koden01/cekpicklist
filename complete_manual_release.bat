@echo off
REM Complete Manual Release Script
echo 🚀 Complete Manual Release
echo ==========================

REM Get current version from version.txt
for /f "delims=" %%i in (version.txt) do set current_version=%%i
echo 📱 Current version: %current_version%

echo.
echo 📱 Step 1: Package APK
echo ========================================

REM Check if APK exists
if exist "app\build\outputs\apk\release\app-release.apk" (
    echo ✅ APK found in build directory
    copy "app\build\outputs\apk\release\app-release.apk" "CekPicklist-v%current_version%-release.apk"
    echo ✅ APK packaged: CekPicklist-v%current_version%-release.apk
) else (
    echo ❌ APK not found in build directory
    pause
    exit /b 1
)

echo.
echo 📝 Step 2: Git Operations
echo ========================================

echo Adding all changes to git...
git add .

echo.
echo Committing changes...
git commit -m "🚀 Release v%current_version% - Fix release script cleaning project issue and add refresh button to modal"

echo.
echo Creating tag v%current_version%...
git tag "v%current_version%"

echo.
echo 📤 Step 3: Push to GitHub
echo ========================================

echo Pushing commits...
git push origin master

echo.
echo Pushing tags...
git push origin --tags

echo.
echo 🎉 Release Complete!
echo =======================================
echo 📱 APK: CekPicklist-v%current_version%-release.apk
echo 📝 Version: %current_version%
echo 🏷️ Tag: v%current_version%
echo 📤 Push: Completed
echo =======================================

REM Cleanup
del version.txt 2>nul

pause
