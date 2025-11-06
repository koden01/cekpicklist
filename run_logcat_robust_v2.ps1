# ========================================
# LOGCAT ROBUST SCRIPT V2 - ENHANCED EOF HANDLING
# ========================================
# Peningkatan untuk menangani "Unexpected EOF!" dengan lebih baik

param(
    [string]$PackageName = "com.example.cekpicklist",
    [int]$MaxRetries = 20,  # Dinaikkan dari 10
    [int]$RetryDelay = 2,   # Dikurangi dari 3 untuk reconnect lebih cepat
    [int]$HeartbeatTimeout = 30,  # Timeout jika tidak ada log selama 30 detik
    [int]$BufferSize = 64MB,  # Buffer 64MB untuk menangani high volume (dinaikkan dari 16MB)
    [switch]$RestartADB = $false,  # Restart ADB daemon jika diperlukan
    [switch]$Silent = $false,  # Silent mode: hanya tampilkan errors/warnings penting
    [int]$MaxLogsPerSecond = 10,  # Rate limiting: maksimal log per detik (default: 10)
    [int]$DeduplicationWindow = 5,  # Deduplication: skip log yang sama dalam N detik (default: 5)
    [string]$MinLogLevel = "Debug"  # Minimal log level: Error, Warning, Info, Debug (default: Debug)
)

$ErrorActionPreference = "Continue"
$LogFile = "logcat_robust_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
$RetryCount = 0
$LastLogTime = Get-Date
$EOFCount = 0

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

Write-Host "🚀 Starting Enhanced Robust Logcat Monitor V2" -ForegroundColor Green
Write-Host "📱 Package: $PackageName" -ForegroundColor Cyan
Write-Host "📄 Log File: $LogFile" -ForegroundColor Cyan
Write-Host "🔄 Max Retries: $MaxRetries" -ForegroundColor Cyan
Write-Host "⏱️ Retry Delay: ${RetryDelay}s" -ForegroundColor Cyan
Write-Host "💓 Heartbeat Timeout: ${HeartbeatTimeout}s" -ForegroundColor Cyan
Write-Host "📦 Buffer Size: 64MB (hardcoded untuk stabilitas)" -ForegroundColor Cyan
if ($Silent) {
    Write-Host "🔇 Silent Mode: ENABLED (hanya errors/warnings penting)" -ForegroundColor Yellow
}
Write-Host "🚦 Rate Limiting: $MaxLogsPerSecond logs/detik" -ForegroundColor Cyan
Write-Host "🔄 Deduplication: $DeduplicationWindow detik" -ForegroundColor Cyan
Write-Host "📊 Min Log Level: $MinLogLevel" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Yellow

