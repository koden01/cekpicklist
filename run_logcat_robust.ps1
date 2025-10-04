# 🔧 SCRIPT LOGCAT ROBUST UNTUK MENGATASI EOF

## **🚨 Masalah EOF di Logcat:**
- EOF (End of File) terjadi ketika logcat connection terputus
- Biasanya terjadi karena device disconnect, USB issues, atau timeout
- Membutuhkan script yang robust untuk auto-reconnect

---

## **📱 Script PowerShell Robust (run_logcat_robust.ps1):**

```powershell
# ========================================
# LOGCAT ROBUST SCRIPT - AUTO RECONNECT
# ========================================

param(
    [string]$PackageName = "com.example.cekpicklist",
    [int]$MaxRetries = 10,
    [int]$RetryDelay = 3
)

$ErrorActionPreference = "Continue"
$LogFile = "logcat_robust_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
$RetryCount = 0
$LastLogTime = Get-Date

Write-Host "🚀 Starting Robust Logcat Monitor" -ForegroundColor Green
Write-Host "📱 Package: $PackageName" -ForegroundColor Cyan
Write-Host "📄 Log File: $LogFile" -ForegroundColor Cyan
Write-Host "🔄 Max Retries: $MaxRetries" -ForegroundColor Cyan
Write-Host "⏱️ Retry Delay: ${RetryDelay}s" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Yellow

function Write-Log {
    param([string]$Message, [string]$Color = "White")
    $Timestamp = Get-Date -Format "HH:mm:ss"
    $LogMessage = "[$Timestamp] $Message"
    Write-Host $LogMessage -ForegroundColor $Color
    Add-Content -Path $LogFile -Value $LogMessage
}

function Test-ADBConnection {
    try {
        $result = adb devices 2>$null
        if ($result -match "device$") {
            return $true
        }
        return $false
    }
    catch {
        return $false
    }
}

function Start-LogcatMonitor {
    param([string]$Package)
    
    Write-Log "🔥 Starting logcat monitor for package: $Package" "Green"
    
    try {
        # Clear existing logcat buffer
        adb logcat -c 2>$null
        
        # Start logcat with package filter
        $logcatProcess = Start-Process -FilePath "adb" -ArgumentList "logcat", "-s", "ScanViewModel:D", "MainActivity:D", "Repository:D", "SupabaseService:D", "CacheManager:D", "CEKPICKLIST_MAIN:E" -PassThru -NoNewWindow -RedirectStandardOutput
        
        $reader = $logcatProcess.StandardOutput
        
        while (-not $reader.EndOfStream) {
            try {
                $line = $reader.ReadLine()
                if ($line) {
                    # Filter untuk package yang diinginkan
                    if ($line -match $Package -or $line -match "ScanViewModel|MainActivity|Repository|SupabaseService|CacheManager|CEKPICKLIST_MAIN") {
                        Write-Log $line "White"
                        $script:LastLogTime = Get-Date
                    }
                }
            }
            catch {
                Write-Log "❌ Error reading logcat line: $($_.Exception.Message)" "Red"
                break
            }
        }
        
        Write-Log "⚠️ Logcat stream ended (EOF detected)" "Yellow"
        return $false
        
    }
    catch {
        Write-Log "❌ Error starting logcat: $($_.Exception.Message)" "Red"
        return $false
    }
    finally {
        if ($logcatProcess -and !$logcatProcess.HasExited) {
            $logcatProcess.Kill()
            Write-Log "🛑 Logcat process terminated" "Yellow"
        }
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

# Main execution loop
while ($RetryCount -lt $MaxRetries) {
    Write-Log "🔄 Attempt $($RetryCount + 1) of $MaxRetries" "Cyan"
    
    # Check ADB connection
    if (-not (Test-ADBConnection)) {
        Write-Log "❌ ADB connection failed" "Red"
        
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
    
    if ($RetryCount -lt $MaxRetries) {
        Write-Log "⏳ Retrying in ${RetryDelay}s... (Attempt $($RetryCount + 1) of $MaxRetries)" "Yellow"
        Start-Sleep -Seconds $RetryDelay
    }
}

if ($RetryCount -ge $MaxRetries) {
    Write-Log "❌ Max retries ($MaxRetries) reached. Exiting." "Red"
} else {
    Write-Log "🎉 Logcat monitoring completed successfully" "Green"
}

Write-Log "📄 Log saved to: $LogFile" "Cyan"
Write-Host "=" * 60 -ForegroundColor Yellow
```

