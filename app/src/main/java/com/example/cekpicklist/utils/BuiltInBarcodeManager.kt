package com.example.cekpicklist.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

// Hardware Scanner API imports (using reflection to avoid dependency issues)
// import com.rscja.deviceapi.Barcode1D
// import com.rscja.deviceapi.DeviceAPI

/**
 * Built-in Barcode Scanner Manager
 * HARDWARE SCANNER ONLY - Using actual 1D barcode scanner hardware
 * Supports both hardware scanner API (Barcode1D) and broadcast receiver
 */
class BuiltInBarcodeManager(
    private val context: Context,
    private val activity: Activity
) {
    
    companion object {
        private const val TAG = "BuiltInBarcodeManager"
        private const val SCAN_TIMEOUT_MS = 5000L // 5 detik timeout
        private const val DEBOUNCE_DELAY_MS = 100L // 100ms debounce
        private const val SCANNER_BROADCAST_ACTION = "com.scanner.broadcast"
        private const val SCANNER_BROADCAST_KEY = "data"
    }
    
    // Real hardware scanner implementation
    private var isInitialized = false
    private var isScanning = false
    private var deviceAPI: Any? = null
    private var barcode1D: Any? = null
    
    // Callbacks
    private var onBarcodeDetected: ((String) -> Unit)? = null
    private var onScanStateChanged: ((Boolean) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    
    // Debounce mechanism untuk mencegah duplicate scans
    private val debounceExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val lastScannedBarcode = mutableSetOf<String>()
    private val scanHistory = mutableListOf<BarcodeScanResult>()
    
    // Performance tracking
    private var scanStartTime = 0L
    private var totalScans = 0
    private var successfulScans = 0
    
    // Broadcast receiver for scanner
    private var scannerBroadcastReceiver: BroadcastReceiver? = null
    
    data class BarcodeScanResult(
        val barcode: String,
        val timestamp: Long,
        val scanDuration: Long,
        val success: Boolean
    )
    
    init {
        initializeBuiltInScanner()
        setupBroadcastReceiver()
    }
    
    /**
     * Initialize built-in barcode scanner (REAL HARDWARE)
     */
    private fun initializeBuiltInScanner() {
        try {
            Log.d(TAG, "🔧 Initializing built-in barcode scanner (REAL HARDWARE)...")
            
            // Try to initialize hardware scanner using reflection
            try {
                val deviceAPIClass = Class.forName("com.rscja.deviceapi.DeviceAPI")
                val getInstanceMethod = deviceAPIClass.getMethod("getInstance")
                deviceAPI = getInstanceMethod.invoke(null)
                
                if (deviceAPI != null) {
                    Log.d(TAG, "✅ DeviceAPI initialized successfully via reflection")
                    
                    // Try to initialize Barcode1D scanner
                    val barcode1DClass = Class.forName("com.rscja.deviceapi.Barcode1D")
                    val getInstanceMethod2 = barcode1DClass.getMethod("getInstance")
                    barcode1D = getInstanceMethod2.invoke(null)
                    
                    if (barcode1D != null) {
                        Log.d(TAG, "✅ Barcode1D scanner initialized successfully via reflection")
                        
                        // Set up barcode callback using reflection
                        val setOnBarcodeListenerMethod = barcode1DClass.getMethod("setOnBarcodeListener", 
                            Class.forName("com.rscja.deviceapi.Barcode1D\$OnBarcodeListener"))
                        
                        val listener = java.lang.reflect.Proxy.newProxyInstance(
                            barcode1DClass.classLoader,
                            arrayOf(Class.forName("com.rscja.deviceapi.Barcode1D\$OnBarcodeListener"))
                        ) { _, method, args ->
                            Log.d(TAG, "🔍 Barcode listener method called: ${method.name}")
                            if (method.name == "onBarcode") {
                                val barcode = args?.get(0) as? String
                                Log.d(TAG, "📱 Barcode detected: $barcode")
                                Log.d(TAG, "📱 Calling onBarcodeDetected callback...")
                                onBarcodeDetected?.invoke(barcode ?: "")
                                Log.d(TAG, "✅ onBarcodeDetected callback completed")
                            }
                            null
                        }
                        
                        setOnBarcodeListenerMethod.invoke(barcode1D, listener)
                        
                        isInitialized = true
                        Log.d(TAG, "✅ Built-in barcode scanner initialized for REAL HARDWARE")
                        Log.d(TAG, "💡 1D Barcode scanner ready for real barcode detection")
                    } else {
                        Log.e(TAG, "❌ Failed to initialize Barcode1D scanner")
                        onError?.invoke("Failed to initialize Barcode1D scanner")
                    }
                } else {
                    Log.e(TAG, "❌ Failed to initialize DeviceAPI")
                    onError?.invoke("Failed to initialize DeviceAPI")
                }
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "⚠️ Hardware scanner API not available, using fallback")
                // Initialize fallback scanner
                initializeFallbackScanner()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing built-in scanner: ${e.message}", e)
            onError?.invoke("Error initializing built-in scanner: ${e.message}")
        }
    }
    
    /**
     * Initialize fallback scanner (DISABLED - Hardware scanner only)
     */
    private fun initializeFallbackScanner() {
        Log.w(TAG, "⚠️ Fallback scanner disabled - Hardware scanner only")
        Log.e(TAG, "❌ Hardware scanner tidak tersedia. Hanya hardware scanner yang didukung.")
        onError?.invoke("Hardware scanner tidak tersedia. Hanya hardware scanner yang didukung.")
    }
    
    /**
     * Check if built-in scanner is available
     */
    fun isBuiltInScannerAvailable(): Boolean {
        return try {
            // Try to check if real hardware scanner is available
            val hasHardwareScanner = checkHardwareScanner()
            Log.d(TAG, "🔍 Checking built-in scanner availability: $hasHardwareScanner")
            hasHardwareScanner
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error checking hardware scanner, using MOCK MODE: ${e.message}")
            false
        }
    }
    
    /**
     * Check if device has real hardware scanner
     */
    private fun checkHardwareScanner(): Boolean {
        return try {
            // Check if Barcode1D scanner is available
            val isAvailable = barcode1D != null && isInitialized
            Log.d(TAG, "🔍 Hardware scanner check: available=$isAvailable")
            isAvailable
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error in hardware scanner check: ${e.message}")
            false
        }
    }
    
    /**
     * Setup broadcast receiver for scanner
     */
    private fun setupBroadcastReceiver() {
        Log.d(TAG, "🔧 Setting up broadcast receiver for scanner...")
        
        scannerBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == SCANNER_BROADCAST_ACTION) {
                    val barcodeData = intent.getStringExtra(SCANNER_BROADCAST_KEY)
                    Log.d(TAG, "📱 Broadcast received: $barcodeData")
                    
                    if (!barcodeData.isNullOrEmpty()) {
                        Log.d(TAG, "📱 Barcode from broadcast: $barcodeData")
                        Log.d(TAG, "📱 Barcode length: ${barcodeData.length}")
                        Log.d(TAG, "📱 onBarcodeDetected callback is null: ${onBarcodeDetected == null}")
                        
                        if (onBarcodeDetected != null) {
                            Log.d(TAG, "📱 Calling onBarcodeDetected callback...")
                            onBarcodeDetected?.invoke(barcodeData)
                            Log.d(TAG, "✅ onBarcodeDetected callback completed")
                        } else {
                            Log.e(TAG, "❌ onBarcodeDetected callback is null!")
                        }
                    } else {
                        Log.w(TAG, "⚠️ Empty barcode data from broadcast")
                    }
                }
            }
        }
        
        val filter = IntentFilter(SCANNER_BROADCAST_ACTION)
        context.registerReceiver(scannerBroadcastReceiver, filter)
        Log.d(TAG, "✅ Broadcast receiver registered for: $SCANNER_BROADCAST_ACTION")
    }
    
    /**
     * Start scanning with built-in scanner
     */
    fun startScanning(): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "❌ Scanner not initialized")
            onError?.invoke("Scanner belum diinisialisasi")
            return false
        }
        
        if (isScanning) {
            Log.w(TAG, "⚠️ Scanner sudah aktif")
            return true
        }
        
        try {
            val isRealScanner = isBuiltInScannerAvailable()
            if (isRealScanner) {
                Log.d(TAG, "🔍 Starting built-in barcode scan (REAL HARDWARE)...")
                return startRealHardwareScanning()
            } else {
                Log.d(TAG, "🔍 Starting built-in barcode scan (MOCK MODE)...")
                return startMockScanning()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting scanner: ${e.message}", e)
            onError?.invoke("Error starting scanner: ${e.message}")
            return false
        }
    }
    
    /**
     * Start real hardware scanning
     */
    private fun startRealHardwareScanning(): Boolean {
        try {
            isScanning = true
            scanStartTime = System.currentTimeMillis()
            onScanStateChanged?.invoke(true)
            
            Log.d(TAG, "🔍 Starting REAL 1D barcode scanner...")
            Log.d(TAG, "💡 Laser scanner should be ON now")
            
            // Start Barcode1D scanner using reflection
            if (barcode1D != null) {
                try {
                    Log.d(TAG, "🔍 Calling startScan method on Barcode1D...")
                    val startScanMethod = barcode1D!!.javaClass.getMethod("startScan")
                    val success = startScanMethod.invoke(barcode1D) as? Boolean ?: false
                    
                    Log.d(TAG, "🔍 startScan result: $success")
                    
                    if (success) {
                        Log.d(TAG, "✅ 1D Barcode scanner started successfully")
                        Log.d(TAG, "💡 Laser is ON - ready to scan barcodes")
                        return true
                    } else {
                        Log.e(TAG, "❌ Failed to start 1D barcode scanner")
                        onError?.invoke("Failed to start 1D barcode scanner")
                        return false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error calling startScan: ${e.message}", e)
                    return startFallbackScanning()
                }
            } else {
                Log.w(TAG, "⚠️ Hardware scanner not available, using fallback")
                return startFallbackScanning()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting real hardware scanner: ${e.message}", e)
            onError?.invoke("Error starting real hardware scanner: ${e.message}")
            return false
        }
    }
    
    /**
     * Start fallback scanning (DISABLED - Hardware scanner only)
     */
    private fun startFallbackScanning(): Boolean {
        Log.w(TAG, "⚠️ Fallback scanning disabled - Hardware scanner only")
        onError?.invoke("Hardware scanner tidak tersedia. Hanya hardware scanner yang didukung.")
        return false
    }
    
    /**
     * Try to use real hardware scanner (Hardware scanner only)
     */
    private fun tryRealHardwareScanner(): Boolean {
        return try {
            // Only use hardware scanner API
            if (useHardwareScannerAPI()) {
                Log.d(TAG, "✅ Using hardware scanner API")
                return true
            }
            
            // No fallback - hardware scanner only
            Log.e(TAG, "❌ Hardware scanner API not available")
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in hardware scanner: ${e.message}", e)
            false
        }
    }
    
    /**
     * Use camera as barcode scanner (DISABLED)
     */
    private fun useCameraAsScanner(): Boolean {
        Log.w(TAG, "⚠️ Camera scanner disabled - Hardware scanner only")
        return false
    }
    
    /**
     * Use system scanner (DISABLED)
     */
    private fun useSystemScanner(): Boolean {
        Log.w(TAG, "⚠️ System scanner disabled - Hardware scanner only")
        return false
    }
    
    /**
     * Use hardware scanner API
     */
    private fun useHardwareScannerAPI(): Boolean {
        return try {
            Log.d(TAG, "⚙️ Attempting to use hardware scanner API...")
            // Try to use hardware scanner APIs
            Log.d(TAG, "⚙️ Hardware scanner API activated")
            true
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Hardware scanner API not available: ${e.message}")
            false
        }
    }
    
    /**
     * Use camera fallback (DISABLED)
     */
    private fun useCameraFallback() {
        Log.w(TAG, "⚠️ Camera fallback disabled - Hardware scanner only")
    }
    
    /**
     * Start mock scanning (DISABLED - Hardware scanner only)
     */
    private fun startMockScanning(): Boolean {
        Log.w(TAG, "⚠️ Mock scanning disabled - Hardware scanner only")
        onError?.invoke("Hardware scanner tidak tersedia. Hanya hardware scanner yang didukung.")
        return false
    }
    
    /**
     * Simulate real scanner behavior
     */
    private fun simulateRealScanner() {
        Log.d(TAG, "🔍 Simulating real scanner behavior...")
        Log.d(TAG, "💡 Laser should be ON (simulated)")
        Log.d(TAG, "📱 Scanner ready to detect barcodes")
        
        // Simulate scanner timeout
        debounceExecutor.schedule({
            if (isScanning) {
                Log.d(TAG, "⏰ Scanner timeout - no barcode detected")
                stopScanning()
            }
        }, SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }
    
    /**
     * Stop scanning
     */
    fun stopScanning(): Boolean {
        if (!isScanning) {
            Log.d(TAG, "📱 Scanner sudah berhenti")
            return true
        }
        
        try {
            Log.d(TAG, "🛑 Stopping 1D barcode scanner...")
            
            // Stop Barcode1D scanner using reflection
            if (barcode1D != null) {
                try {
                    val stopScanMethod = barcode1D!!.javaClass.getMethod("stopScan")
                    stopScanMethod.invoke(barcode1D)
                    Log.d(TAG, "✅ 1D Barcode scanner stopped via hardware API")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error stopping hardware scanner: ${e.message}")
                }
            }
            
            isScanning = false
            onScanStateChanged?.invoke(false)
            
            Log.d(TAG, "✅ Scanner stopped")
            Log.d(TAG, "💡 Laser is OFF")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping scan: ${e.message}", e)
            onError?.invoke("Error stopping scan: ${e.message}")
            return false
        }
    }
    
    /**
     * Set barcode detected callback
     */
    fun setOnBarcodeDetected(callback: (String) -> Unit) {
        Log.d(TAG, "🔧 Setting barcode detected callback")
        onBarcodeDetected = callback
        Log.d(TAG, "✅ Barcode detected callback set successfully")
    }
    
    /**
     * Set scan state changed callback
     */
    fun setOnScanStateChanged(callback: (Boolean) -> Unit) {
        onScanStateChanged = callback
    }
    
    /**
     * Set error callback
     */
    fun setOnError(callback: (String) -> Unit) {
        onError = callback
    }
    
    /**
     * Cleanup scanner resources
     */
    fun cleanup() {
        try {
            Log.d(TAG, "🧹 Cleaning up scanner resources...")
            
            // Stop scanning if active
            if (isScanning) {
                stopScanning()
            }
            
            // Release scanner resources
            barcode1D = null
            deviceAPI = null
            
            // Unregister broadcast receiver
            scannerBroadcastReceiver?.let { receiver ->
                try {
                    context.unregisterReceiver(receiver)
                    Log.d(TAG, "✅ Broadcast receiver unregistered")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error unregistering broadcast receiver: ${e.message}")
                }
            }
            scannerBroadcastReceiver = null
            
            // Shutdown executor
            debounceExecutor.shutdown()
            
            Log.d(TAG, "✅ Scanner resources cleaned up")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up scanner: ${e.message}", e)
        }
    }
    
    // Alias methods untuk kompatibilitas
    fun setBarcodeCallback(callback: (String) -> Unit) {
        setOnBarcodeDetected(callback)
    }
    
    fun setScanStateCallback(callback: (Boolean) -> Unit) {
        setOnScanStateChanged(callback)
    }
    
    fun startScan(): Boolean {
        return startScanning()
    }
    
    fun stopScan(): Boolean {
        return stopScanning()
    }
    
    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): Map<String, Any> {
        val avgScanTime = if (totalScans > 0) {
            scanHistory.map { it.scanDuration }.average()
        } else 0.0
        
        val successRate = if (totalScans > 0) {
            (successfulScans.toDouble() / totalScans) * 100
        } else 0.0
        
        return mapOf(
            "totalScans" to totalScans,
            "successfulScans" to successfulScans,
            "avgScanTime" to avgScanTime,
            "successRate" to successRate,
            "isAvailable" to isBuiltInScannerAvailable(),
            "isInitialized" to isInitialized,
            "isScanning" to isScanning
        )
    }
    
    /**
     * Get scan history
     */
    fun getScanHistory(): List<BarcodeScanResult> {
        return scanHistory.toList()
    }
    
    /**
     * Clear scan history
     */
    fun clearScanHistory() {
        scanHistory.clear()
        lastScannedBarcode.clear()
        totalScans = 0
        successfulScans = 0
        Log.d(TAG, "🗑️ Scan history cleared")
    }
    
}