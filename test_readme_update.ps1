# Test README update script
param(
    [string]$Version = "4.5.0"
)

Write-Host "🔍 Testing README update..." -ForegroundColor Yellow
Write-Host "Version: $Version" -ForegroundColor Cyan

try {
    # Read README content
    $readmePath = "README.md"
    $readmeContent = Get-Content $readmePath -Raw
    
    Write-Host "✅ README content loaded" -ForegroundColor Green
    
    # Get current date
    $currentDate = Get-Date -Format "yyyy-MM-dd"
    
    # Show current version line
    $versionLine = ($readmeContent -split "`n" | Where-Object { $_ -match "Version.*Auto-updating" })[0]
    Write-Host "Current version line: $versionLine" -ForegroundColor Yellow
    
    # Update version and date
    $newContent = $readmeContent -replace '\*\*Version\*\*: [0-9]+\.[0-9]+\.[0-9]+ \(Auto-updating\)', "**Version**: $Version (Auto-updating)"
    $newContent = $newContent -replace '\*\*Last Updated\*\*: [0-9]{4}-[0-9]{2}-[0-9]{2}', "**Last Updated**: $currentDate"
    
    # Write back to file
    Set-Content $readmePath $newContent -Encoding UTF8
    
    Write-Host "✅ README updated successfully" -ForegroundColor Green
    Write-Host "   Version: $Version" -ForegroundColor Cyan
    Write-Host "   Date: $currentDate" -ForegroundColor Cyan
    
} catch {
    Write-Error "Error: $($_.Exception.Message)"
    exit 1
}
