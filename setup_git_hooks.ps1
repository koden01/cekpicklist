# Setup Git Hooks untuk Cek Picklist
# Script ini akan mengatur Git hooks untuk auto build

Write-Host "🔧 Setting up Git Hooks for Cek Picklist" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green

# Check if we're in git repository
if (-not (Test-Path ".git")) {
    Write-Error "❌ Not in a git repository!"
    exit 1
}

# Create hooks directory if it doesn't exist
$hooksDir = ".git/hooks"
if (-not (Test-Path $hooksDir)) {
    New-Item -ItemType Directory -Path $hooksDir -Force
    Write-Host "✅ Created hooks directory" -ForegroundColor Green
}

# Copy post-commit hook
$postCommitHook = "$hooksDir/post-commit"
Copy-Item "post-commit.bat" $postCommitHook -Force
Write-Host "✅ Installed post-commit hook" -ForegroundColor Green

# Make hook executable (for Unix-like systems)
if (Get-Command chmod -ErrorAction SilentlyContinue) {
    chmod +x $postCommitHook
    Write-Host "✅ Made hook executable" -ForegroundColor Green
}

Write-Host "🎉 Git hooks setup completed!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host "📋 Installed hooks:" -ForegroundColor Yellow
Write-Host "   • post-commit: Auto build APK after version bump commits" -ForegroundColor White
Write-Host "" -ForegroundColor White
Write-Host "💡 Usage:" -ForegroundColor Yellow
Write-Host "   • Run '.\auto_version.ps1' to bump version and build APK" -ForegroundColor White
Write-Host "   • Run '.\build_release.ps1' to build APK with current version" -ForegroundColor White
Write-Host "   • Git hooks will auto-build on version bump commits" -ForegroundColor White