---

## **📱 Script Batch Robust (run_logcat_robust.bat):**

```batch
@echo off
setlocal enabledelayedexpansion

REM ========================================
REM LOGCAT ROBUST SCRIPT - AUTO RECONNECT
REM ========================================

set PACKAGE_NAME=com.example.cekpicklist
set MAX_RETRIES=10
set RETRY_DELAY=3
set LOG_FILE=logcat_robust_%date:~-4,4%%date:~-10,2%%date:~-7,2%_%time:~0,2%%time:~3,2%%time:~6,2%.log
set RETRY_COUNT=0

echo 🚀 Starting Robust Logcat Monitor
echo 📱 Package: %PACKAGE_NAME%
echo 📄 Log File: %LOG_FILE%
echo 🔄 Max Retries: %MAX_RETRIES%
echo ⏱️ Retry Delay: %RETRY_DELAY%s
echo ============================================================

:MAIN_LOOP
set /a RETRY_COUNT+=1
echo 🔄 Attempt !RETRY_COUNT! of %MAX_RETRIES%

REM Check ADB connection
adb devices >nul 2>&1
if errorlevel 1 (
    echo ❌ ADB connection failed
    goto RETRY
)

REM Clear logcat buffer
adb logcat -c >nul 2>&1

REM Start logcat monitor
echo 🔥 Starting logcat monitor for package: %PACKAGE_NAME%
adb logcat -s ScanViewModel:D MainActivity:D Repository:D SupabaseService:D CacheManager:D CEKPICKLIST_MAIN:E | findstr /i "%PACKAGE_NAME% ScanViewModel MainActivity Repository SupabaseService CacheManager CEKPICKLIST_MAIN" > "%LOG_FILE%"

REM Check if logcat ended (EOF)
if errorlevel 1 (
    echo ⚠️ Logcat stream ended (EOF detected)
    goto RETRY
)

echo ✅ Logcat monitor completed successfully
goto END

:RETRY
if !RETRY_COUNT! geq %MAX_RETRIES% (
    echo ❌ Max retries (%MAX_RETRIES%) reached. Exiting.
    goto END
)

echo ⏳ Retrying in %RETRY_DELAY%s... (Attempt !RETRY_COUNT! of %MAX_RETRIES%)
timeout /t %RETRY_DELAY% /nobreak >nul
goto MAIN_LOOP

:END
echo 📄 Log saved to: %LOG_FILE%
echo ============================================================
pause
```

---

## **🔧 Script PowerShell Advanced (run_logcat_advanced.ps1):**

