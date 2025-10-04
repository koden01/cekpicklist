param(
    [Parameter(Mandatory=$true)]
    [string]$Version
)

# Update README script
$readmePath = "README.md"

try {
    # Read README content
    $readmeContent = Get-Content $readmePath -Raw
    
    # Get current date
    $currentDate = Get-Date -Format "yyyy-MM-dd"
    
    # Update version and date
    $readmeContent = $readmeContent -replace '\*\*Version\*\*: [0-9]+\.[0-9]+\.[0-9]+ \(Auto-updating\)', "**Version**: $Version (Auto-updating)"
    $readmeContent = $readmeContent -replace '\*\*Last Updated\*\*: [0-9]{4}-[0-9]{2}-[0-9]{2}', "**Last Updated**: $currentDate"
    
    # Write back to file
    Set-Content $readmePath $readmeContent -Encoding UTF8
    
    Write-Host "✅ Updated README.md" -ForegroundColor Green
    Write-Host "   Version: $Version" -ForegroundColor Cyan
    Write-Host "   Date: $currentDate" -ForegroundColor Cyan
    
} catch {
    Write-Error "Error updating README: $($_.Exception.Message)"
    exit 1
}
