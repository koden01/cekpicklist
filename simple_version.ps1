param(
    [Parameter(Mandatory=$true)]
    [string]$VersionType
)

# Simple versioning script
$buildGradlePath = "app/build.gradle.kts"

try {
    # Read current version
    $content = Get-Content $buildGradlePath -Raw
    
    # Extract current version
    $versionCodeMatch = [regex]::Match($content, 'versionCode\s*=\s*(\d+)')
    $versionNameMatch = [regex]::Match($content, 'versionName\s*=\s*"([^"]+)"')
    
    if (-not $versionCodeMatch.Success -or -not $versionNameMatch.Success) {
        Write-Error "Could not find version information in build.gradle.kts"
        exit 1
    }
    
    $currentVersionCode = [int]$versionCodeMatch.Groups[1].Value
    $currentVersionName = $versionNameMatch.Groups[1].Value
    
    Write-Host "Current: $currentVersionName (build $currentVersionCode)" -ForegroundColor Yellow
    
    # Calculate new version
    $newVersionCode = $currentVersionCode + 1
    $versionParts = $currentVersionName.Split('.')
    $major = [int]$versionParts[0]
    $minor = [int]$versionParts[1]
    $patch = [int]$versionParts[2]
    
    switch ($VersionType.ToLower()) {
        "major" {
            $major++
            $minor = 0
            $patch = 0
        }
        "minor" {
            $minor++
            $patch = 0
        }
        "patch" {
            $patch++
        }
        default {
            $patch++
        }
    }
    
    $newVersionName = "$major.$minor.$patch"
    
    Write-Host "New: $newVersionName (build $newVersionCode)" -ForegroundColor Green
    
    # Update build.gradle.kts
    $newContent = $content.Replace("versionCode = $currentVersionCode", "versionCode = $newVersionCode")
    $newContent = $newContent.Replace("versionName = `"$currentVersionName`"", "versionName = `"$newVersionName`"")
    
    Set-Content $buildGradlePath $newContent -Encoding UTF8
    
    Write-Host "✅ Updated build.gradle.kts" -ForegroundColor Green
    
    # Write new version to file for batch script
    Set-Content "version.txt" $newVersionName -Encoding UTF8
    
    Write-Host "✅ Versioning completed successfully" -ForegroundColor Green
    
} catch {
    Write-Error "Error during versioning: $($_.Exception.Message)"
    exit 1
}
