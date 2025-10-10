param(
    [Parameter(Mandatory=$true)]
    [string]$Version,
    [Parameter(Mandatory=$false)]
    [string]$VersionCode = ""
)

# Update README script
$readmePath = "README.md"

try {
    # Check if file exists
    if (-not (Test-Path $readmePath)) {
        Write-Error "README.md file not found at: $readmePath"
        Write-Host "Current directory: $(Get-Location)" -ForegroundColor Yellow
        exit 1
    }
    
    # Read README content
    $readmeContent = Get-Content $readmePath -Raw
    
    if (-not $readmeContent) {
        Write-Error "Could not read content from README.md"
        exit 1
    }
    
    # Get current date
    $currentDate = Get-Date -Format "yyyy-MM-dd"
    
    # Get version code from build.gradle.kts if not provided
    if (-not $VersionCode) {
        $buildGradlePath = "app/build.gradle.kts"
        if (Test-Path $buildGradlePath) {
            $buildContent = Get-Content $buildGradlePath -Raw
            $versionCodeMatch = [regex]::Match($buildContent, 'versionCode\s*=\s*(\d+)')
            if ($versionCodeMatch.Success) {
                $VersionCode = $versionCodeMatch.Groups[1].Value
            }
        }
    }
    
    # Update version and date - support multiple patterns
    if ($VersionCode) {
        $readmeContent = $readmeContent -replace '\*\*Version\*\*: [0-9]+\.[0-9]+\.[0-9]+ \(Version Code: [0-9]+\)', "**Version**: $Version (Version Code: $VersionCode)"
    } else {
        $readmeContent = $readmeContent -replace '\*\*Version\*\*: [0-9]+\.[0-9]+\.[0-9]+ \(Version Code: [0-9]+\)', "**Version**: $Version (Version Code: [AUTO])"
    }
    $readmeContent = $readmeContent -replace '\*\*Version\*\*: [0-9]+\.[0-9]+\.[0-9]+ \(Auto-updating\)', "**Version**: $Version (Auto-updating)"
    $readmeContent = $readmeContent -replace '\*\*Last Updated\*\*: [0-9]{4}-[0-9]{2}-[0-9]{2}', "**Last Updated**: $currentDate"
    
    # Debug: Show what was replaced
    Write-Host "Debug: Looking for version pattern..." -ForegroundColor Yellow
    if ($readmeContent -match '\*\*Version\*\*: [0-9]+\.[0-9]+\.[0-9]+ \(Version Code: [0-9]+\)') {
        Write-Host "Found version pattern (Version Code) to replace" -ForegroundColor Green
    } elseif ($readmeContent -match '\*\*Version\*\*: [0-9]+\.[0-9]+\.[0-9]+ \(Auto-updating\)') {
        Write-Host "Found version pattern (Auto-updating) to replace" -ForegroundColor Green
    } else {
        Write-Host "Version pattern not found" -ForegroundColor Red
    }
    
    # Write back to file
    Set-Content $readmePath $readmeContent -Encoding UTF8
    
    Write-Host "Updated README.md" -ForegroundColor Green
    Write-Host "   Version: $Version" -ForegroundColor Cyan
    Write-Host "   Date: $currentDate" -ForegroundColor Cyan
    
} catch {
    Write-Error "Error updating README: $($_.Exception.Message)"
    exit 1
}