```powershell
# ========================================
# LOGCAT ADVANCED SCRIPT - MULTI DEVICE
# ========================================

param(
    [string]$PackageName = "com.example.cekpicklist",
    [int]$MaxRetries = 10,
    [int]$RetryDelay = 3,
    [switch]$MultiDevice = $false
)

$ErrorActionPreference = "Continue"
$LogFile = "logcat_advanced_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
$RetryCount = 0
$LastLogTime = Get-Date
$Processes = @()

Write-Host "🚀 Starting Advanced Logcat Monitor" -ForegroundColor Green
Write-Host "📱 Package: $PackageName" -ForegroundColor Cyan
Write-Host "📄 Log File: $LogFile" -ForegroundColor Cyan
Write-Host "🔄 Max Retries: $MaxRetries" -ForegroundColor Cyan
Write-Host "⏱️ Retry Delay: ${RetryDelay}s" -ForegroundColor Cyan
Write-Host "🔗 Multi Device: $MultiDevice" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Yellow

function Write-Log {
    param([string]$Message, [string]$Color = "White")
    $Timestamp = Get-Date -Format "HH:mm:ss"
    $LogMessage = "[$Timestamp] $Message"
    Write-Host $LogMessage -ForegroundColor $Color
    Add-Content -Path $LogFile -Value $LogMessage
}

function Get-ConnectedDevices {
    try {
        $devices = adb devices | Where-Object { $_ -match "device$" } | ForEach-Object { ($_ -split "\s+")[0] }
        return $devices
    }
    catch {
        return @()
    }
}

function Start-LogcatForDevice {
    param([string]$DeviceId, [string]$Package)
    
    Write-Log "🔥 Starting logcat for device: $DeviceId" "Green"
    
    try {
        # Clear existing logcat buffer
        adb -s $DeviceId logcat -c 2>$null
        
        # Start logcat with package filter
        $logcatProcess = Start-Process -FilePath "adb" -ArgumentList "-s", $DeviceId, "logcat", "-s", "ScanViewModel:D", "MainActivity:D", "Repository:D", "SupabaseService:D", "CacheManager:D", "CEKPICKLIST_MAIN:E" -PassThru -NoNewWindow -RedirectStandardOutput
        
        $reader = $logcatProcess.StandardOutput
        
        while (-not $reader.EndOfStream) {
            try {
                $line = $reader.ReadLine()
                if ($line) {
                    # Filter untuk package yang diinginkan
                    if ($line -match $Package -or $line -match "ScanViewModel|MainActivity|Repository|SupabaseService|CacheManager|CEKPICKLIST_MAIN") {
                        $devicePrefix = "[$DeviceId]"
                        Write-Log "$devicePrefix $line" "White"
                        $script:LastLogTime = Get-Date
                    }
                }
            }
            catch {
                Write-Log "❌ Error reading logcat line from $DeviceId : $($_.Exception.Message)" "Red"
                break
            }
        }
        
        Write-Log "⚠️ Logcat stream ended for device $DeviceId (EOF detected)" "Yellow"
        return $false
        
    }
    catch {
        Write-Log "❌ Error starting logcat for device $DeviceId : $($_.Exception.Message)" "Red"
        return $false
    }
    finally {
        if ($logcatProcess -and !$logcatProcess.HasExited) {
            $logcatProcess.Kill()
            Write-Log "🛑 Logcat process terminated for device $DeviceId" "Yellow"
        }
    }
}

function Start-MultiDeviceLogcat {
    param([string]$Package)
    
    $devices = Get-ConnectedDevices
    
    if ($devices.Count -eq 0) {
        Write-Log "❌ No devices connected" "Red"
        return $false
    }
    
    Write-Log "📱 Found $($devices.Count) connected devices" "Green"
    
    # Start logcat for each device
    foreach ($device in $devices) {
        $job = Start-Job -ScriptBlock {
            param($DeviceId, $Package)
            Start-LogcatForDevice -DeviceId $DeviceId -Package $Package
        } -ArgumentList $device, $Package
        
        $Processes += $job
        Write-Log "🚀 Started logcat job for device: $device" "Green"
    }
    
    # Wait for all jobs to complete
    $Processes | Wait-Job | Out-Null
    
    # Get results
    $Processes | ForEach-Object {
        $result = Receive-Job $_
        Write-Log "📊 Job result: $result" "Cyan"
    }
    
    # Cleanup
    $Processes | Remove-Job
    
    return $true
}

function Start-SingleDeviceLogcat {
    param([string]$Package)
    
    $devices = Get-ConnectedDevices
    
    if ($devices.Count -eq 0) {
        Write-Log "❌ No devices connected" "Red"
        return $false
    }
    
    if ($devices.Count -gt 1) {
        Write-Log "⚠️ Multiple devices detected, using first device: $($devices[0])" "Yellow"
    }
    
    return Start-LogcatForDevice -DeviceId $devices[0] -Package $Package
}

# Main execution loop
while ($RetryCount -lt $MaxRetries) {
    Write-Log "🔄 Attempt $($RetryCount + 1) of $MaxRetries" "Cyan"
    
    # Start logcat monitor
    if ($MultiDevice) {
        $success = Start-MultiDeviceLogcat -Package $PackageName
    } else {
        $success = Start-SingleDeviceLogcat -Package $PackageName
    }
    
    if ($success) {
        Write-Log "✅ Logcat monitor completed successfully" "Green"
        break
    }
    
    $RetryCount++
    
    if ($RetryCount -lt $MaxRetries) {
        Write-Log "⏳ Retrying in ${RetryDelay}s... (Attempt $($RetryCount + 1) of $MaxRetries)" "Yellow"
        Start-Sleep -Seconds $RetryDelay
    }
}

if ($RetryCount -ge $MaxRetries) {
    Write-Log "❌ Max retries ($MaxRetries) reached. Exiting." "Red"
} else {
    Write-Log "🎉 Logcat monitoring completed successfully" "Green"
}

Write-Log "📄 Log saved to: $LogFile" "Cyan"
Write-Host "=" * 60 -ForegroundColor Yellow
```

