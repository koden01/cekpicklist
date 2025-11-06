@echo off
REM Working Release Script untuk Cek Picklist
REM Script ini akan: Auto versioning, Update README, Build APK, dan Git operations

echo 🚀 Cek Picklist - Working Release Script
echo =======================================

REM Get version type from user
echo.
echo Select version type:
echo 1. Patch (4.0.0 → 4.0.1)
echo 2. Minor (4.0.0 → 4.1.0)
echo 3. Major (4.0.0 → 5.0.0)
echo.
set /p choice="Enter choice (1-3): "

if "%choice%"=="1" set version_type=patch
if "%choice%"=="2" set version_type=minor
if "%choice%"=="3" set version_type=major
if "%choice%"=="" set version_type=patch

echo.
echo 📝 Enter release notes (optional, press Enter to skip):
set /p release_notes="Release notes: "

echo.
echo GIT AUTHENTICATION CHECK
echo ========================================
echo.
echo IMPORTANT: Git authentication is required for release process
echo.
echo Git Login Options:
echo    - .\git_simple_setup.bat   - Step-by-step setup (Recommended)
echo    - .\git_fix_login.bat      - Fix authentication issues
echo    - .\git_debug_login.bat    - Debug authentication problems
echo    - .\git_auto_login.bat     - Auto-detect platform and open browser
echo    - .\git_browser_login.bat  - Manual platform selection
echo.
echo Quick setup commands:
echo    git config --global user.name "Your Name"
echo    git config --global user.email "your.email@example.com"
echo    git config --global credential.helper wincred
echo.
echo Test your authentication with:
echo    git push --dry-run origin main
echo.
echo Press 'S' for simple setup, 'F' for fix login, 'D' for debug, 'B' for browser login, or any other key to continue...
set /p login_choice="Choice (S/F/D/B for login help, Enter to continue): "
if /i "%login_choice%"=="S" (
    echo Opening simple setup...
    .\git_simple_setup.bat
    if %errorlevel% neq 0 (
        echo Simple setup failed!
        pause
        exit /b 1
    )
) else (
    if /i "%login_choice%"=="F" (
        echo Opening fix login...
        .\git_fix_login.bat
        if %errorlevel% neq 0 (
            echo Fix login failed!
            pause
            exit /b 1
        )
    ) else (
        if /i "%login_choice%"=="D" (
            echo Opening debug login...
            .\git_debug_login.bat
            if %errorlevel% neq 0 (
                echo Debug login failed!
                pause
                exit /b 1
            )
        ) else (
            if /i "%login_choice%"=="B" (
                echo Opening browser login...
                rem Ensure Git Credential Manager is configured to enable browser-based OAuth
                git config --global credential.helper manager-core >nul 2>&1
                rem Invoke PowerShell helper to open the correct platform page and guide login
                powershell.exe -ExecutionPolicy Bypass -NoProfile -File "git_browser_helper.ps1"
                if %errorlevel% neq 0 (
                    echo Browser login helper failed!
                    pause
                    exit /b 1
                )
            ) else (
                echo Continuing with current authentication...
            )
        )
    )
)
echo.

REM Check Git authentication before proceeding (simplified)
echo Verifying Git authentication...
git remote -v >nul 2>&1 || goto auth_fail

REM Prefer Git Credential Manager (browser OAuth) on Windows
git config --global credential.helper manager-core >nul 2>&1

REM Test Git authentication
echo Testing Git authentication...
git ls-remote origin >nul 2>&1 || goto auth_fail

echo Git authentication verified successfully!
goto auth_ok

:auth_fail
echo Git authentication failed!
echo.
echo Please setup Git authentication:
echo    - Run: .\git_simple_setup.bat
echo    - Or configure manually with Personal Access Token/SSH
echo    - Or choose Browser Login (option B) to sign in via OAuth
echo.
echo Test authentication with:
echo    git push --dry-run origin main
echo.
echo Press any key to exit and setup Git authentication...
pause
exit /b 1

:auth_ok
echo.
echo 🚀 Starting working release process...
echo.

REM Step 1: Auto Versioning
echo 📋 Step 1: Auto Versioning...
REM Ensure version_type has a default when running non-interactively
if "%version_type%"=="" set "version_type=patch"
powershell.exe -ExecutionPolicy Bypass -NoProfile -Command ".\simple_version.ps1 -VersionType '%version_type%'"

