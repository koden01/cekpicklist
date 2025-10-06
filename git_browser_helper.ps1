param(
    [Parameter(Mandatory=$false)]
    [string]$Platform = "auto"
)

# ASCII-only Git Browser Helper to avoid parsing issues
Write-Host "Cek Picklist - Git Browser Helper" -ForegroundColor Cyan
Write-Host "=================================" -ForegroundColor Cyan
Write-Host ""

try {
    # Check Git
    $gitVersion = git --version 2>$null
    if (-not $gitVersion) {
        Write-Host "Git is not installed. Install from: https://git-scm.com/downloads" -ForegroundColor Red
        exit 1
    }
    Write-Host "Git detected: $gitVersion" -ForegroundColor Green

    # Remote URL
    $remoteUrl = git remote get-url origin 2>$null
    if (-not $remoteUrl) {
        Write-Host "No Git remote configured. Run: git remote add origin <url>" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "Remote: $remoteUrl" -ForegroundColor Green
    Write-Host ""

    # Detect platform
    $platformName = "Custom"
    $tokenUrl = ""
    if ($remoteUrl -like "*github.com*") { $platformName = "GitHub"; $tokenUrl = "https://github.com/settings/tokens" }
    elseif ($remoteUrl -like "*gitlab.com*") { $platformName = "GitLab"; $tokenUrl = "https://gitlab.com/-/profile/personal_access_tokens" }
    elseif ($remoteUrl -like "*bitbucket.org*") { $platformName = "Bitbucket"; $tokenUrl = "https://bitbucket.org/account/settings/app-passwords/" }

    Write-Host "Detected platform: $platformName" -ForegroundColor Yellow
    if (-not $tokenUrl) {
        Write-Host "Open your platform's token/app password settings manually." -ForegroundColor Yellow
        exit 0
    }

    # Ensure credential manager
    git config --global credential.helper manager-core 1>$null 2>$null

    # Open browser
    Write-Host "Opening browser: $tokenUrl" -ForegroundColor Cyan
    Start-Process $tokenUrl
    Write-Host "Press any key after you finish creating the token..." -ForegroundColor Yellow
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

    # Test auth
    git ls-remote origin 1>$null 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Git authentication OK." -ForegroundColor Green
        exit 0
    } else {
        Write-Host "Git authentication failed." -ForegroundColor Red
        exit 1
    }

} catch {
    $msg = $_.Exception.Message
    Write-Host "Helper error: $msg" -ForegroundColor Red
    exit 1
}
