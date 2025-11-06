package com.example.cekpicklist.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Barcode Scanner Manager
 * Broadcast Receiver Only - Menggunakan broadcast receiver untuk menerima data barcode
 */
class BuiltInBarcodeManager(
    private val context: Context,
    private val activity: Activity
) {
    
    companion object {
        private const val TAG = "BuiltInBarcodeManager"
        private const val SCANNER_BROADCAST_ACTION = "com.scanner.broadcast"
        private const val SCANNER_BROADCAST_KEY = "data"
    }
    
    // Callbacks
    private var onBarcodeDetected: ((String) -> Unit)? = null
    private var onScanStateChanged: ((Boolean) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    
    // Broadcast receiver for scanner
    private var scannerBroadcastReceiver: BroadcastReceiver? = null
    
    init {
        setupBroadcastReceiver()
    }
    
    /**
     * Setup broadcast receiver for scanner
     */
    private fun setupBroadcastReceiver() {
        Log.d(TAG, "🔧 Setting up broadcast receiver for scanner...")
        
        scannerBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "📡 Broadcast receiver triggered")
                Log.d(TAG, "📡 Intent action: ${intent?.action}")
                Log.d(TAG, "📡 Expected action: $SCANNER_BROADCAST_ACTION")
                
                if (intent?.action == SCANNER_BROADCAST_ACTION) {
                    val barcodeData = intent.getStringExtra(SCANNER_BROADCAST_KEY)
                    Log.d(TAG, "📱 Broadcast received with action match: $barcodeData")
                    
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
                } else {
                    Log.w(TAG, "⚠️ Broadcast received but action mismatch: ${intent?.action} (expected: $SCANNER_BROADCAST_ACTION)")
                    // Debug: log semua extras
                    intent?.extras?.keySet()?.forEach { key ->
                        Log.d(TAG, "📡 Intent extra: $key = ${intent.extras?.get(key)}")
                    }
                }
            }
        }
        
        val filter = IntentFilter(SCANNER_BROADCAST_ACTION)
        context.registerReceiver(scannerBroadcastReceiver, filter)
        Log.d(TAG, "✅ Broadcast receiver registered for action: $SCANNER_BROADCAST_ACTION")
        Log.d(TAG, "✅ Broadcast receiver ready to receive barcode data")
    }
    
    /**
     * Start scanning - untuk kompatibilitas, tidak melakukan apa-apa karena menggunakan broadcast
     */
    fun startScanning(): Boolean {
        Log.d(TAG, "📱 Start scanning called (broadcast mode - no action needed)")
        onScanStateChanged?.invoke(true)
        return true
    }
    
    /**
     * Stop scanning - untuk kompatibilitas, tidak melakukan apa-apa karena menggunakan broadcast
     */
    fun stopScanning(): Boolean {
        Log.d(TAG, "📱 Stop scanning called (broadcast mode - no action needed)")
        onScanStateChanged?.invoke(false)
        return true
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
     * Check if built-in scanner is available - selalu false karena menggunakan broadcast
     */
    fun isBuiltInScannerAvailable(): Boolean {
        return false
    }
    
}