function Write-Log {
    param([string]$Message, [string]$Color = "White", [bool]$Force = $false)
    $Timestamp = Get-Date -Format "HH:mm:ss.fff"
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

function Test-ADBConnection {
    try {
        $result = adb devices 2>&1 | Out-String
        if ($result -match "device\s+$" -or $result -match "device$") {
            return $true
        }
        return $false
    }
    catch {
        return $false
    }
}

function Restart-ADBDaemon {
    Write-Log "🔄 Restarting ADB daemon..." "Yellow"
    try {
        adb kill-server 2>&1 | Out-Null
        Start-Sleep -Seconds 2
        adb start-server 2>&1 | Out-Null
        Start-Sleep -Seconds 2
        
        if (Test-ADBConnection) {
            Write-Log "✅ ADB daemon restarted successfully" "Green"
            return $true
        } else {
            Write-Log "❌ ADB daemon restart failed" "Red"
            return $false
        }
    }
    catch {
        Write-Log "❌ Error restarting ADB: $($_.Exception.Message)" "Red"
        return $false
    }
}

function Wait-ForDevice {
    Write-Log "⏳ Waiting for device to be available..." "Yellow"
    
    $timeout = 30
    $elapsed = 0
    
    while ($elapsed -lt $timeout) {
        if (Test-ADBConnection) {
            Write-Log "✅ Device is available" "Green"
            return $true
        }
        
        Start-Sleep -Seconds 2
        $elapsed += 2
        Write-Host "." -NoNewline -ForegroundColor Yellow
    }
    
    Write-Host ""
    Write-Log "❌ Device not available after ${timeout}s timeout" "Red"
    return $false
}

function Start-LogcatMonitor {
    param([string]$Package)
    
    Write-Log "🔥 Starting logcat monitor for package: $Package" "Green"
    $logcatProcess = $null
    $reader = $null
    
    try {
        # Check ADB connection first
        if (-not (Test-ADBConnection)) {
            Write-Log "❌ ADB connection not available" "Red"
            return $false
        }
        
        # Clear existing logcat buffer dan set buffer size SEBELUM start
        adb logcat -c 2>&1 | Out-Null
        adb logcat -G 64M 2>&1 | Out-Null  # Set buffer size terlebih dahulu
        Start-Sleep -Milliseconds 200  # Tunggu buffer size ter-set
        
        # Start logcat with enhanced parameters
        # -G 64M = Set buffer size to 64MB (default is 256KB) - INCREASED untuk handle high volume
        # -v threadtime = Include thread time for better debugging
        # -b all = All buffers (main, system, radio, events, crash, kernel)
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = "adb"
        $psi.Arguments = "logcat -G 64M -v threadtime -b all -s ScanViewModel:D MainActivity:D Repository:D SupabaseService:D CacheManager:D BarcodeCacheManager:D CEKPICKLIST_MAIN:E *:S"
        $psi.UseShellExecute = $false
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        $psi.CreateNoWindow = $true
        $psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
        
        $logcatProcess = New-Object System.Diagnostics.Process
        $logcatProcess.StartInfo = $psi
        $logcatProcess.Start() | Out-Null
        
        $reader = $logcatProcess.StandardOutput
        $errorReader = $logcatProcess.StandardError
        
        # Start heartbeat monitor in background
        $heartbeatJob = Start-Job -ScriptBlock {
            param($TimeoutSeconds)
            Start-Sleep -Seconds $TimeoutSeconds
            return "TIMEOUT"
        } -ArgumentList $HeartbeatTimeout
        
        $lastLogReceived = Get-Date
        $lineCount = 0
        $emptyReadCount = 0
        
        Write-Log "✅ Logcat process started (PID: $($logcatProcess.Id))" "Green"
        
        # Read with timeout detection
        while (-not $logcatProcess.HasExited) {
            try {
                # Check if process is still alive
                if ($logcatProcess.HasExited) {
                    Write-Log "⚠️ Logcat process exited unexpectedly" "Yellow"
                    break
                }
                
                # Check for errors in stderr
                if ($errorReader.Peek() -gt 0) {
                    $errorLine = $errorReader.ReadLine()
                    if ($errorLine -match "Unexpected EOF" -or $errorLine -match "EOF") {
                        Write-Log "⚠️ EOF detected in stderr: $errorLine" "Yellow"
                        $script:EOFCount++
                        break
                    }
                    Write-Log "⚠️ Error: $errorLine" "Yellow"
                }
                
                # Non-blocking read with timeout
                $line = $null
                $readTimeout = 5000  # 5 seconds timeout per read
                
                # Use async read with timeout
                $readTask = $reader.ReadLineAsync()
                $completed = $readTask.Wait($readTimeout)
                
                if ($completed) {
                    $line = $readTask.Result
                } else {
                    # Timeout - check heartbeat
                    $heartbeatResult = Receive-Job -Job $heartbeatJob -ErrorAction SilentlyContinue
                    if ($heartbeatResult -eq "TIMEOUT") {
                        $timeSinceLastLog = (Get-Date) - $lastLogReceived
                        if ($timeSinceLastLog.TotalSeconds -gt $HeartbeatTimeout) {
                            Write-Log "⚠️ Heartbeat timeout: No log received for ${HeartbeatTimeout}s" "Yellow"
                            Write-Log "🔄 Restarting logcat monitor..." "Yellow"
                            break
                        }
                        # Reset heartbeat job
                        Stop-Job -Job $heartbeatJob -ErrorAction SilentlyContinue
                        Remove-Job -Job $heartbeatJob -ErrorAction SilentlyContinue
                        $heartbeatJob = Start-Job -ScriptBlock {
                            param($TimeoutSeconds)
                            Start-Sleep -Seconds $TimeoutSeconds
                            return "TIMEOUT"
                        } -ArgumentList $HeartbeatTimeout
                    }
                    continue
                }
                
                if ($line) {
                    $lineCount++
                    $emptyReadCount = 0
                    $lastLogReceived = Get-Date
                    
                    # Reset heartbeat on successful read
                    if ($heartbeatJob.State -eq "Running") {
                        Stop-Job -Job $heartbeatJob -ErrorAction SilentlyContinue
                        Remove-Job -Job $heartbeatJob -ErrorAction SilentlyContinue
                        $heartbeatJob = Start-Job -ScriptBlock {
                            param($TimeoutSeconds)
                            Start-Sleep -Seconds $TimeoutSeconds
                            return "TIMEOUT"
                        } -ArgumentList $HeartbeatTimeout
                    }
                    
                    # Filter untuk package yang diinginkan
                    if ($line -match $Package -or $line -match "ScanViewModel|MainActivity|Repository|SupabaseService|CacheManager|BarcodeCacheManager|CEKPICKLIST_MAIN") {
                        # Exclude known noisy sources (OkHttp/System.out/socket)
                        if ($line -match "System\.out|\[okhttp\]|OkHttp|\[socket\]") {
                            continue
                        }
                        # Apply anti-spam filters
                        if (Test-ShouldLog -LogLine $line) {
                            # Determine color based on log level
                            $logLevel = Test-LogLevel -LogLine $line
                            $color = switch ($logLevel) {
                                "Error" { "Red" }
                                "Warning" { "Yellow" }
                                "Info" { "Cyan" }
                                "Debug" { "White" }
                                default { "White" }
                            }
                            Write-Log $line $color
                            $script:LastLogTime = Get-Date
                        }
                    }
                    
                    # Log progress setiap 1000 baris (dikurangi dari 100)
                    if ($lineCount % 1000 -eq 0 -and $lineCount -gt 0) {
                        $filtered = $script:totalLinesFiltered
                        $deduped = $script:totalLinesDeduplicated
                        $rateLimited = $script:totalLinesRateLimited
                        $total = $script:totalLinesProcessed
                        $shown = $total - $filtered - $deduped - $rateLimited
                        Write-Log "📊 Processed $lineCount lines | Total: $total | Shown: $shown | Filtered: $filtered | Deduped: $deduped | Rate-limited: $rateLimited" "Cyan" $true
                    }
                } else {
                    $emptyReadCount++
                    # Jika terlalu banyak empty reads, mungkin EOF
                    if ($emptyReadCount -gt 100) {
                        Write-Log "⚠️ Too many empty reads, possible EOF" "Yellow"
                        break
                    }
                }
            }
            catch [System.InvalidOperationException] {
                # Process might have exited
                if ($logcatProcess.HasExited) {
                    Write-Log "⚠️ Logcat process exited" "Yellow"
                    break
                }
                Write-Log "❌ Invalid operation: $($_.Exception.Message)" "Red"
                Start-Sleep -Milliseconds 100
            }
            catch {
                Write-Log "❌ Error reading logcat line: $($_.Exception.Message)" "Red"
                Start-Sleep -Milliseconds 100
            }
        }
        
        # Check why we exited
        if ($logcatProcess.HasExited) {
            $exitCode = $logcatProcess.ExitCode
            Write-Log "⚠️ Logcat process exited with code: $exitCode" "Yellow"
            
            # Read remaining error output
            while (-not $errorReader.EndOfStream) {
                try {
                    $errorLine = $errorReader.ReadLine()
                    if ($errorLine) {
                        Write-Log "⚠️ Error output: $errorLine" "Yellow"
                        if ($errorLine -match "Unexpected EOF" -or $errorLine -match "EOF") {
                            $script:EOFCount++
                        }
                    }
                }
                catch {
                    break
                }
            }
        }
        
        Write-Log "⚠️ Logcat stream ended (EOF detected or process exited)" "Yellow" $true
        $filtered = $script:totalLinesFiltered
        $deduped = $script:totalLinesDeduplicated
        $rateLimited = $script:totalLinesRateLimited
        $total = $script:totalLinesProcessed
        $shown = $total - $filtered - $deduped - $rateLimited
        Write-Log "📊 Summary: Lines processed: $lineCount | Total: $total | Shown: $shown | Filtered: $filtered | Deduped: $deduped | Rate-limited: $rateLimited" "Cyan" $true
        return $false
        
    }
    catch {
        Write-Log "❌ Error starting logcat: $($_.Exception.Message)" "Red"
        Write-Log "📋 Stack trace: $($_.ScriptStackTrace)" "Red"
        return $false
    }
    finally {
        # Cleanup
        if ($heartbeatJob) {
            Stop-Job -Job $heartbeatJob -ErrorAction SilentlyContinue
            Remove-Job -Job $heartbeatJob -ErrorAction SilentlyContinue
        }
        
        if ($reader) {
            try { $reader.Close() } catch {}
        }
        
        if ($logcatProcess -and !$logcatProcess.HasExited) {
            try {
                $logcatProcess.Kill()
                Write-Log "🛑 Logcat process terminated" "Yellow"
            } catch {
                Write-Log "⚠️ Error terminating process: $($_.Exception.Message)" "Yellow"
            }
        }
        
        if ($logcatProcess) {
            try {
                $logcatProcess.Dispose()
            } catch {}
        }
    }
}

# Main execution loop
while ($RetryCount -lt $MaxRetries) {
    Write-Log "🔄 Attempt $($RetryCount + 1) of $MaxRetries (EOF Count: $EOFCount)" "Cyan"
    
    # Check ADB connection
    if (-not (Test-ADBConnection)) {
        Write-Log "❌ ADB connection failed" "Red"
        
        # Optionally restart ADB daemon
        if ($RestartADB -or $EOFCount -gt 3) {
            if (Restart-ADBDaemon) {
                $EOFCount = 0  # Reset counter after successful restart
            }
        }
        
        if (-not (Wait-ForDevice)) {
            $RetryCount++
            if ($RetryCount -lt $MaxRetries) {
                Write-Log "⏳ Retrying in ${RetryDelay}s..." "Yellow"
                Start-Sleep -Seconds $RetryDelay
            }
            continue
        }
    }
    
    # Start logcat monitor
    $success = Start-LogcatMonitor -Package $PackageName
    
    if ($success) {
        Write-Log "✅ Logcat monitor completed successfully" "Green"
        break
    }
    
    $RetryCount++
    
    # Exponential backoff untuk retry yang berulang
    $actualDelay = $RetryDelay
    if ($EOFCount -gt 5) {
        $calculatedDelay = $RetryDelay * 2
        if ($calculatedDelay -gt 10) {
            $actualDelay = 10  # Max 10 seconds
        } else {
            $actualDelay = $calculatedDelay
        }
        Write-Log "⚠️ Multiple EOFs detected, using exponential backoff: ${actualDelay}s" "Yellow"
    }
    
    if ($RetryCount -lt $MaxRetries) {
        Write-Log "⏳ Retrying in ${actualDelay}s... (Attempt $($RetryCount + 1) of $MaxRetries)" "Yellow"
        Start-Sleep -Seconds $actualDelay
    }
}

if ($RetryCount -ge $MaxRetries) {
    Write-Log "❌ Max retries ($MaxRetries) reached. Exiting." "Red"
    Write-Log "📊 Total EOF occurrences: $EOFCount" "Red"
}
else {
    Write-Log "🎉 Logcat monitoring completed successfully" "Green"
}

Write-Log "📄 Log saved to: $LogFile" "Cyan" $true
Write-Log "📊 Final Stats: Total processed: $totalLinesProcessed | Filtered: $totalLinesFiltered | Deduped: $totalLinesDeduplicated | Rate-limited: $totalLinesRateLimited" "Cyan" $true
Write-Host "=" * 60 -ForegroundColor Yellow

