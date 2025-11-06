# ========================================
# LOGCAT SIMPLE SCRIPT - BASIC RECONNECT
# ========================================

param(
    [string]$PackageName = "com.example.cekpicklist",
    [switch]$Silent = $false,  # Silent mode: hanya tampilkan errors/warnings penting
    [int]$MaxLogsPerSecond = 10,  # Rate limiting: maksimal log per detik (default: 10)
    [int]$DeduplicationWindow = 5,  # Deduplication: skip log yang sama dalam N detik (default: 5)
    [string]$MinLogLevel = "Debug"  # Minimal log level: Error, Warning, Info, Debug (default: Debug)
)

$ErrorActionPreference = "Continue"
$LogFile = "logcat_simple_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"

# Anti-spam log variables
$logRateLimiter = @{
    logs = @()
    windowStart = Get-Date
}
$logDeduplication = @{}  # Hash table untuk menyimpan log terakhir
$silentMode = $Silent
$totalLinesProcessed = 0
$totalLinesFiltered = 0
$totalLinesDeduplicated = 0
$totalLinesRateLimited = 0

Write-Host "🚀 Starting Simple Logcat Monitor" -ForegroundColor Green
Write-Host "📱 Package: $PackageName" -ForegroundColor Cyan
Write-Host "📄 Log File: $LogFile" -ForegroundColor Cyan
if ($Silent) {
    Write-Host "🔇 Silent Mode: ENABLED (hanya errors/warnings penting)" -ForegroundColor Yellow
}
Write-Host "🚦 Rate Limiting: $MaxLogsPerSecond logs/detik" -ForegroundColor Cyan
Write-Host "🔄 Deduplication: $DeduplicationWindow detik" -ForegroundColor Cyan
Write-Host "📊 Min Log Level: $MinLogLevel" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Yellow

function Write-Log {
    param([string]$Message, [string]$Color = "White", [bool]$Force = $false)
    $Timestamp = Get-Date -Format "HH:mm:ss"
    $LogMessage = "[$Timestamp] $Message"
    
    # Always save to file (untuk debugging)
    Add-Content -Path $LogFile -Value $LogMessage -ErrorAction SilentlyContinue
    
    # Skip console output jika silent mode dan bukan error/warning penting
    if ($silentMode -and -not $Force) {
        if ($Color -notmatch "Red|Yellow" -and $Message -notmatch "❌|⚠️|ERROR|WARN|EOF|TIMEOUT") {
            return
        }
    }
    
    Write-Host $LogMessage -ForegroundColor $Color
}

function Test-LogLevel {
    param([string]$LogLine)
    # Detect log level dari format logcat
    if ($LogLine -match "\s+E\s+") { return "Error" }
    if ($LogLine -match "\s+W\s+") { return "Warning" }
    if ($LogLine -match "\s+I\s+") { return "Info" }
    if ($LogLine -match "\s+D\s+") { return "Debug" }
    if ($LogLine -match "\s+V\s+") { return "Verbose" }
    return "Unknown"
}

function Test-ShouldLog {
    param([string]$LogLine)
    
    $script:totalLinesProcessed++
    
    # 1. Check log level
    $logLevel = Test-LogLevel -LogLine $LogLine
    $levelPriority = @{ "Error" = 4; "Warning" = 3; "Info" = 2; "Debug" = 1; "Verbose" = 0; "Unknown" = 2 }
    $minLevelPriority = $levelPriority[$MinLogLevel]
    $currentLevelPriority = $levelPriority[$logLevel]
    
    if ($currentLevelPriority -lt $minLevelPriority) {
        $script:totalLinesFiltered++
        return $false
    }
    
    # 2. Rate limiting
    $now = Get-Date
    $windowDuration = ($now - $logRateLimiter.windowStart).TotalSeconds
    
    # Reset window jika sudah lebih dari 1 detik
    if ($windowDuration -ge 1.0) {
        $logRateLimiter.logs = @()
        $logRateLimiter.windowStart = $now
    }
    
    # Check rate limit
    if ($logRateLimiter.logs.Count -ge $MaxLogsPerSecond) {
        $script:totalLinesRateLimited++
        return $false
    }
    
    # 3. Deduplication
    $logHash = $LogLine.GetHashCode()
    if ($logDeduplication.ContainsKey($logHash)) {
        $lastSeen = $logDeduplication[$logHash]
        $timeSinceLastSeen = ($now - $lastSeen).TotalSeconds
        
        if ($timeSinceLastSeen -lt $DeduplicationWindow) {
            $script:totalLinesDeduplicated++
            return $false
        }
    }
    
    # Update deduplication cache
    $logDeduplication[$logHash] = $now
    
    # Cleanup old deduplication entries (older than 2x window)
    $keysToRemove = @()
    foreach ($key in $logDeduplication.Keys) {
        $entryTime = $logDeduplication[$key]
        if (($now - $entryTime).TotalSeconds -gt ($DeduplicationWindow * 2)) {
            $keysToRemove += $key
        }
    }
    foreach ($key in $keysToRemove) {
        $logDeduplication.Remove($key)
    }
    
    # Add to rate limiter
    $logRateLimiter.logs += $now
    
    return $true
}