if %errorlevel% neq 0 (
    echo ❌ Versioning failed!
    echo.
    echo 🔍 Debug information:
    echo    • PowerShell execution policy: 
    powershell.exe -Command "Get-ExecutionPolicy"
    echo    • Current directory: %CD%
    echo    • Script exists: 
    if exist "simple_version.ps1" (echo YES) else (echo NO)
    echo    • Build.gradle.kts exists: 
    if exist "app\build.gradle.kts" (echo YES) else (echo NO)
    pause
    exit /b 1
)

REM Get new version from file
for /f "delims=" %%i in (version.txt) do set new_version=%%i
echo ✅ New version: %new_version%

if %errorlevel% neq 0 (
    echo ❌ Versioning failed!
    pause
    exit /b 1
)

REM Step 2: Update README
echo 📝 Step 2: Update README...
powershell.exe -ExecutionPolicy Bypass -NoProfile -Command ".\update_readme.ps1 -Version '%new_version%'"

if %errorlevel% neq 0 (
    echo ❌ README update failed!
    echo.
    echo 🔍 Debug information:
    echo    • README.md exists: 
    if exist "README.md" (echo YES) else (echo NO)
    echo    • Update script exists: 
    if exist "update_readme.ps1" (echo YES) else (echo NO)
    echo    • Version parameter: %new_version%
    pause
    exit /b 1
)

REM Step 3: Build APK
echo 🔨 Step 3: Building APK...

REM Check if gradlew exists
if not exist "gradlew.bat" (
    echo ❌ gradlew.bat not found!
    echo 🔍 Debug information:
    echo    • Current directory: %CD%
    echo    • Files in directory:
    dir /b
    pause
    exit /b 1
)

REM Clean before build
echo 🧹 Cleaning project...
call .\gradlew.bat clean

if %errorlevel% neq 0 (
    echo ⚠️ Clean failed, continuing with build...
)

echo.
echo 🔨 Building release APK...
echo ⏰ This will take 3-5 minutes, please wait...
echo 📝 Build progress will be shown below:
echo ========================================
echo.

REM Build APK - Show progress di console
call .\gradlew.bat assembleRelease -x test

REM Check build result
if %errorlevel% neq 0 (
    echo ❌ Build failed!
    echo.
    echo 🔍 Build errors (last 30 lines):
    echo =======================================
    powershell -Command Get-Content build_log.txt -Tail 30
    echo =======================================
    echo.
    echo 📄 Full build log saved to: build_log.txt
    echo.
    echo 💡 Common fixes:
    echo    • Check for missing imports in Kotlin files
    echo    • Run: .\gradlew.bat clean assembleRelease --stacktrace
    echo    • Check Android SDK is properly configured
    echo.
    pause
    exit /b 1
)

echo ✅ APK build completed successfully!

REM Step 4: Copy APK
echo 📱 Step 4: Packaging APK...

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

copy "app\build\outputs\apk\release\app-release.apk" "CekPicklist-v%new_version%-release.apk"

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

echo ✅ APK packaged: CekPicklist-v%new_version%-release.apk

REM Step 5: Git Operations
echo.
echo 📝 Step 5: Git Operations...
echo.

REM Check if there are changes to commit
git diff --quiet HEAD
if %errorlevel% equ 0 (
    echo ℹ️ No changes detected, checking staged files...
    git diff --cached --quiet
    if %errorlevel% equ 0 (
        echo ⚠️ No changes to commit
    )
)

echo 📝 Adding all changes...
git add -A

if %errorlevel% neq 0 (
    echo ❌ Git add failed!
    pause
    exit /b 1
)

echo 📝 Creating commit...
REM Use appropriate commit message based on whether release notes were provided
if "%release_notes%"=="" (
    git commit -m "🚀 Release v%new_version%"
) else (
    git commit -m "🚀 Release v%new_version% - %release_notes%"
)

if %errorlevel% neq 0 (
    echo ⚠️ Git commit failed or nothing to commit
    echo Checking if APK file is tracked...
    git status
)

echo 📝 Creating git tag...
REM Create annotated tag only if it does not already exist
git rev-parse -q --verify "refs/tags/v%new_version%" >nul 2>&1
if %errorlevel% neq 0 (
    git tag -a "v%new_version%" -m "Release v%new_version%"
    echo ✅ Tag v%new_version% created
) else (
    echo ℹ️ Tag v%new_version% already exists, skipping tag creation.
)

