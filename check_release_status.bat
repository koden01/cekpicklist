@echo off
REM Check Release Status Script
echo 🔍 Checking Release Status
echo ===========================

echo.
echo 📋 Step 1: Check APK files
echo ---------------------------
echo Checking build output directory...
if exist "app\build\outputs\apk\release\app-release.apk" (
    echo ✅ APK found in build directory
    dir "app\build\outputs\apk\release\app-release.apk"
) else (
    echo ❌ APK not found in build directory
)

echo.
echo Checking root directory for packaged APK...
for %%f in (CekPicklist-v*.apk) do (
    echo ✅ Found: %%f
    dir "%%f"
)

echo.
echo 📋 Step 2: Check Git status
echo ---------------------------
git status --short

echo.
echo 📋 Step 3: Check version
echo -------------------------
if exist version.txt (
    echo Current version from version.txt:
    type version.txt
) else (
    echo version.txt not found
)

echo.
echo 📋 Step 4: Check recent commits
echo --------------------------------
git log --oneline -3

echo.
echo 📋 Step 5: Check tags
echo ---------------------
git tag --list

echo.
echo 📋 Step 6: Manual release completion
echo -------------------------------------
echo If release is incomplete, you can manually complete it:

echo.
echo 1. Package APK (if not done):
if exist "app\build\outputs\apk\release\app-release.apk" (
    for /f "delims=" %%i in (version.txt) do set current_version=%%i
    if not exist "CekPicklist-v%current_version%-release.apk" (
        echo Copying APK to root directory...
        copy "app\build\outputs\apk\release\app-release.apk" "CekPicklist-v%current_version%-release.apk"
        echo ✅ APK packaged
    ) else (
        echo ✅ APK already packaged
    )
) else (
    echo ❌ No APK to package
)

echo.
echo 2. Git operations (if not done):
git add .
git commit -m "🚀 Release v4.4.2 - Fix btnRefreshModal reference error"
git tag v4.4.2
git push origin master
git push origin --tags

echo.
echo 🎉 Release Status Check Complete!
echo =================================
pause