# Main execution loop dengan enhanced EOF handling
$retryCount = 0
$maxRetries = 999  # Unlimited retries
$eofCount = 0

while ($retryCount -lt $maxRetries) {
    try {
        Write-Log "🔥 Starting logcat monitor... (Attempt $($retryCount + 1))" "Green"
        
        # Check ADB connection first
        $devices = adb devices 2>&1
        if ($devices -notmatch "device\s+$") {
            Write-Log "❌ No device connected. Waiting..." "Red"
            Start-Sleep -Seconds 3
            $retryCount++
            continue
        }
        
        # Clear logcat buffer dan set buffer size SEBELUM start
        $clearResult = adb logcat -c 2>&1
        adb logcat -G 64M 2>&1 | Out-Null  # Set buffer size terlebih dahulu
        Start-Sleep -Milliseconds 500  # Tunggu buffer size ter-set
        
        # Start logcat with enhanced buffer size (-G 64M) - INCREASED untuk handle high volume
        # -b all = All buffers untuk stabilitas lebih baik
        # Use try-catch untuk handle EOF
        $lineCount = 0
        $logcatOutput = adb logcat -G 64M -v threadtime -b all -s ScanViewModel:D MainActivity:D Repository:D SupabaseService:D CacheManager:D BarcodeCacheManager:D CEKPICKLIST_MAIN:E *:S 2>&1 | ForEach-Object {
            $lineCount++
            
            if ($_ -match "Unexpected EOF" -or $_ -match "EOF") {
                Write-Log "⚠️ EOF detected in stream: $_" "Yellow" $true
                $script:eofCount++
                throw "EOF_DETECTED"
            }
            elseif ($_ -match $PackageName -or $_ -match "ScanViewModel|MainActivity|Repository|SupabaseService|CacheManager|BarcodeCacheManager|CEKPICKLIST_MAIN") {
                # Exclude known noisy sources (OkHttp/System.out/socket)
                if ($_ -match "System\.out|\[okhttp\]|OkHttp|\[socket\]") {
                    continue
                }
                # Apply anti-spam filters
                if (Test-ShouldLog -LogLine $_) {
                    # Determine color based on log level
                    $logLevel = Test-LogLevel -LogLine $_
                    $color = switch ($logLevel) {
                        "Error" { "Red" }
                        "Warning" { "Yellow" }
                        "Info" { "Cyan" }
                        "Debug" { "White" }
                        default { "White" }
                    }
                    Write-Log $_ $color
                }
            }
            
            # Log progress setiap 1000 baris
            if ($lineCount % 1000 -eq 0 -and $lineCount -gt 0) {
                $filtered = $script:totalLinesFiltered
                $deduped = $script:totalLinesDeduplicated
                $rateLimited = $script:totalLinesRateLimited
                $total = $script:totalLinesProcessed
                $shown = $total - $filtered - $deduped - $rateLimited
                Write-Log "📊 Processed $lineCount lines | Total: $total | Shown: $shown | Filtered: $filtered | Deduped: $deduped | Rate-limited: $rateLimited" "Cyan" $true
            }
        }
        
        # Jika sampai sini berarti selesai dengan normal
        Write-Log "✅ Logcat monitor completed normally" "Green"
        break
        
    }
    catch {
        $errorMsg = $_.Exception.Message
        if ($errorMsg -eq "EOF_DETECTED") {
            Write-Log "⚠️ EOF detected. Retrying..." "Yellow"
            $retryCount++
            Start-Sleep -Seconds 2  # Delay lebih pendek untuk reconnect cepat
        }
        else {
            Write-Log "❌ Error in logcat: $errorMsg" "Red"
            Write-Log "⏳ Retrying in 3 seconds..." "Yellow"
            Start-Sleep -Seconds 3
            $retryCount++
        }
        
        # Restart ADB jika terlalu banyak EOF
        if ($eofCount -gt 5) {
            Write-Log "🔄 Too many EOFs, restarting ADB daemon..." "Yellow"
            adb kill-server 2>&1 | Out-Null
            Start-Sleep -Seconds 2
            adb start-server 2>&1 | Out-Null
            Start-Sleep -Seconds 2
            $eofCount = 0
        }
    }
}

Write-Log "📄 Log saved to: $LogFile" "Cyan" $true
$filtered = $script:totalLinesFiltered
$deduped = $script:totalLinesDeduplicated
$rateLimited = $script:totalLinesRateLimited
$total = $script:totalLinesProcessed
$shown = $total - $filtered - $deduped - $rateLimited
Write-Log "📊 Final Stats: Total processed: $total | Shown: $shown | Filtered: $filtered | Deduped: $deduped | Rate-limited: $rateLimited" "Cyan" $true
