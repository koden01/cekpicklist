@echo off
REM Script untuk install GitHub CLI
REM Untuk full automation release process

echo 🚀 GitHub CLI Installer
echo ========================
echo.

REM Check if gh already installed
where gh >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ GitHub CLI already installed!
    gh --version
    echo.
    
    REM Check authentication
    echo Checking authentication...
    gh auth status >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✅ Already authenticated with GitHub!
        echo.
        echo 🎉 You're all set! Run release_working.bat for full automation.
        pause
        exit /b 0
    ) else (
        echo ⚠️ Not authenticated yet
        goto auth_setup
    )
)

echo GitHub CLI not found. Installing...
echo.

REM Check if winget is available
where winget >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ winget not found!
    echo.
    echo GitHub CLI installation methods:
    echo.
    echo Option 1: Manual Download (Recommended)
    echo    1. Download from: https://cli.github.com/
    echo    2. Run installer (MSI file)
    echo    3. Follow installation wizard
    echo.
    echo Option 2: Using Chocolatey
    echo    choco install gh
    echo.
    echo Option 3: Using Scoop
    echo    scoop install gh
    echo.
    pause
    exit /b 1
)

echo Installing GitHub CLI using winget...
echo.
winget install --id GitHub.cli --accept-package-agreements --accept-source-agreements

if %errorlevel% neq 0 (
    echo ❌ Installation failed!
    echo.
    echo Please install manually from: https://cli.github.com/
    pause
    exit /b 1
)

echo.
echo ✅ GitHub CLI installed successfully!
echo.

REM Reload PATH environment (might need to restart terminal)
echo ⚠️ You might need to restart your terminal/PowerShell for 'gh' command to work.
echo.

:auth_setup
echo.
echo 📝 Step 2: Authenticate with GitHub
echo ====================================
echo.
echo Please authenticate with GitHub to enable release creation.
echo.
set /p do_auth="Authenticate now? (Y/n): "
if /i "%do_auth%"=="N" (
    echo Skipping authentication. Run 'gh auth login' later.
    pause
    exit /b 0
)

echo.
echo Starting GitHub authentication...
echo.
echo 💡 Tips:
echo    • Choose "GitHub.com" when prompted
echo    • Choose "HTTPS" as preferred protocol
echo    • Choose "Login with a web browser" (easiest)
echo    • Follow browser instructions
echo.
pause

gh auth login

if %errorlevel% equ 0 (
    echo.
    echo ✅ Authentication successful!
    echo.
    echo 🎉 Setup complete! You can now run:
    echo    • .\release_working.bat - For full automated release
    echo.
) else (
    echo ❌ Authentication failed!
    echo.
    echo Please try again with: gh auth login
    echo.
)

pause

