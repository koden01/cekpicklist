package com.example.cekpicklist.base

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cekpicklist.utils.LoadingAnimationHelper
import com.example.cekpicklist.utils.RfidScanManager
import com.example.cekpicklist.utils.RfidScanSettings

/**
 * Base Activity untuk semua activity yang menggunakan RFID scanning
 * Menyediakan implementasi yang konsisten untuk RFID scanning
 */
abstract class BaseRfidActivity : AppCompatActivity() {
    
    protected lateinit var rfidScanManager: RfidScanManager
    protected var isScanning = false
    
    companion object {
        private const val TAG = "BaseRfidActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize RFID Scan Manager
        initializeRfidScanManager()
    }
    
    /**
     * Initialize RFID Scan Manager
     */
    private fun initializeRfidScanManager() {
        rfidScanManager = RfidScanManager(this, this)
        
        // Set callbacks
        rfidScanManager.setOnRfidDetected { epc, rssi ->
            runOnUiThread { handleRfidDetected(epc, rssi) }
        }
        
        rfidScanManager.setOnScanStateChanged { scanning ->
            runOnUiThread { handleScanStateChanged(scanning) }
        }
        
        rfidScanManager.setOnError { error ->
            runOnUiThread { handleRfidError(error) }
        }
        
        rfidScanManager.setOnGracePeriodCompleted {
            runOnUiThread { handleGracePeriodCompleted() }
        }
        
        rfidScanManager.setOnLookupRequired { rfidList ->
            runOnUiThread { handleLookupRequired(rfidList) }
        }
        
        rfidScanManager.setOnDataCleared {
            runOnUiThread { handleDataCleared() }
        }
        
        Log.d(TAG, "🔥 RFID Scan Manager initialized")
    }
    
    /**
     * Handle RFID detected - to be implemented by subclasses
     */
    protected abstract fun handleRfidDetected(epc: String, rssi: Int)
    
    /**
     * Handle scan state changed - to be implemented by subclasses
     */
    protected abstract fun handleScanStateChanged(isScanning: Boolean)
    
    /**
     * Handle RFID error - to be implemented by subclasses
     */
    protected abstract fun handleRfidError(error: String)
    
    /**
     * Handle grace period completed - to be implemented by subclasses
     */
    protected abstract fun handleGracePeriodCompleted()
    
    /**
     * Handle lookup required - to be implemented by subclasses
     */
    protected abstract fun handleLookupRequired(rfidList: List<String>)
    
    /**
     * Handle data cleared - to be implemented by subclasses
     */
    protected abstract fun handleDataCleared()
    
    /**
     * Start RFID scanning
     */
    protected fun startRfidScanning(): Boolean {
        val started = rfidScanManager.startScanning()
        if (started) {
            isScanning = true
            Log.d(TAG, "🔥 RFID scanning started")
        }
        return started
    }
    
    /**
     * Stop RFID scanning
     */
    protected fun stopRfidScanning(): Boolean {
        val stopped = rfidScanManager.stopScanning()
        if (stopped) {
            isScanning = false
            Log.d(TAG, "🔥 RFID scanning stopped")
        }
        return stopped
    }
    
    /**
     * Stop RFID scanning dengan grace period
     */
    protected fun stopRfidScanningWithGracePeriod(): Boolean {
        val stopped = rfidScanManager.stopScanningWithGracePeriod()
        if (stopped) {
            isScanning = false
            Log.d(TAG, "🔥 RFID scanning stopped with grace period")
        }
        return stopped
    }
    
    /**
     * Toggle RFID scanning
     */
    protected fun toggleRfidScanning() {
        if (isScanning) {
            stopRfidScanningWithGracePeriod()
        } else {
            // Cancel grace period if active
            rfidScanManager.cancelGracePeriod()
            startRfidScanning()
        }
    }
    
    /**
     * Handle key events untuk physical scan button
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val handled = rfidScanManager.handleKeyDown(keyCode, event)
        if (handled) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    
    /**
     * Handle key up events untuk physical scan button
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val handled = rfidScanManager.handleKeyUp(keyCode, event)
        if (handled) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
    
    /**
     * Play beep sound
     */
    protected fun playBeepSound() {
        rfidScanManager.playBeepSound()
    }
    
    /**
     * Check if scanning is active
     */
    protected fun isRfidScanning(): Boolean = rfidScanManager.isScanning()
    
    /**
     * Check if grace period is active
     */
    protected fun isGracePeriodActive(): Boolean = rfidScanManager.isGracePeriodActive()
    
    /**
     * Cancel grace period (wrapper)
     */
    protected fun cancelGracePeriod() {
        rfidScanManager.cancelGracePeriod()
    }
    
    /**
     * Get RFID scan settings
     */
    protected fun getRfidScanSettings(): RfidScanSettings {
        return rfidScanManager.getSettings()
    }
    
    /**
     * Update RFID scan settings
     */
    protected fun updateRfidScanSettings(settings: RfidScanSettings) {
        rfidScanManager.updateSettings(settings)
    }
    
    /**
     * Clear all data
     */
    protected fun clearAllData() {
        rfidScanManager.clearAllData()
    }
    
    /**
     * Get all unique RFIDs
     */
    protected fun getAllUniqueRfids(): List<String> {
        return rfidScanManager.getAllUniqueRfids()
    }
    
    /**
     * Get unique RFID count
     */
    protected fun getUniqueRfidCount(): Int {
        return rfidScanManager.getUniqueRfidCount()
    }
    
    /**
     * Show grace period indicator
     */
    protected fun showGracePeriodIndicator() {
        LoadingAnimationHelper.showLoadingToast(this, "Waiting for late RFIDs...", Toast.LENGTH_SHORT)
    }
    
    /**
     * Hide grace period indicator
     */
    protected fun hideGracePeriodIndicator() {
        LoadingAnimationHelper.hideLoadingToast()
    }
    
    /**
     * Show grace period cancelled feedback
     */
    protected fun showGracePeriodCancelledFeedback() {
        LoadingAnimationHelper.showLoadingToast(this, "Grace period cancelled - starting new scan", Toast.LENGTH_SHORT)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cleanup RFID Scan Manager
        rfidScanManager.cleanup()
        Log.d(TAG, "🔥 BaseRfidActivity destroyed")
    }
}