echo ✅ Git operations completed

REM Step 6: Push to remote
echo.
echo 📤 Step 6: Pushing to remote...
echo.

REM Check current branch
for /f "tokens=*" %%i in ('git branch --show-current 2^>nul') do set current_branch=%%i
echo 🔍 Current branch: %current_branch%
echo.

REM Check if there's something to push
git cherry -v origin/%current_branch% 2>nul | find "+" >nul
if %errorlevel% neq 0 (
    echo ℹ️ No new commits to push
) else (
    echo 📤 Pushing commits to origin/%current_branch%...
)

REM Try to push to current branch first, then fallback to master/main
echo 📤 Pushing to origin/%current_branch%...
git push origin %current_branch%

if %errorlevel% neq 0 (
    echo ⚠️ Push to %current_branch% failed, trying alternative branches...
    git push origin master 2>nul
    
    if %errorlevel% neq 0 (
        git push origin main 2>nul
        
        if %errorlevel% neq 0 (
            echo ❌ All push attempts failed!
            echo.
            echo 🔍 Debug information:
            echo    • Current branch: %current_branch%
            echo    • Remote branches:
            git branch -r
            echo.
            echo 💡 Possible fixes:
            echo    • Check your Git authentication
            echo    • Verify remote repository is accessible
            echo    • Try manually: git push origin %current_branch%
            echo.
            pause
            exit /b 1
        ) else (
            echo ✅ Pushed to origin/main
        )
    ) else (
        echo ✅ Pushed to origin/master
    )
) else (
    echo ✅ Pushed to origin/%current_branch%
)

echo.
echo 📤 Pushing tags...
git push origin --tags

if %errorlevel% neq 0 (
    echo ⚠️ Push tags failed (tags might already exist on remote)
) else (
    echo ✅ Tags pushed successfully
)

echo.
echo ✅ Push to remote completed

REM Step 7: Create GitHub Release
echo.
echo 📦 Step 7: Create GitHub Release...
echo.

REM Check if GitHub CLI is installed (with PATH refresh fallback)
where gh >nul 2>&1
if %errorlevel% neq 0 (
    REM Try to refresh PATH and check again (for freshly installed gh CLI)
    powershell -Command "$env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path','User'); $null = Get-Command gh -ErrorAction Stop; exit 0" >nul 2>&1
    if %errorlevel% neq 0 (
        echo ⚠️ GitHub CLI not found - Skipping GitHub Release creation
        echo.
        echo 💡 To enable automated GitHub Release:
        echo    • Install GitHub CLI: winget install --id GitHub.cli
        echo    • Restart PowerShell/Terminal after installation
        echo    • Or manually create release at: https://github.com/koden01/cekpicklist/releases/new
        echo.
        goto skip_github_release
    ) else (
        echo ℹ️ GitHub CLI found after PATH refresh
    )
)

echo ✅ GitHub CLI found
echo.

REM Check if already authenticated (refresh PATH first if needed)
where gh >nul 2>&1
if %errorlevel% neq 0 (
    powershell -Command "$env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path','User')" >nul 2>&1
)

gh auth status >nul 2>&1
if %errorlevel% neq 0 (
    echo ⚠️ Not authenticated with GitHub - Skipping GitHub Release creation
    echo.
    echo 💡 To authenticate:
    echo    • Run: gh auth login
    echo    • Or run: .\install_github_cli.bat (will guide through auth)
    echo.
    goto skip_github_release
)

echo ✅ Authenticated with GitHub
echo.

REM Check if release already exists
echo 🔍 Checking if release v%new_version% already exists...
gh release view "v%new_version%" >nul 2>&1
if %errorlevel% equ 0 (
    echo ⚠️ Release v%new_version% already exists!
    echo.
    set /p overwrite_release="Delete and recreate release? (y/N): "
    if /i "%overwrite_release%"=="Y" (
        echo Deleting existing release...
        gh release delete "v%new_version%" --yes
        if %errorlevel% neq 0 (
            echo ❌ Failed to delete existing release
            goto skip_github_release
        )
        echo ✅ Deleted existing release
    ) else (
        echo Skipping GitHub Release creation
        goto skip_github_release
    )
)