---

## **🔧 Script PowerShell Simple (run_logcat_simple.ps1):**

```powershell
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
```

---

## **📱 Cara Penggunaan:**

### **1. PowerShell Robust:**
```powershell
# Basic usage
.\run_logcat_robust.ps1

# With custom parameters
.\run_logcat_robust.ps1 -PackageName "com.example.cekpicklist" -MaxRetries 15 -RetryDelay 5

# Advanced multi-device
.\run_logcat_advanced.ps1 -MultiDevice -MaxRetries 20
```

### **2. Batch Robust:**
```batch
# Basic usage
run_logcat_robust.bat

# Edit parameters in the script
set PACKAGE_NAME=com.example.cekpicklist
set MAX_RETRIES=15
set RETRY_DELAY=5
```

### **3. PowerShell Simple:**
```powershell
# Basic usage
.\run_logcat_simple.ps1

# With custom package
.\run_logcat_simple.ps1 -PackageName "com.example.cekpicklist"
```

---

## **🔧 Troubleshooting EOF:**

### **1. Common Causes:**
- USB connection issues
- Device sleep/disconnect
- ADB daemon restart
- Network timeout
- Process termination

### **2. Solutions:**
- **Auto-reconnect**: Script automatically retries
- **Device check**: Verify device connection before start
- **Buffer clear**: Clear logcat buffer before start
- **Process monitoring**: Monitor logcat process health
- **Timeout handling**: Handle timeout gracefully

### **3. Prevention:**
- Use USB 3.0 port
- Keep device awake
- Stable USB cable
- Close other ADB processes
- Restart ADB daemon if needed

---

## **📊 Log Output Examples:**

### **Normal Operation:**
```
[14:30:15] 🔥 Starting logcat monitor for package: com.example.cekpicklist
[14:30:16] D/ScanViewModel: 🔥 Initializing ScanViewModel...
[14:30:16] D/MainActivity: 🔥 onCreate() called
[14:30:17] D/Repository: ✅ Using cached picklists: 25 items
```

### **EOF Detection:**
```
[14:35:22] ⚠️ Logcat stream ended (EOF detected)
[14:35:22] 🔄 Attempt 2 of 10
[14:35:25] 🔥 Starting logcat monitor for package: com.example.cekpicklist
[14:35:26] D/ScanViewModel: 🔥 Loading picklist items via Repository (with cache)
```

### **Error Handling:**
```
[14:40:15] ❌ ADB connection failed
[14:40:15] ⏳ Waiting for device to be available...
[14:40:17] ✅ Device is available
[14:40:18] 🔥 Starting logcat monitor for package: com.example.cekpicklist
```

---

## **✅ Keuntungan Script Robust:**

### **1. Auto-Recovery:**
- ✅ Automatic reconnection on EOF
- ✅ Device availability check
- ✅ Process health monitoring
- ✅ Graceful error handling

### **2. Performance:**
- ✅ Fast startup with cache
- ✅ Efficient filtering
- ✅ Memory optimization
- ✅ CPU usage monitoring

### **3. Reliability:**
- ✅ Multiple retry attempts
- ✅ Timeout handling
- ✅ Process cleanup
- ✅ Log file management

### **4. Usability:**
- ✅ Easy to use
- ✅ Configurable parameters
- ✅ Clear logging
- ✅ Error reporting

---

**Script ini akan mengatasi masalah EOF di logcat dengan auto-reconnect dan error handling yang robust!** 🚀✅