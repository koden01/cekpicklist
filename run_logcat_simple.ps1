# ========================================
# LOGCAT SIMPLE SCRIPT - BASIC RECONNECT
# ========================================

param(
    [string]$PackageName = "com.example.cekpicklist"
)

$ErrorActionPreference = "Continue"
$LogFile = "logcat_simple_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"

Write-Host "🚀 Starting Simple Logcat Monitor" -ForegroundColor Green
Write-Host "📱 Package: $PackageName" -ForegroundColor Cyan
Write-Host "📄 Log File: $LogFile" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Yellow

function Write-Log {
    param([string]$Message, [string]$Color = "White")
    $Timestamp = Get-Date -Format "HH:mm:ss"
    $LogMessage = "[$Timestamp] $Message"
    Write-Host $LogMessage -ForegroundColor $Color
    Add-Content -Path $LogFile -Value $LogMessage
}

# Main execution loop
while ($true) {
    try {
        Write-Log "🔥 Starting logcat monitor..." "Green"
        
        # Clear logcat buffer
        adb logcat -c 2>$null
        
        # Start logcat with package filter
        adb logcat -s ScanViewModel:D MainActivity:D Repository:D SupabaseService:D CacheManager:D CEKPICKLIST_MAIN:E | ForEach-Object {
            if ($_ -match $PackageName -or $_ -match "ScanViewModel|MainActivity|Repository|SupabaseService|CacheManager|CEKPICKLIST_MAIN") {
                Write-Log $_ "White"
            }
        }
        
    }
    catch {
        Write-Log "❌ Error in logcat: $($_.Exception.Message)" "Red"
        Write-Log "⏳ Retrying in 5 seconds..." "Yellow"
        Start-Sleep -Seconds 5
    }
}

Write-Log "📄 Log saved to: $LogFile" "Cyan"