echo.
echo 📦 Creating GitHub Release v%new_version%...
echo.

REM Create release notes
set "release_title=v%new_version%"
if not "%release_notes%"=="" (
    set "release_title=v%new_version% - %release_notes%"
)

REM Create release notes using PowerShell (safer untuk special characters)
powershell -Command "$notes = @'^
## 🎉 Release v%new_version%^

### 📝 Release Notes^

%release_notes%^

### 📱 Download & Install^

Download APK file below and install directly on your device.^

### 🔄 Auto Update^

The app will automatically detect this update and offer direct download & install from within the app!^

### 📊 Build Information^

- Version Name: %new_version%^
- Build Date: %date% %time%^
- Branch: %current_branch%^

---^

Full Changelog: https://github.com/koden01/cekpicklist/compare/v5.1.4...v%new_version%^
'@; $notes | Out-File -FilePath 'release_notes_temp.md' -Encoding UTF8"

REM Create release with APK
gh release create "v%new_version%" "CekPicklist-v%new_version%-release.apk" --title "%release_title%" --notes-file release_notes_temp.md --latest

if %errorlevel% equ 0 (
    echo.
    echo ✅ GitHub Release created successfully!
    echo.
    echo 🔗 View release: https://github.com/koden01/cekpicklist/releases/tag/v%new_version%
    echo 📥 Download URL: https://github.com/koden01/cekpicklist/releases/download/v%new_version%/CekPicklist-v%new_version%-release.apk
    echo.
    echo 🔄 Auto-update in app will now work!
    echo.
) else (
    echo ❌ Failed to create GitHub Release
    echo.
    echo 💡 You can create it manually:
    echo    • Go to: https://github.com/koden01/cekpicklist/releases/new
    echo    • Tag: v%new_version%
    echo    • Upload: CekPicklist-v%new_version%-release.apk
    echo.
)

:skip_github_release

REM Step 8: Summary
echo.
echo ========================================================================
echo 🎉 RELEASE COMPLETED SUCCESSFULLY!
echo ========================================================================
echo.
echo 📱 APK Information:
echo    • File: CekPicklist-v%new_version%-release.apk
echo    • Version: %new_version%
echo    • Build Date: %date% %time%
for %%A in ("CekPicklist-v%new_version%-release.apk") do set apk_size=%%~zA
echo    • File Size: %apk_size% bytes
echo.
echo 📝 Git Information:
echo    • Commit Message: 🚀 Release v%new_version% %release_notes%
echo    • Tag: v%new_version%
echo    • Branch: %current_branch%
echo    • Pushed to: origin/%current_branch%
echo    • README: Updated with version %new_version%
echo.
echo 📦 GitHub Release:
where gh >nul 2>&1
if %errorlevel% equ 0 (
    echo    • Status: Created automatically ✅
    echo    • URL: https://github.com/koden01/cekpicklist/releases/tag/v%new_version%
    echo    • Auto-update: Ready ✅
) else (
    echo    • Status: Manual creation needed ⚠️
    echo    • URL: https://github.com/koden01/cekpicklist/releases/new
    echo    • Note: Auto-update won't work until release is created
)
echo.
echo 📂 Files Generated:
echo    • CekPicklist-v%new_version%-release.apk (in project root)
echo    • build_log.txt (build details)
echo.
echo 🔗 Next Steps:
echo    • Install APK on device for testing
echo    • Verify changes on GitHub repository
echo    • Test auto-update from previous version
echo    • Distribute APK to users
echo.
echo ========================================================================
echo ✅ Complete release workflow finished successfully!
echo ========================================================================
echo.

REM Show APK location
echo 📍 APK Location: %CD%\CekPicklist-v%new_version%-release.apk
echo.

REM Cleanup temporary files
echo 🧹 Cleaning up temporary files...
del version.txt 2>nul
del release_notes_temp.md 2>nul

REM Ask if user wants to keep build log
set /p keep_log="Keep build_log.txt for reference? (Y/N, default: N): "
if /i "%keep_log%"=="Y" (
    echo ℹ️ Build log kept: build_log.txt
) else (
    del build_log.txt 2>nul
    echo ✅ Build log cleaned up
)

echo.
echo 👋 Press any key to exit...
pause >nul
